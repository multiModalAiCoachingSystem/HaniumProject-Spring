// 역할: 로그인 상태, 권한별 라우트 보호, 인증 컨텍스트를 관리합니다.
import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../hooks/useAuth.js';

// 함수 역할: ProtectedRoute 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function ProtectedRoute() {
  const { user } = useAuth();
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}
