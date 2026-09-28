// 내Law남불 메인 스크립트
import './style.css'

// API 기본 경로 (빌드 시 VITE_API_BASE_URL 환경변수로 주입, 없으면 /api/v1)
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

const app = document.querySelector('#app')

app.innerHTML = `
  <main>
    <h1>내Law남불</h1>
    <p>당신이 판사라면, 어떤 판결을 내리겠습니까?</p>
  </main>
`
