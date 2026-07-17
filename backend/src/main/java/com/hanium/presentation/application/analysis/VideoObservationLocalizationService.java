package com.hanium.presentation.application.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VideoObservationLocalizationService {

    public List<Map<String, Object>> localize(List<Map<String, Object>> observations) {
        List<Map<String, Object>> localized = new ArrayList<>();
        if (observations == null) {
            return localized;
        }
        for (Map<String, Object> observation : observations) {
            localized.add(localizeOne(observation));
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

        item.put("category", category);
        item.put("level", level);
        item.put("type", typeForLevel(level));
        item.put("titleKo", categoryLabel + " " + levelLabel);
        item.putIfAbsent("descriptionKo", descriptionKo(category, level));
        item.putIfAbsent("recommendationKo", recommendationKo(category, level));
        item.put("categoryKo", categoryLabel);
        item.put("levelKo", levelLabel);
        return item;
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
