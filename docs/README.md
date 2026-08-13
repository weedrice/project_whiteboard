# 문서 인덱스

프로젝트 문서는 목적에 따라 설계 노트, 운영 가이드, QA 체크리스트, PostgreSQL 참고 SQL로 구분한다.

## 설계 노트

- [프론트엔드 색상 토큰 사용 기준](design-notes/frontend-color-token-guidelines-2026-05-29.md): 공통 색상 토큰과 다크모드 대응 원칙을 정리한다.
- [콘텐츠 접근 계약 hardening](design-notes/content-access-contract-hardening-2026-07-18.md): 제한 콘텐츠 접근 응답과 클라이언트 처리 계약을 정리한다.
- [키워드 알림 성능](design-notes/keyword-notification-performance-2026-07-09.md): 키워드 매칭 쿼리의 제약과 비동기 처리 방식을 설명한다.
- [멘션 도메인 설계](design-notes/mention-domain-design-2026-07-08.md): 멘션 저장, 파싱, 알림 책임의 후속 구현 방향을 정리한다.
- [멘션 알림 정책](design-notes/mention-notification-policy-2026-07-07.md): 게시글과 댓글의 멘션 알림 생성 정책을 기록한다.
- [알림 다국어 설계](design-notes/notification-i18n-design-2026-07-08.md): 알림 메시지의 다국어 렌더링 모델과 이행 방향을 정리한다.
- [알림 미읽음 그룹 유일성](design-notes/notification-unread-group-uniqueness-2026-07-17.md): 미읽음 알림 그룹의 중복 방지 계약을 기록한다.
- [비밀번호 재설정 토큰 보존](design-notes/password-reset-token-retention-2026-07-17.md): 만료 토큰 보존과 정리 정책을 설명한다.
- [보존 게시글 HTML 검색 인덱스](design-notes/preserved-post-html-search-index-2026-08-10.md): 원본 HTML 보존과 검색용 텍스트 인덱스의 역할을 구분한다.
- [PWA 1단계 노트](design-notes/pwa-phase1-notes-2026-07-07.md): 서비스 워커, 매니페스트, 오프라인 처리의 적용 범위를 기록한다.
- [리텐션·참여 3단계 구현](design-notes/retention-engagement-phase3-implementation-2026-07-08.md): 읽기 재개, 웹 푸시, 온보딩, 키워드 알림의 구현 상태를 정리한다.
- [LIKE 검색 최적화 설계](design-notes/search-like-to-fulltext-design.md): trigram과 전문 검색을 포함한 검색 최적화 현황과 운영 주의를 설명한다.
- [의미 검색 재색인 재시도 상태](design-notes/semantic-reindex-retry-state-2026-07-17.md): 재색인 작업의 실패·재시도 상태 계약을 기록한다.

## 감사와 백로그

- [API 컨트롤러 엔드포인트 감사](api-controller-endpoint-audit-2026-07-10.md): 컨트롤러와 API 명세의 대조 이력을 기록한다.
- [코드 중복·유지보수성 검토](code-duplication-maintainability-review-2026-07-31.md): 중복 코드와 유지보수 위험을 분석한다.
- [프론트엔드·백엔드 계약 감사](frontend-backend-contract-audit-2026-07-14.md): 양쪽 API 계약의 불일치와 조치 결과를 정리한다.
- [프론트엔드·백엔드 개선 백로그](frontend-backend-improvement-backlog-2026-07-25.md): 계약과 구조 개선 작업의 상태를 추적한다.

## 운영

- [Agent heartbeat 대시보드 API](ops/agent-heartbeat-dashboard-api.md): 에이전트 상태와 heartbeat 대시보드를 위한 API 계약을 설명한다.
- [애플리케이션 서비스 hardening](ops/application-service-hardening.md): systemd 서비스 보안과 운영 계약을 설명한다.
- [데이터베이스 마이그레이션 정책](ops/database-migration-policy.md): Flyway expand/contract 및 배포 호환성 규칙을 설명한다.
- [Amazon RDS PostgreSQL 백업·복구](ops/postgres-backup-restore.md): 자동 백업, PITR, 격리 복구와 운영 전환 절차를 설명한다.
- [로컬 Docker Compose](ops/docker-compose-local.md): 백엔드와 프론트엔드 로컬 컨테이너 실행 및 점검 방법을 안내한다.

## QA

- [프론트엔드 다크모드 smoke 체크리스트](qa/frontend-dark-mode-smoke-checklist-2026-05-29.md): 색상 토큰 변경 후 자동·수동 회귀 점검 항목을 제공한다.
- [프론트엔드 영문 레이아웃 QA 체크리스트](qa/frontend-en-layout-smoke-checklist.md): 영어 번역의 긴 라벨을 주요 20개 화면과 네 가지 viewport에서 점검한다.

## PostgreSQL 참고 SQL

- [익명 광고 클릭 사유](sql/ad-click-log-anonymous-reason-postgres.sql): 익명 광고 클릭 로그 사유 컬럼 보강 SQL이다.
- [관리자 역할 제약](sql/admin-role-hardening-postgres.sql): 관리자 역할 데이터의 제약을 강화하는 SQL이다.
- [스페이스 구독 정렬 순서](sql/board-subscription-sort-order-hardening-postgres.sql): 구독 정렬 순서 데이터의 제약을 강화하는 SQL이다.
- [문의 스페이스 초기화](sql/inquiry-board-bootstrap-hardening-postgres.sql): 문의 스페이스 초기 데이터를 안전하게 보강하는 SQL이다.
- [메시지 큐 처리 복구](sql/message-queue-processing-recovery-postgres.sql): 메시지 큐 처리 상태 복구를 지원하는 SQL이다.
- [신고 enum 제약](sql/report-enum-hardening-postgres.sql): 신고 관련 enum 데이터의 제약을 강화하는 SQL이다.
- [검색 개인화 정규화 키워드](sql/search-personalization-normalized-keyword-hardening-postgres.sql): 검색 개인화 키워드 정규화 제약을 보강하는 SQL이다.
- [상점 아이템 권한 대상](sql/shop-item-entitlement-target-postgres.sql): 상점 아이템 권한 대상 구조를 보강하는 SQL이다.
- [소셜 계정 연결 제약](sql/social-account-link-hardening-postgres.sql): 소셜 계정 연결 데이터의 무결성을 강화하는 SQL이다.
- [인증 코드 목적 제약](sql/verification-code-purpose-hardening-postgres.sql): 인증 코드 목적 데이터의 제약을 강화하는 SQL이다.
