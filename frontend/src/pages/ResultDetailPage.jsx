// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { PolarAngleAxis, PolarGrid, Radar, RadarChart, ResponsiveContainer } from 'recharts';
import FeedbackTabs from '../components/result/FeedbackTabs.jsx';
import useResult from '../hooks/useResult.js';
import useVideoSeek from '../hooks/useVideoSeek.js';
import { visualReliability } from '../utils/visualReliability.js';

const SCORE_ORDER = ['POSTURE', 'EXPRESSION', 'EYE_CONTACT', 'VOICE', 'GESTURE'];

const CATEGORY_LABELS = {
  POSTURE: '자세',
  EXPRESSION: '표정',
  EYE_CONTACT: '시선 처리',
  GAZE: '시선 처리',
  VOICE: '음성',
  GESTURE: '제스처',
};

function categoryLabel(category) {
  return CATEGORY_LABELS[category] || category || '-';
}

function normalizedScores(scores = []) {
  const byCategory = new Map((scores || []).map((score) => [score.category, score]));
  return SCORE_ORDER
    .map((category) => byCategory.get(category))
    .filter(Boolean)
    .map((score) => ({
      ...score,
      name: categoryLabel(score.category),
      score: clampScore(score.score),
    }));
}

function clampScore(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(0, Math.min(100, number));
}

function round(value, digits = 1) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return number.toFixed(digits).replace(/\.0$/, '');
}

function ratioText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${round(number * 100, 1)}%`;
}

function scoreText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${round(number, 1)}점`;
}

function secondsText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${round(number, 1)}초`;
}

function countText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${Math.round(number)}회`;
}

