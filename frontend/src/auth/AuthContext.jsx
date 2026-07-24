// 역할: 로그인 상태, 권한별 라우트 보호, 인증 컨텍스트를 관리합니다.
import { createContext, useEffect, useMemo, useState } from 'react';
import { getMe, logout as requestLogout } from '../api/authApi.js';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const token = localStorage.getItem('accessToken');
    const role = localStorage.getItem('role');
    const email = localStorage.getItem('email');
    const nickname = localStorage.getItem('nickname');
    return token ? { email, nickname, role: role || 'USER' } : null;
  });

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) return;
    getMe()
      .then(({ data }) => {
        const nextUser = data.data;
        localStorage.setItem('email', nextUser.email);
        localStorage.setItem('nickname', nextUser.nickname);
        localStorage.setItem('role', nextUser.role);
        setUser(nextUser);
      })
      .catch((error) => {
        if (error.response?.status === 429) return;
        localStorage.removeItem('accessToken');
        setUser(null);
      });
  }, []);

  const value = useMemo(() => ({
    user,
    loginWithToken(auth) {
      localStorage.setItem('accessToken', auth.accessToken);
      localStorage.setItem('role', auth.role);
      localStorage.setItem('email', auth.email);
      localStorage.setItem('nickname', auth.nickname);
      setUser({ email: auth.email, nickname: auth.nickname, role: auth.role });
    },
    async logout() {
      await requestLogout().catch(() => {});
      localStorage.removeItem('accessToken');
      localStorage.removeItem('role');
      localStorage.removeItem('email');
      localStorage.removeItem('nickname');
      setUser(null);
    },
  }), [user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
