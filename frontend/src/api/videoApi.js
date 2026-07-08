// 역할: 백엔드 API 호출을 캡슐화해 페이지와 컴포넌트에서 재사용하는 클라이언트 모듈입니다.
import httpClient from './httpClient.js';

// 함수 역할: uploadVideo에서 업로드 파일을 검증하고 저장 또는 응답 데이터를 구성합니다.
export const uploadVideo = (file, onProgress) => {
  const formData = new FormData();
  formData.append('file', file);
  return httpClient.post('/videos', formData, {
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return;
      onProgress(Math.round((event.loaded * 100) / event.total));
    },
  });
};
