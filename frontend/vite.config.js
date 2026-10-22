import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  server: {
    port: 5173,
    // 로컬 개발 시 /api 요청을 Spring Boot(8080)로 전달해 CORS 설정 없이 연동
    proxy: {
      // 카카오 로그인(BE-62)은 원래 주소(Host: localhost:5173)를 그대로 넘긴다. changeOrigin이 켜져 있으면 백엔드가 만드는
      // Redirect URI가 localhost:8080이 되어 카카오 콘솔에 등록한 localhost:5173 주소와 달라 로그인이 막힌다(KOE006).
      // 더 구체적인 경로를 먼저 둔다(앞에서부터 맞는 첫 규칙을 쓴다)
      '/api/v1/auth/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
