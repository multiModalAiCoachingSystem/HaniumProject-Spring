// 역할: 전역 인증 컨텍스트와 라우팅을 감싸는 최상위 React 컴포넌트입니다.
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext.jsx';
import AppRoutes from './routes/AppRoutes.jsx';

// 함수 역할: App 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
