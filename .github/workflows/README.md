# GitHub Actions 워크플로우

## 기준

| 항목 | 내용 |
| --- | --- |
| 갱신일 | 2026-07-17 |
| 위치 | `.github/workflows` |

## 현재 워크플로우

### CI

파일: `ci.yml`

트리거:

- `main`, `develop` 브랜치 push
- `main`, `develop` 대상 pull request
- 수동 실행. 배포 입력은 기본 `false`이며 명시적으로 선택한 영역만 전체 검증 뒤 배포

작업:

- 변경 경로를 감지해 backend, frontend, 운영 구성 job을 필요한 경우에만 실행
- Backend: Java 21 설정, Gradle test, JaCoCo report, coverage verification
- PostgreSQL: migration SQL 적용과 Spring/Flyway application context smoke test
- Frontend: Node 22 설정, `npm ci`, lint, i18n·UI 규약 검사, type-check, coverage, build, Playwright E2E·접근성 검사
- Ops: actionlint, Prometheus, Grafana JSON, shell, systemd, Compose 구성 검사
- CI Gate: 변경 감지와 필요한 test/smoke/ops job의 성공·건너뜀 상태 확인
- Deploy: main push의 관련 변경 또는 명시적인 수동 입력에 대해 CI Gate 성공 뒤 reusable workflow 호출
- Frontend coverage artifact 업로드 단계는 `npm run coverage` 결과인 `frontend/coverage`를 업로드한다.

특징:

- backend coverage verification은 실패 시 CI를 실패시킨다.
- backend test/coverage artifact와 frontend coverage·Playwright artifact를 업로드한다.
- 모든 third-party Action은 검증한 release의 40자리 commit SHA로 고정하고 release tag를 주석으로 남긴다.
- workflow 기본 권한은 `contents: read`이고, 변경 감지 job에만 `pull-requests: read`를 추가한다.
- job timeout은 변경 감지·gate 5분, backend·PostgreSQL 30분, frontend 40분이다.

### Deploy Backend

파일: `deploy-backend.yml`

호출 조건:

- `ci.yml`의 전체 gate를 통과한 main backend 변경
- `ci.yml` 수동 실행에서 backend 배포를 명시적으로 선택하고 전체 gate를 통과한 경우

작업:

1. 동일 commit의 Gradle bootJar 빌드
2. EC2 release 디렉터리로 JAR과 버전 관리되는 활성화 script 업로드
3. 서비스 재시작과 8081 health 검증
4. 활성화 이후 실패 시 이전 JAR 복구와 이전 health 재검증
5. 성공 후 mtime 기준 현재 release를 포함한 최신 5개 보존

특징:

- test, coverage, PostgreSQL smoke 실패는 앞선 CI Gate에서 배포를 차단한다.
- 배포 SSH 접속은 GitHub Secrets를 사용한다.
- workflow 권한은 `contents: read`, job timeout은 30분이다.

### Deploy Frontend

파일: `deploy-frontend.yml`

호출 조건:

- `ci.yml`의 전체 gate를 통과한 main frontend 변경
- `ci.yml` 수동 실행에서 frontend 배포를 명시적으로 선택하고 전체 gate를 통과한 경우

작업:

1. Node 22 설정
2. `npm ci`
3. `npm run build:seo` 실행
   - `sitemap:generate`
   - `vite build`
   - `prerender:posts`
4. EC2 release 디렉터리에 `frontend/dist` 정적 파일 업로드
5. `/var/www/app` symlink를 새 release로 원자적으로 전환
6. 배포 결과와 pre-render metadata 검증
7. `npm run seo:verify` 실행
8. `npm run seo:submit` 실행

특징:

- `seo:verify`는 배포 후 검증 단계이며 실패하면 워크플로우가 실패한다.
- `seo:submit`은 `continue-on-error: true`로 설정되어 검색 엔진 제출 실패가 배포 결과를 막지 않는다.
- sitemap 생성에는 `SITEMAP_SITE_URL`, `SITEMAP_API_BASE_URL` 환경 변수를 사용한다.
- workflow 권한은 `contents: read`, job timeout은 30분이다.

### SEO Monitor

파일: `seo-monitor.yml`

트리거:

- schedule
- 수동 실행 `workflow_dispatch`

작업:

- sitemap/SEO 상태 확인
- Google Search Console 또는 custom submit URL 관련 환경 변수는 Secrets에서 주입
- workflow 권한은 `contents: read`, job timeout은 15분

## 필요한 Secrets

배포:

- `EC2_HOST`
- `EC2_SSH_KEY`

SEO/Search Console:

- `GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_ID`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET`
- `GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN`
- `CUSTOM_SITEMAP_SUBMIT_URL`

Secrets 값은 워크플로우 로그, 문서, 예제 파일에 직접 기록하지 않는다.

## 유지보수 원칙

- workflow 파일을 수정하면 이 README도 함께 갱신한다.
- action major version 변경은 CI에서 먼저 확인한 뒤 배포 workflow에 반영한다.
- Action은 tag만 사용하지 않고 공식 release tag가 가리키는 full commit SHA를 사용한다.
- 자동 배포는 독립 push trigger를 갖지 않으며 반드시 통합 CI Gate 뒤에서 호출한다.
- Flyway 변경은 `docs/ops/database-migration-policy.md`와 migration compatibility 검사를 통과해야 한다.
