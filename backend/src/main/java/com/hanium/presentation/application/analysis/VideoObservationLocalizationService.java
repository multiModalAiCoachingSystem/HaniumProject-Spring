package com.hanium.presentation.application.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class VideoObservationLocalizationService {

    public List<Map<String, Object>> localize(List<Map<String, Object>> observations) {
        List<Map<String, Object>> localized = new ArrayList<>();
        if (observations == null) {
            return localized;
        }
        for (Map<String, Object> observation : observations) {
            Map<String, Object> candidate = localizeOne(observation);
            int duplicateIndex = duplicateIndex(localized, candidate);
            if (duplicateIndex < 0) {
                localized.add(candidate);
            } else if (isPreferred(candidate, localized.get(duplicateIndex))) {
                localized.set(duplicateIndex, candidate);
            }
        }
        for (int index = 0; index < localized.size(); index++) {
            localized.get(index).put("id", index + 1);
        }
        return localized;
    }

    public Map<String, Object> localizeOne(Map<String, Object> observation) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (observation != null) {
            item.putAll(observation);
        }

        item.putIfAbsent("rawTitle", safeString(item.get("title")));
        item.putIfAbsent("rawDescription", safeString(item.get("description")));
        item.putIfAbsent("rawSuggestion", safeString(item.get("suggestion")));
        item.putIfAbsent("rawLevel", safeString(item.get("level")));
        item.putIfAbsent("rawType", safeString(item.get("type")));
        item.putIfAbsent("rawCategory", safeString(item.get("category")));

        String category = normalizeCategory(item.get("category"));
        String level = normalizeLevel(item.get("level"), item.get("type"));
        String categoryLabel = categoryLabel(category);
        String levelLabel = levelLabel(level);
        double timestampSeconds = timestampSeconds(item);

        item.put("category", category);
        item.put("level", level);
        item.put("type", typeForLevel(level));
        item.put("startSeconds", timestampSeconds);
        item.put("timestampSeconds", timestampSeconds);
        item.put("titleKo", categoryLabel + " " + levelLabel);
        item.put("descriptionKo", localizedDescription(item, category, level));
        item.put("recommendationKo", recommendationKo(category, level));
        item.put("categoryKo", categoryLabel);
        item.put("levelKo", levelLabel);
        item.put("displayNeutral", isMeaningfulNeutral(item, category, level));
        return item;
    }

    private int duplicateIndex(List<Map<String, Object>> localized, Map<String, Object> candidate) {
        for (int index = 0; index < localized.size(); index++) {
            Map<String, Object> existing = localized.get(index);
            if (!Objects.equals(existing.get("category"), candidate.get("category"))
                    || !Objects.equals(existing.get("level"), candidate.get("level"))) {
                continue;
            }
            if (Math.abs(timestampSeconds(existing) - timestampSeconds(candidate)) > 2.0) {
                continue;
            }
            String existingDescription = normalizeText(existing.get("descriptionKo"));
            String candidateDescription = normalizeText(candidate.get("descriptionKo"));
            if (existingDescription.equals(candidateDescription)
                    || Math.round(timestampSeconds(existing)) == Math.round(timestampSeconds(candidate))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isPreferred(Map<String, Object> candidate, Map<String, Object> existing) {
        double candidateConfidence = numberValue(candidate.get("confidence"));
        double existingConfidence = numberValue(existing.get("confidence"));
        if (candidateConfidence != existingConfidence) {
            return candidateConfidence > existingConfidence;
        }
        return safeString(candidate.get("rawDescription")).length()
                > safeString(existing.get("rawDescription")).length();
    }

    private String localizedDescription(Map<String, Object> item, String category, String level) {
        String translated = safeString(item.get("descriptionKo"));
        if (containsKorean(translated) && !conflictsWithLevel(translated, level)) {
            return translated;
        }
        String original = safeString(item.get("description"));
        if (containsKorean(original) && !conflictsWithLevel(original, level)) {
            return original;
        }
        return descriptionKo(category, level);
    }

    private boolean conflictsWithLevel(String text, String level) {
        String compact = normalizeText(text);
        if ("STRENGTH".equals(level)) {
            return containsAny(compact, "개선", "보완", "부족", "줄여", "필요", "벗어나", "어렵", "불안정");
        }
        if ("IMPROVEMENT".equals(level)) {
            return containsAny(compact, "강점", "우수", "현재와같", "안정적으로관찰", "잘유지");
        }
        return false;
    }

    private boolean isMeaningfulNeutral(Map<String, Object> item, String category, String level) {
        if (!"NEUTRAL".equals(level) || !"VIDEO_QUALITY".equals(category)) {
            return false;
        }
        if (numberValue(item.get("confidence")) > 0.0 && numberValue(item.get("confidence")) < 0.6) {
            return true;
        }
        String source = normalizeText(safeString(item.get("rawTitle")) + " "
                + safeString(item.get("rawDescription")) + " " + safeString(item.get("rawSuggestion")));
        return containsAny(source,
                "낮", "어두", "흐림", "흐리", "가려", "화면밖", "충분하지", "신뢰도", "품질경고",
                "lowconfidence", "dark", "blurry", "blurred", "occluded", "outofframe", "insufficient", "poorquality");
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) {
            if (source.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsKorean(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3);
    }

    private String normalizeText(Object value) {
        return safeString(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private double timestampSeconds(Map<String, Object> item) {
        Object value = item.get("timestampSeconds");
        if (value == null) {
            value = item.get("startSeconds");
        }
        return Math.max(0.0, numberValue(value));
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(safeString(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private String normalizeCategory(Object value) {
        String category = safeString(value).toUpperCase(Locale.ROOT);
        return switch (category) {
            case "GAZE" -> "EYE_CONTACT";
            case "VISIBILITY", "VIDEO_QUALITY" -> "VIDEO_QUALITY";
            case "POSTURE", "GESTURE", "EXPRESSION", "EYE_CONTACT" -> category;
            default -> "GENERAL";
        };
    }

    private String normalizeLevel(Object levelValue, Object typeValue) {
        String level = safeString(levelValue);
        if (level.isBlank()) {
            level = safeString(typeValue);
        }
        level = level.toUpperCase(Locale.ROOT);
        return switch (level) {
            case "STRENGTH" -> "STRENGTH";
            case "IMPROVEMENT", "NEEDS_IMPROVEMENT", "WEAKNESS" -> "IMPROVEMENT";
            default -> "NEUTRAL";
        };
    }

    private String typeForLevel(String level) {
        return switch (level) {
            case "STRENGTH" -> "STRENGTH";
            case "IMPROVEMENT" -> "WEAKNESS";
            default -> "INFO";
        };
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "POSTURE" -> "자세";
            case "EYE_CONTACT" -> "시선";
            case "GESTURE" -> "제스처";
            case "EXPRESSION" -> "표정";
            case "VIDEO_QUALITY" -> "화면 가시성";
            default -> "종합";
        };
    }

    private String levelLabel(String level) {
        return switch (level) {
            case "STRENGTH" -> "강점";
            case "IMPROVEMENT" -> "개선 필요";
            default -> "참고";
        };
    }

    private String descriptionKo(String category, String level) {
        if ("STRENGTH".equals(level)) {
            return switch (category) {
                case "POSTURE" -> "발표 중 안정적인 자세가 관찰되었습니다.";
                case "EYE_CONTACT" -> "청중 또는 카메라를 향한 시선 흐름이 비교적 안정적으로 관찰되었습니다.";
                case "GESTURE" -> "발표 내용을 보조하는 손동작이나 몸의 움직임이 관찰되었습니다.";
                case "EXPRESSION" -> "발표 분위기에 어울리는 자연스러운 표정 흐름이 관찰되었습니다.";
                case "VIDEO_QUALITY" -> "발표자의 모습이 분석에 활용될 수 있을 정도로 확인되었습니다.";
                default -> "발표 영상에서 긍정적인 시각적 요소가 관찰되었습니다.";
            };
        }
        if ("IMPROVEMENT".equals(level)) {
            return switch (category) {
                case "POSTURE" -> "자세나 상체 움직임에서 보완하면 좋을 부분이 관찰되었습니다.";
                case "EYE_CONTACT" -> "시선이 카메라나 청중 방향에서 벗어나는 구간이 관찰되었습니다.";
                case "GESTURE" -> "제스처의 양이나 안정성에서 보완하면 좋을 부분이 관찰되었습니다.";
                case "EXPRESSION" -> "표정 변화나 긴장감에서 보완하면 좋을 부분이 관찰되었습니다.";
                case "VIDEO_QUALITY" -> "얼굴이나 상반신이 충분히 보이지 않는 구간이 관찰되었습니다.";
                default -> "발표 영상에서 보완하면 좋을 시각적 요소가 관찰되었습니다.";
            };
        }
        return switch (category) {
            case "POSTURE" -> "자세와 상체 움직임을 참고할 수 있는 구간입니다.";
            case "EYE_CONTACT" -> "시선 흐름을 참고할 수 있는 구간입니다.";
            case "GESTURE" -> "제스처 사용을 참고할 수 있는 구간입니다.";
            case "EXPRESSION" -> "표정 흐름을 참고할 수 있는 구간입니다.";
            case "VIDEO_QUALITY" -> "영상 가시성을 참고할 수 있는 구간입니다.";
            default -> "발표 영상에서 참고할 수 있는 시각적 관찰입니다.";
        };
    }

    private String recommendationKo(String category, String level) {
        if ("STRENGTH".equals(level)) {
            return switch (category) {
                case "POSTURE" -> "앞으로의 발표에서도 현재와 같은 안정적인 자세를 유지해 보세요.";
                case "EYE_CONTACT" -> "핵심 문장을 말할 때 현재와 같은 시선 흐름을 유지해 보세요.";
                case "GESTURE" -> "강조할 문장에서 현재와 같은 자연스러운 제스처를 유지해 보세요.";
                case "EXPRESSION" -> "중요한 내용 앞뒤에서도 현재처럼 자연스러운 표정을 유지해 보세요.";
                case "VIDEO_QUALITY" -> "비슷한 촬영 구도와 밝기를 유지하면 분석 품질도 안정적입니다.";
                default -> "현재의 긍정적인 시각적 흐름을 다음 발표에서도 유지해 보세요.";
            };
        }
        if ("IMPROVEMENT".equals(level)) {
            return switch (category) {
                case "POSTURE" -> "어깨와 상체를 곧게 유지하고 불필요한 흔들림을 줄여 보세요.";
                case "EYE_CONTACT" -> "문장 단위로 카메라나 청중을 다시 바라보는 연습을 해 보세요.";
                case "GESTURE" -> "강조할 문장에 맞춰 손동작을 짧고 명확하게 사용해 보세요.";
                case "EXPRESSION" -> "핵심 문장을 말하기 전후로 표정을 풀고 자연스럽게 말해 보세요.";
                case "VIDEO_QUALITY" -> "얼굴과 상반신이 화면 안에 안정적으로 들어오도록 촬영 구도를 조정해 보세요.";
                default -> "다음 리허설에서 이 구간의 시각적 전달 방식을 한 번 더 점검해 보세요.";
            };
        }
        return "이 구간을 참고해 다음 리허설에서 발표 자세와 시각적 전달 방식을 점검해 보세요.";
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
