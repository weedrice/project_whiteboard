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
- 수동 실행. 배포 입력은 기본 `false`이며 `main`에서 명시적으로 선택한 영역만 전체 검증 뒤 배포
- contract migration 수동 배포는 별도 승인 입력과 비어 있지 않은 사전 snapshot 식별자를 모두 요구

작업:

- 변경 경로를 감지해 backend, frontend, 운영 구성 job을 필요한 경우에만 실행
- Backend: Java 21 설정, Gradle test, JaCoCo report, coverage verification
- PostgreSQL: migration SQL 적용과 Spring/Flyway application context smoke test
- Frontend: Node 22 설정, `npm ci`, lint, i18n·UI 규약 검사, type-check, coverage, build, Playwright E2E·접근성 검사
- Ops: actionlint, Prometheus, Grafana JSON, shell, systemd, Compose 구성 검사
- CI Gate: 선택된 test/smoke/ops job은 반드시 성공해야 하고 선택되지 않은 job만 `skipped`인지 확인
- Deploy: main push의 관련 변경 또는 main 수동 입력에 대해 CI Gate 성공 뒤 reusable workflow 호출
- Release artifact: CI가 만든 JAR 또는 frontend tarball, SBOM, commit metadata와 SHA-256 manifest를 배포 workflow가 그대로 승격하고 GitHub artifact attestation을 발급
- Frontend coverage artifact 업로드 단계는 `npm run coverage` 결과인 `frontend/coverage`를 업로드한다.

특징:

- backend coverage verification은 실패 시 CI를 실패시킨다.
- backend 의존성은 Gradle lock state와 strict SHA-256 verification metadata로 재현성과 무결성을 검사한다.
- backend test/coverage artifact와 frontend coverage·Playwright artifact를 업로드한다.
- backend/frontend 변경은 실제 Dockerfile build까지 수행하며 production Nginx 구성도 `nginx -t`로 확인한다.
- 모든 third-party Action은 검증한 release의 40자리 commit SHA로 고정하고 release tag를 주석으로 남긴다.
- workflow 기본 권한은 `contents: read`이고, 변경 감지 job에만 `pull-requests: read`를 추가한다.
- job timeout은 변경 감지·gate 5분, backend·PostgreSQL 30분, frontend 40분이다.

### Deploy Backend

파일: `deploy-backend.yml`

호출 조건:

- `ci.yml`의 전체 gate를 통과한 main backend 변경
- `ci.yml` 수동 실행에서 backend 배포를 명시적으로 선택하고 전체 gate를 통과한 경우

작업:

1. CI가 검증하고 checksum을 기록한 동일 commit JAR 다운로드
2. checksum·commit metadata를 검증한 뒤 EC2 release 디렉터리로 JAR과 활성화 script 업로드
3. 서비스 재시작과 8081 health 검증
4. 활성화 이후 실패 시 이전 JAR 복구와 이전 health 재검증
5. 성공 후 mtime 기준 현재 release를 포함한 최신 5개 보존

특징:

- test, coverage, PostgreSQL smoke 실패는 앞선 CI Gate에서 배포를 차단한다.
- 배포 SSH 접속은 GitHub Secrets를 사용한다.
- 활성화 직전 origin/main SHA가 검증 artifact의 SHA와 다르면 stale deployment를 차단한다.
- SSH/SCP는 등록된 EC2 host key fingerprint를 검증한다.
- workflow 권한은 `contents: read`, job timeout은 30분이다.

### Deploy Frontend

파일: `deploy-frontend.yml`

호출 조건:

- `ci.yml`의 전체 gate를 통과한 main frontend 변경
- `ci.yml` 수동 실행에서 frontend 배포를 명시적으로 선택하고 전체 gate를 통과한 경우

작업:

1. CI가 `npm run build:seo`로 생성하고 checksum을 기록한 tarball 다운로드
2. checksum과 내장 commit marker 검증
3. 배포 후 SEO 검증을 위한 Node 22와 `npm ci` 실행
   - `sitemap:generate`
   - `vite build`
   - `prerender:posts`
4. EC2 release 디렉터리에 검증된 tarball 업로드
5. 별도 활성화 script가 `/var/www/app` symlink를 새 release로 원자적으로 전환
6. commit marker와 pre-render metadata 검증, 실패 시 이전 symlink 복구
7. `npm run seo:verify` 실행
8. `npm run seo:submit` 실행

특징:

- `seo:verify`는 배포 후 검증 단계이며 실패하면 워크플로우가 실패한다.
- 배포 후 `seo:verify`가 실패해도 이전 frontend release로 되돌린다.
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
- `EC2_HOST_FINGERPRINT`

`EC2_HOST_FINGERPRINT` must be the independently verified SHA-256 host-key fingerprint expected by Appleboy SSH/SCP actions. Do not derive trust from the same unauthenticated deployment connection.

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
- production 배포는 main SHA만 허용하고 활성화 직전에 최신 main인지 다시 확인한다.
- backend와 frontend가 함께 바뀌면 backend 배포를 먼저 완료한다.
- GitHub의 `production` environment는 main deployment branch restriction, required reviewer, self-review 금지를 별도로 활성화한다.
- Flyway 변경은 `docs/ops/database-migration-policy.md`와 migration compatibility 검사를 통과해야 한다.
