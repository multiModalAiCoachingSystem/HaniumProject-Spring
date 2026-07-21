// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import httpClient from './httpClient.js';

// 함수 역할: getResults에서 현재 사용자의 완료 리포트 목록을 조회합니다.
export const getResults = () => httpClient.get('/results');

// 함수 역할: getResult에서 필요한 값이나 상태를 조회해 반환합니다.
export const getResult = (resultId, options = {}) => httpClient.get(`/results/${resultId}`, options);

// 함수 역할: deleteResult에서 현재 사용자의 보관함 리포트를 삭제합니다.
export const deleteResult = (resultId) => httpClient.delete(`/results/${resultId}`);
