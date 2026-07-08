// 역할: API 조회, 인증 상태, 영상 seek 같은 재사용 가능한 React Hook을 정의합니다.
import { useEffect, useRef, useState } from 'react';
import { getAnalysisStatus } from '../api/analysisApi.js';

// 함수 역할: useAnalysisStatus 훅에서 화면 상태, API 호출 결과, 이벤트 처리 함수를 묶어 반환합니다.
export default function useAnalysisStatus(jobId) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [completedOnce, setCompletedOnce] = useState(false);
  const notified = useRef(false);

  useEffect(() => {
    if (!jobId) return undefined;
    let timerId;
    let mounted = true;

    // 함수 역할: poll에서 화면 동작에 필요한 상태 처리와 보조 로직을 수행합니다.
    const poll = async () => {
      try {
        const { data } = await getAnalysisStatus(jobId);
        if (!mounted) return;
        setStatus(data.data);
        if (data.data.status === 'DONE' || data.data.status === 'FAILED') {
          window.clearInterval(timerId);
          if (data.data.status === 'DONE' && !notified.current) {
            notified.current = true;
            setCompletedOnce(true);
          }
        }
      } catch {
        setError('분석 상태를 불러오지 못했습니다.');
      }
    };

    poll();
    timerId = window.setInterval(poll, 2000);
    return () => {
      mounted = false;
      window.clearInterval(timerId);
    };
  }, [jobId]);

  return { status, error, completedOnce };
}
