// 역할: API 조회, 인증 상태, 영상 seek 같은 재사용 가능한 React Hook을 정의합니다.
import { useEffect, useState } from 'react';
import { getResult } from '../api/resultApi.js';
import { apiErrorMessage } from '../api/httpClient.js';

// 함수 역할: useResult 훅에서 화면 상태, API 호출 결과, 이벤트 처리 함수를 묶어 반환합니다.
export default function useResult(resultId) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    setResult(null);
    setError('');
    if (!resultId) return undefined;

    const controller = new AbortController();
    getResult(resultId, { signal: controller.signal })
      .then(({ data }) => setResult(data.data))
      .catch((error) => {
        if (error.code !== 'ERR_CANCELED') {
          setResult(null);
          setError(apiErrorMessage(error, '결과를 불러오지 못했습니다.'));
        }
      });
    return () => controller.abort();
  }, [resultId]);
  return { result, error };
}
