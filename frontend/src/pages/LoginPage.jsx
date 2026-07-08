// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { login } from '../api/authApi.js';
import useAuth from '../hooks/useAuth.js';

// 함수 역할: LoginPage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const { loginWithToken } = useAuth();
  const navigate = useNavigate();

  // 함수 역할: submit에서 화면 동작에 필요한 상태 처리와 보조 로직을 수행합니다.
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const { data } = await login({ email, password });
      loginWithToken(data.data);
      navigate('/');
    } catch (exception) {
      setError(exception.response?.data?.message || '로그인에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="auth-shell">
      <div className="auth-copy">
        <p className="section-kicker">Welcome Back</p>
        <h1>코칭 리포트를 이어서 확인하세요.</h1>
        <p className="lead-text">업로드한 발표 영상, 분석 상태, 시간대별 피드백을 안전하게 관리합니다.</p>
        <div className="auth-proof">
          <span>JWT Access Token</span>
          <span>HttpOnly Refresh Cookie</span>
          <span>사용자별 리포트 보호</span>
        </div>
      </div>
      <form className="auth-card" onSubmit={submit}>
        <h2>로그인</h2>
        <label>
          이메일
          <input value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" />
        </label>
        <label>
          비밀번호
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="비밀번호" />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="primary-link auth-submit" disabled={submitting}>{submitting ? '로그인 중...' : '로그인'}</button>
        <p className="auth-switch">계정이 없나요? <Link to="/signup">회원가입</Link></p>
      </form>
    </section>
  );
}
