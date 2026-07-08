// 역할: 공통 네비게이션과 페이지 레이아웃을 구성합니다.
import { Link, NavLink, Outlet } from 'react-router-dom';
import useAuth from '../hooks/useAuth.js';

// 함수 역할: MainLayout 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function MainLayout() {
  const { user, logout } = useAuth();
  const canOpenConsole = user?.role === 'ADMIN' || user?.role === 'MANAGER';

  return (
    <>
      <header className="app-header">
        <Link to="/" className="brand" aria-label="MulMoCo 홈">
          <span className="brand-symbol">M</span>
          <span className="brand-copy">
            <strong>MulMoCo</strong>
            <small>Presentation Intelligence</small>
          </span>
        </Link>

        <nav className="nav-links" aria-label="주요 메뉴">
          <NavLink to="/upload">분석 시작</NavLink>
          <NavLink to="/results">리포트 보관함</NavLink>
          {canOpenConsole && <NavLink to="/admin">운영 콘솔</NavLink>}
          {user?.role === 'ADMIN' && <NavLink to="/admin/system-logs">시스템 로그</NavLink>}
        </nav>

        <div className="header-actions">
          {user ? (
            <>
              <span className="user-chip">{user.role || 'USER'} · {user.nickname || user.email}</span>
              <button className="outline-button" onClick={logout}>로그아웃</button>
            </>
          ) : (
            <>
              <Link className="outline-link" to="/login">로그인</Link>
              <Link className="primary-link" to="/signup">시작하기</Link>
            </>
          )}
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </>
  );
}
