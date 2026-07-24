// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import axios from 'axios';

const httpClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

export const RATE_LIMIT_MESSAGE = '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';

export function apiErrorMessage(error, fallbackMessage) {
  if (error.response?.status === 429) {
    return error.response?.data?.message || RATE_LIMIT_MESSAGE;
  }
  return error.response?.data?.message || fallbackMessage;
}

httpClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 429) {
      error.userMessage = error.response?.data?.message || RATE_LIMIT_MESSAGE;
    }
    return Promise.reject(error);
  },
);

export default httpClient;
