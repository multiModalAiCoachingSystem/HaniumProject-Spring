// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import httpClient from './httpClient.js';

// 함수 역할: 관리자 화면에 표시할 사용자 목록을 조회합니다.
export const getAdminUsers = () => httpClient.get('/admin/users');

// 함수 역할: 관리자 화면에 표시할 분석 작업 목록을 조회합니다.
export const getAdminAnalyses = (status) => httpClient.get('/admin/analyses', { params: { status } });

// 함수 역할: 관리자 화면에 표시할 서비스 상태 정보를 조회합니다.
export const getAdminHealth = () => httpClient.get('/admin/health');

// 함수 역할: 관리자가 선택한 사용자를 추방 처리합니다.
export const expelAdminUser = (userId) => httpClient.delete(`/admin/users/${userId}`);

// 함수 역할: 관리자가 선택한 사용자의 USER/MANAGER 권한을 변경합니다.
export const updateAdminUserRole = (userId, role) => httpClient.patch(`/admin/users/${userId}/role`, { role });

// 함수 역할: 관리자가 선택한 분석 작업을 삭제합니다.
export const deleteAdminAnalysis = (jobId) => httpClient.delete(`/admin/analyses/${jobId}`);
