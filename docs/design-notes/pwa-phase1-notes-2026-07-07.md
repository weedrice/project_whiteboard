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

위 항목은 Phase 1 당시 알림 정책과 권한 UX를 먼저 확정하기 위해 후속 단계로 이관했다.

## 후속 구현 상태

2026-08-13 현재 제외 항목은 후속 단계에서 구현이 완료됐다.

- 사용자 설정과 온보딩에서 브라우저 알림 권한 요청 및 구독·해지 UI 제공
- VAPID 공개키 조회와 사용자별 Web Push 구독 등록·단건 해지·전체 해지 API 제공
- `push_subscriptions`에 브라우저 구독을 저장하고 사용자 `pushEnabled` 설정을 발송 전에 확인
- 알림 이벤트와 구독별 `push_delivery_jobs`를 생성하고 scheduler가 재시도·만료·dead-letter 처리
- 알림 유형별 수신 설정을 통과해 생성된 알림을 활성 브라우저 구독으로 fan-out

현재 Web Push 동작과 운영 계약은
[알림 도메인 가이드](../../backend/src/main/java/com/weedrice/whiteboard/domain/notification/NOTIFICATION_GUIDE.md)를 기준으로 한다.

## 확인 방법

- 프론트 테스트: `npm.cmd run test:run`
- 타입 확인: `npm.cmd run type-check`
- 빌드 산출물의 manifest 아이콘 경로와 service worker fallback 설정 확인
