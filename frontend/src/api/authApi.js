// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import httpClient from './httpClient.js';

// 함수 역할: signup에서 회원가입 입력값을 검증하고 사용자 계정을 생성합니다.
export const signup = (payload) => httpClient.post('/auth/signup', payload);
// 함수 역할: login에서 로그인 요청을 검증하고 인증 토큰 발급 흐름을 처리합니다.
export const login = (payload) => httpClient.post('/auth/login', payload);
// 함수 역할: logout에서 로그아웃 요청에 맞춰 세션 또는 토큰 정보를 정리합니다.
export const logout = () => httpClient.post('/auth/logout');
// 함수 역할: getMe에서 필요한 값이나 상태를 조회해 반환합니다.
export const getMe = () => httpClient.get('/auth/me');
