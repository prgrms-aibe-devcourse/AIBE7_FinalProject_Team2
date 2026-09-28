import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  server: {
    port: 5173,
    // 로컬 개발 시 /api 요청을 Spring Boot(8080)로 전달해 CORS 설정 없이 연동
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
