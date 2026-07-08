// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { PolarAngleAxis, PolarGrid, Radar, RadarChart, ResponsiveContainer } from 'recharts';
import FeedbackTabs from '../components/result/FeedbackTabs.jsx';
import useResult from '../hooks/useResult.js';
import useVideoSeek from '../hooks/useVideoSeek.js';

// 함수 역할: ResultDetailPage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function ResultDetailPage() {
  const { resultId } = useParams();
  const result = useResult(resultId);
  const videoRef = useRef(null);
  const seek = useVideoSeek(videoRef);
  const [tab, setTab] = useState('STRENGTH');
  const [videoSrc, setVideoSrc] = useState('');

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
            <div><span>우선 개선</span><strong>{result.summary?.priority}</strong></div>
            <div><span>연습 방법</span><strong>{result.summary?.practice}</strong></div>
          </div>
        </section>

        <section className="chart-panel">
          <h2>항목별 점수</h2>
          <ResponsiveContainer width="100%" height={300}>
            <RadarChart data={result.scores}>
              <PolarGrid />
              <PolarAngleAxis dataKey="name" />
              <Radar dataKey="score" fill="#2563eb" fillOpacity={0.35} stroke="#2563eb" />
            </RadarChart>
          </ResponsiveContainer>
        </section>
      </div>

      <div className="score-card-grid">
        {result.scores.map((score) => (
          <article className="score-card" key={score.category}>
            <span>{score.category}</span>
            <h2>{score.name}</h2>
            <strong>{score.score}</strong>
            <p>{score.grade} · {score.summary}</p>
          </article>
        ))}
      </div>

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

      <FeedbackTabs feedback={result.feedback} tab={tab} onTabChange={setTab} onSeek={seek} />
    </section>
  );
}
