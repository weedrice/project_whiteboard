# NoviIs PWA Phase 1 Notes

작성일: 2026-07-07

## 적용 범위

- `vite-plugin-pwa` 기반 서비스 워커 등록
- 앱 manifest와 192/512/maskable 아이콘 제공
- SPA navigation fallback을 `/index.html`로 처리
- API, OAuth, robots/sitemap 요청은 navigation fallback에서 제외
- 오프라인 fallback 문서는 `frontend/public/offline.html`을 사용
- 새 버전 준비 및 오프라인 준비 상태는 토스트로 안내

## 제외 범위

- 웹 푸시 구독, VAPID 키, 브라우저 push permission UI
- 서버의 push subscription 저장소
- 알림 타입별 웹 푸시 fan-out

위 항목은 알림 정책과 권한 UX가 확정된 뒤 별도 단계로 진행한다.

## 확인 방법

- 프론트 테스트: `npm.cmd run test:run`
- 타입 확인: `npm.cmd run type-check`
- 빌드 산출물의 manifest 아이콘 경로와 service worker fallback 설정 확인