function perMinuteText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${round(number, 1)}회/분`;
}

function spmText(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return '-';
  return `${round(number, 1)} SPM`;
}

function buildMetricRows(metrics = {}, scores = []) {
  const scoreByCategory = new Map((scores || []).map((score) => [score.category, score.score]));
  const expressionReliability = visualReliability(metrics, 'EXPRESSION');
  const gazeReliability = visualReliability(metrics, 'EYE_CONTACT');
  return [
    ['자세', '어깨 기울기', `${round(metrics.shoulderTiltDegrees, 1)}°`],
    ['자세', '고개 움직임', ratioText(metrics.headMovementRatio)],
    ['자세', '상체 안정성', ratioText(metrics.postureStabilityRatio)],
    ['자세', '자세 점수', scoreText(scoreByCategory.get('POSTURE'))],
    ['표정', '얼굴 감지율', ratioText(metrics.faceDetectionRatio)],
    ['표정', '자연스러운 표정', ratioText(metrics.naturalExpressionRatio)],
    ['표정', '긴장 표정', ratioText(metrics.tenseExpressionRatio)],
    ['표정', '미소 비율', ratioText(metrics.smileRatio)],
    ['표정', '분석 신뢰도', expressionReliability.label],
    ['표정', '표정 점수', scoreText(scoreByCategory.get('EXPRESSION'))],
    ['시선 처리', '랜드마크 감지율', ratioText(metrics.gazeDetectionRatio ?? metrics.faceDetectionRatio)],
    ['시선 처리', '시선 처리 비율', ratioText(metrics.eyeContactRatio)],
    ['시선 처리', '분석 신뢰도', gazeReliability.label],
    ['시선 처리', '시선 처리 점수', scoreText(scoreByCategory.get('EYE_CONTACT'))],
    ['음성', '말하기 속도', spmText(metrics.speechSpeedSpm)],
    ['음성', '필러 표현 횟수', countText(metrics.fillerWordCount)],
    ['음성', '필러 표현 비율', ratioText(metrics.fillerWordRatio)],
    ['음성', '침묵 비율', ratioText(metrics.pauseRatio)],
    ['음성', '침묵 횟수', countText(metrics.pauseCount)],
    ['음성', '최장 침묵', secondsText(metrics.longestPauseSeconds)],
    ['음성', '음량 안정성', ratioText(metrics.volumeStabilityRatio)],
    ['음성', '음성 점수', scoreText(scoreByCategory.get('VOICE'))],
    ['제스처', '랜드마크 감지율', ratioText(metrics.gestureVisibleRatio)],
    ['제스처', '움직임 비율', ratioText(metrics.gestureMovementRatio)],
    ['제스처', '분당 제스처', perMinuteText(metrics.gestureCountPerMinute)],
    ['제스처', '손 정지 비율', ratioText(metrics.stillHandRatio)],
    ['제스처', '제스처 활동 점수', scoreText(metrics.gestureActivityScore)],
    ['제스처', '제스처 점수', scoreText(scoreByCategory.get('GESTURE'))],
  ].filter(([, , value]) => value !== '-');
}

function fallbackMessages(metrics = {}, videoLlmMetadata = {}) {
  const messages = [];
  const expressionReliability = visualReliability(metrics, 'EXPRESSION');
  const gazeReliability = visualReliability(metrics, 'EYE_CONTACT');
  if (metrics.visionAnalysisFallback || metrics.visionAnalysisFallbackReason) {
    messages.push('영상에서 얼굴이나 자세가 충분히 감지되지 않아 시각 분석 일부에 추정값이 사용되었습니다.');
  }
  if (expressionReliability.limited || metrics.expressionAnalysisReliable === false) {
    messages.push('영상에서 얼굴이 충분히 감지되지 않아 표정 점수의 신뢰도가 낮을 수 있습니다. 얼굴이 화면에서 너무 작거나 벗어나지 않도록 정면과 밝은 조명에서 다시 촬영해 보세요.');
  }
  if (gazeReliability.limited || metrics.gazeAnalysisReliable === false) {
    messages.push('얼굴과 눈이 충분히 감지되지 않아 시선 분석 정확도가 낮을 수 있습니다. 얼굴이 화면에서 크게 보이도록 정면을 향하고 밝은 조명에서 촬영해 보세요.');
  }
  if (metrics.gestureAnalysisFallback || metrics.gestureAnalysisFallbackReason) {
    messages.push('손과 팔이 화면에 충분히 보이지 않아 제스처 분석 정확도가 낮을 수 있습니다.');
  }
  if (metrics.audioMetricsFallback || metrics.audioMetricsFallbackReason) {
    messages.push('오디오 품질 또는 발화 감지 문제로 음성 분석 일부에 추정값이 사용되었습니다.');
  }
  if (videoLlmMetadata.fallbackUsed === true || String(videoLlmMetadata.fallbackUsed).toLowerCase() === 'true') {
    messages.push('Video LLM 관찰은 기본 모델 호출 실패로 대체 관찰 결과가 사용되었습니다.');
  }
  return [...new Set(messages)];
}

function textFeedbackBlocks(textFeedback = {}) {
  const strengths = Array.isArray(textFeedback.strengths) ? textFeedback.strengths : [];
  const weaknesses = Array.isArray(textFeedback.weaknesses) ? textFeedback.weaknesses : [];
  const actions = Array.isArray(textFeedback.improvementActions) ? textFeedback.improvementActions : [];
  const questions = Array.isArray(textFeedback.expectedQuestions) ? textFeedback.expectedQuestions : [];
  return { strengths, weaknesses, actions, questions };
}

// 함수 역할: ResultDetailPage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function ResultDetailPage() {
  const { resultId } = useParams();
  const result = useResult(resultId);
  const videoRef = useRef(null);
  const seek = useVideoSeek(videoRef);
  const [tab, setTab] = useState('STRENGTH');
  const [videoSrc, setVideoSrc] = useState('');

  useEffect(() => {
    setTab('STRENGTH');
  }, [resultId]);

  useEffect(() => {
    if (!result?.videoId) return undefined;
    const controller = new AbortController();
    const token = localStorage.getItem('accessToken');
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    let objectUrl = '';

    fetch(`/api/videos/${result.videoId}/stream`, {
      headers,
      credentials: 'include',
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error(`Video stream failed: ${response.status}`);
        return response.blob();
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setVideoSrc(objectUrl);
      })
      .catch((error) => {
        if (error.name !== 'AbortError') console.error(error);
      });

    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setVideoSrc('');
    };
  }, [result?.videoId]);

  if (!result) return <section className="panel">결과를 불러오는 중입니다.</section>;

  const scores = normalizedScores(result.scores || []);
  const metrics = result.metrics || result.summary?.metrics || {};
  const videoLlmMetadata = result.videoLlmMetadata || result.summary?.videoLlmMetadata || {};
  const metricRows = buildMetricRows(metrics, scores);
  const fallbackWarnings = fallbackMessages(metrics, videoLlmMetadata);
  const scoreReliability = {
    EXPRESSION: visualReliability(metrics, 'EXPRESSION'),
    EYE_CONTACT: visualReliability(metrics, 'EYE_CONTACT'),
  };
  const textFeedback = result.summary?.textFeedback || {};
  const feedbackBlocks = textFeedbackBlocks(textFeedback);
  const transcript = result.summary?.transcript || '';
  const stt = result.summary?.stt || {};
  const sttStatus = stt.available
    ? `${stt.provider || 'STT'} · ${stt.model || 'model'} · 신뢰도 ${Math.round((stt.confidence || 0) * 100)}%`
    : stt.error || 'STT 결과가 아직 없습니다.';

  return (
    <section className="result-page">
      <div className="result-header">
        <div>
          <p className="section-kicker">Coaching Report</p>
          <h1>{result.originalFilename}</h1>
          <p className="lead-text">{result.summary?.overall}</p>
        </div>
        <div className="result-score">
          <span>총점</span>
          <strong>{result.totalScore}</strong>
        </div>
      </div>

      <div className="result-layout">
        <section className="video-panel">
          <video ref={videoRef} controls preload="metadata" className="video" src={videoSrc} />
          <div className="summary-strip">
            <div><span>우선 개선</span><strong>{categoryLabel(result.summary?.priority)}</strong></div>
            <div><span>연습 방법</span><strong>{result.summary?.practice}</strong></div>
          </div>
        </section>

        <section className="chart-panel">
          <h2>항목별 점수</h2>
          <ResponsiveContainer width="100%" height={300}>
            <RadarChart data={scores}>
              <PolarGrid />
              <PolarAngleAxis dataKey="name" />
              <Radar dataKey="score" fill="#2563eb" fillOpacity={0.35} stroke="#2563eb" />
            </RadarChart>
          </ResponsiveContainer>
        </section>
      </div>

      {fallbackWarnings.length > 0 && (
        <section className="result-warning-panel">
          <h2>분석 품질 안내</h2>
          {fallbackWarnings.map((message) => <p key={message}>{message}</p>)}
        </section>
      )}

      <div className="score-card-grid">
        {scores.map((score) => (
          <article className="score-card" key={score.category}>
            <span>{categoryLabel(score.category)}</span>
            <h2>{score.name}</h2>
            <strong>{score.score}</strong>
            <p>{score.grade} · {score.summary}</p>
            {scoreReliability[score.category]?.limited && (
              <small className="score-reliability">{scoreReliability[score.category].label}</small>
            )}
          </article>
        ))}
      </div>

      {metricRows.length > 0 && (
        <details className="metric-disclosure" key={resultId}>
          <summary className="metric-disclosure-toggle">
            <span className="metric-disclosure-show">상세 분석 지표 보기</span>
            <span className="metric-disclosure-hide">상세 분석 지표 숨기기</span>
          </summary>
          <section className="metric-panel">
            <div>
              <p className="section-kicker">Raw Metrics</p>
              <h2>상세 분석 지표</h2>
            </div>
            <div className="metric-grid">
              {metricRows.map(([group, label, value]) => (
                <div className="metric-row" key={`${group}-${label}`}>
                  <span>{group}</span>
                  <strong>{label}</strong>
                  <em>{value}</em>
                </div>
              ))}
            </div>
          </section>
        </details>
      )}

      {(textFeedback.presentationSummary || textFeedback.overallFeedback || feedbackBlocks.actions.length > 0) && (
        <section className="text-feedback-panel">
          <div>
            <p className="section-kicker">Text Feedback</p>
            <h2>종합 피드백</h2>
          </div>
          {textFeedback.presentationSummary && <p>{textFeedback.presentationSummary}</p>}
          {textFeedback.overallFeedback && <p>{textFeedback.overallFeedback}</p>}
          <div className="text-feedback-grid">
            {feedbackBlocks.strengths.length > 0 && (
              <div>
                <h3>강점</h3>
                {feedbackBlocks.strengths.map((item) => (
                  <p key={item.title || item.description}>{item.title ? `${item.title}: ` : ''}{item.description}</p>
                ))}
              </div>
            )}
            {feedbackBlocks.weaknesses.length > 0 && (
              <div>
                <h3>개선 필요</h3>
                {feedbackBlocks.weaknesses.map((item) => (
                  <p key={item.title || item.description}>{item.title ? `${item.title}: ` : ''}{item.description}</p>
                ))}
              </div>
            )}
            {feedbackBlocks.actions.length > 0 && (
              <div>
                <h3>연습 방법</h3>
                {feedbackBlocks.actions.map((item) => (
                  <p key={item.title || item.description}>{item.title ? `${item.title}: ` : ''}{item.description}</p>
                ))}
              </div>
            )}
            {feedbackBlocks.questions.length > 0 && (
              <div>
                <h3>예상 질문</h3>
                {feedbackBlocks.questions.map((item) => (
                  <p key={item.question}>{item.question}{item.suggestedAnswer ? ` · ${item.suggestedAnswer}` : ''}</p>
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      <section className="transcript-panel">
        <div className="transcript-heading">
          <div>
            <p className="section-kicker">Speech Transcript</p>
            <h2>발표 음성 텍스트</h2>
          </div>
          <span className={`status-pill ${stt.available ? 'is-ready' : 'is-muted'}`}>{sttStatus}</span>
        </div>
        <div className="transcript-box">
          {transcript || '아직 추출된 발표 음성 텍스트가 없습니다.'}
        </div>
      </section>

      <FeedbackTabs feedback={result.feedback || result.observations || []} tab={tab} onTabChange={setTab} onSeek={seek} />
    </section>
  );
}
