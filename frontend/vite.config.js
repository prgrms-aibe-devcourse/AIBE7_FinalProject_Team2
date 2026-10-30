import { defineConfig, loadEnv } from 'vite'

// 배포 빌드(VITE_API_MODE=real)에 목 모듈이 들어가면 빌드를 멈춘다 (FE-135, PR #354 리뷰).
// 파일 이름 검사(frontend-ci.yml)만으로는 목을 정적으로 import 해 이름에 mock이 없는 청크에 섞인 경우를 놓친다 → 청크가 담은 모듈 경로를 본다.
// 목 모듈 = src/mocks/ 아래 파일 + 이름에 Mock이 든 파일(관리자 목 pages/admin/adminMockApi.js · adminOpsMockApi.js · adminMockBase.js)
// 허용: mocks/mockStageButtons.js — 실제 화면이 정적으로 쓰는 껍데기이고, 배포 빌드에서는 안쪽의 목 저장소 import가 접혀 빠진다
const MOCK_DIR = '/src/mocks/'
const ALLOWED_MOCK_MODULES = ['/src/mocks/mockStageButtons.js']
function noMocksInRealBuild(mode) {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const real = (process.env.VITE_API_MODE ?? env.VITE_API_MODE) === 'real'
  return {
    name: 'no-mocks-in-real-build',
    apply: 'build',
    generateBundle(_options, bundle) {
      if (!real) return
      const leaked = new Set()
      for (const output of Object.values(bundle)) {
        if (output.type !== 'chunk') continue
        const moduleIds = output.moduleIds ?? Object.keys(output.modules ?? {})
        for (const id of moduleIds) {
          const path = id.replaceAll('\\', '/')
          if (!path.includes('/src/')) continue
          const isMock = path.includes(MOCK_DIR) || /mock/i.test(path.slice(path.lastIndexOf('/') + 1))
          if (isMock && !ALLOWED_MOCK_MODULES.some((allowed) => path.endsWith(allowed))) leaked.add(path.slice(path.indexOf('/src/') + 1))
        }
      }
      if (leaked.size) {
        this.error(`배포 빌드(VITE_API_MODE=real)에 목 모듈이 들어갔습니다: ${[...leaked].join(', ')} — frontend/src/mocks/README.md의 FE-135 규칙을 확인하세요`)
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [noMocksInRealBuild(mode)],
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
}))
