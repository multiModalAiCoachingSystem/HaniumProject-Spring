// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { signup } from '../api/authApi.js';
import { apiErrorMessage } from '../api/httpClient.js';

// 함수 역할: SignupPage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function SignupPage() {
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  // 함수 역할: submit에서 화면 동작에 필요한 상태 처리와 보조 로직을 수행합니다.
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    const form = new FormData(event.currentTarget);
    try {
      await signup(Object.fromEntries(form.entries()));
      alert('회원가입이 완료되었습니다.');
      navigate('/login');
    } catch (exception) {
      setError(apiErrorMessage(exception, '회원가입에 실패했습니다.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="auth-shell">
      <div className="auth-copy">
        <p className="section-kicker">Create Workspace</p>
        <h1>나만의 발표 코칭 공간을 만드세요.</h1>
        <p className="lead-text">MulMoCo는 영상 업로드부터 분석 결과 저장까지 사용자별로 분리해 관리합니다.</p>
        <div className="auth-proof">
          <span>BCrypt 비밀번호 암호화</span>
          <span>이메일 중복 검사</span>
          <span>USER / MANAGER / ADMIN 권한 분리</span>
        </div>
      </div>
      <form className="auth-card" onSubmit={submit}>
        <h2>회원가입</h2>
        <label>
          이메일
          <input name="email" placeholder="name@example.com" />
        </label>
        <label>
          이름 또는 닉네임
          <input name="nickname" placeholder="발표자" />
        </label>
        <label>
          비밀번호
          <input name="password" type="password" placeholder="8자 이상" />
        </label>
        <label>
          비밀번호 확인
          <input name="passwordConfirm" type="password" placeholder="비밀번호 재입력" />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="primary-link auth-submit" disabled={submitting}>{submitting ? '가입 중...' : '가입하기'}</button>
        <p className="auth-switch">이미 계정이 있나요? <Link to="/login">로그인</Link></p>
      </form>
    </section>
  );
}
