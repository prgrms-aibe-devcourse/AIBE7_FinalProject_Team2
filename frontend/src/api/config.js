// API 설정. 환경변수는 빌드 시점에 주입된다 (frontend/.env.example 참고)

// API 기본 경로. 개발 서버는 /api 요청을 백엔드(8080)로 프록시하므로 기본값 그대로 쓴다.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

// 화면이 쓰는 API 종류. mock(기본)은 HTTP 요청 없이 목 데이터로, real은 실제 백엔드로 요청한다.
export const API_MODE = import.meta.env.VITE_API_MODE === 'real' ? 'real' : 'mock';
