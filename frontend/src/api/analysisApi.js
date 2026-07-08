// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import httpClient from './httpClient.js';

// 함수 역할: startAnalysis에서 발표 분석 요청, 엔진 호출, 결과 처리 흐름을 조율합니다.
export const startAnalysis = (videoId) => httpClient.post('/analyses', { videoId });
// 함수 역할: getMyAnalyses에서 현재 사용자의 분석 작업 목록을 조회합니다.
export const getMyAnalyses = () => httpClient.get('/analyses');
// 함수 역할: getAnalysisReports에서 보관함에 표시할 완료/실패 리포트 페이지를 조회합니다.
export const getAnalysisReports = (params) => httpClient.get('/analyses/reports', { params });
// 함수 역할: getAnalysisStatus에서 필요한 값이나 상태를 조회해 반환합니다.
export const getAnalysisStatus = (jobId) => httpClient.get(`/analyses/${jobId}/status`);
// 함수 역할: retryAnalysis에서 실패한 내 분석 작업을 다시 실행합니다.
export const retryAnalysis = (jobId) => httpClient.post(`/analyses/${jobId}/retry`);
// 함수 역할: deleteAnalysisReport에서 완료 또는 실패한 리포트를 삭제합니다.
export const deleteAnalysisReport = (jobId) => httpClient.delete(`/analyses/${jobId}`);
// 함수 역할: getAnalysisResult에서 필요한 값이나 상태를 조회해 반환합니다.
export const getAnalysisResult = (jobId) => httpClient.get(`/analyses/${jobId}/result`);
