// 역할: URL 경로와 화면 컴포넌트, 보호 라우트를 연결합니다.
import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout.jsx';
import HomePage from '../pages/HomePage.jsx';
import LoginPage from '../pages/LoginPage.jsx';
import SignupPage from '../pages/SignupPage.jsx';
import UploadPage from '../pages/UploadPage.jsx';
import AnalysisStatusPage from '../pages/AnalysisStatusPage.jsx';
import ResultListPage from '../pages/ResultListPage.jsx';
import ProtectedRoute from '../auth/ProtectedRoute.jsx';
import AdminRoute from '../auth/AdminRoute.jsx';

const ResultDetailPage = lazy(() => import('../pages/ResultDetailPage.jsx'));
const AdminPage = lazy(() => import('../pages/AdminPage.jsx'));

// 함수 역할: AppRoutes 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function AppRoutes() {
  return (
    <Suspense fallback={<div className="route-loading" role="status">화면을 불러오는 중입니다.</div>}>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/upload" element={<UploadPage />} />
            <Route path="/analyses/:jobId/status" element={<AnalysisStatusPage />} />
            <Route path="/results" element={<ResultListPage />} />
            <Route path="/results/:resultId" element={<ResultDetailPage />} />
          </Route>
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<AdminPage />} />
            <Route path="/admin/system-logs" element={<AdminPage />} />
            <Route path="/admin/system-logs/:logId" element={<AdminPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
