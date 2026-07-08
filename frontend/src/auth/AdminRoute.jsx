// 역할: 로그인 상태, 권한별 라우트 보호, 인증 컨텍스트를 관리합니다.
import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../hooks/useAuth.js';

// 함수 역할: 운영 콘솔에 접근할 수 있는 ADMIN 또는 MANAGER 권한만 통과시킵니다.
export default function AdminRoute() {
  const { user } = useAuth();
  return user?.role === 'ADMIN' || user?.role === 'MANAGER' ? <Outlet /> : <Navigate to="/" replace />;
}
