// 역할: API 조회, 인증 상태, 영상 seek 같은 재사용 가능한 React Hook을 정의합니다.
import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext.jsx';

// 함수 역할: useAuth 훅에서 화면 상태, API 호출 결과, 이벤트 처리 함수를 묶어 반환합니다.
export default function useAuth() {
  return useContext(AuthContext);
}
