# GitHub Actions 워크플로우

## 기준

| 항목 | 내용 |
| --- | --- |
| 갱신일 | 2026-07-15 |
| 위치 | `.github/workflows` |

## 현재 워크플로우

### CI

파일: `ci.yml`

트리거:

- `main`, `develop` 브랜치 push
- `main`, `develop` 대상 pull request

작업:

- 변경 경로를 감지해 backend와 frontend job을 필요한 경우에만 실행
- Backend: Java 21 설정, Gradle test, JaCoCo report, coverage verification
- PostgreSQL: migration SQL 적용과 Spring/Flyway application context smoke test
- Frontend: Node 22 설정, `npm ci`, lint, i18n key 검사, type-check, coverage, build, Playwright E2E·접근성 검사
- CI Gate: 변경 감지와 필요한 test/smoke job의 성공·건너뜀 상태 확인
- Frontend coverage artifact 업로드 단계는 `npm run coverage` 결과인 `frontend/coverage`를 업로드한다.

특징:

- backend coverage verification은 실패 시 CI를 실패시킨다.
- backend test/coverage artifact와 frontend coverage·Playwright artifact를 업로드한다.
- Actions는 현재 `checkout@v7`, `setup-java@v5`, `setup-node@v7`, `upload-artifact@v7`를 사용한다.
- workflow 기본 권한은 `contents: read`이고, 변경 감지 job에만 `pull-requests: read`를 추가한다.
- job timeout은 변경 감지·gate 5분, backend·PostgreSQL 30분, frontend 40분이다.

### Deploy Backend

파일: `deploy-backend.yml`

트리거:

- `main` 브랜치 push
- 수동 실행 `workflow_dispatch`

작업:

1. Java 21 설정
2. 테스트 실행
3. coverage report 생성
4. coverage 기준 검증
5. Gradle bootJar 빌드
6. EC2 release 디렉터리로 JAR 업로드
7. 서비스 재시작, health/log 검증, 실패 시 이전 JAR 복구

특징:

- coverage verification 실패는 배포를 차단한다.
- 배포 SSH 접속은 GitHub Secrets를 사용한다.
- workflow 권한은 `contents: read`, job timeout은 30분이다.

### Deploy Frontend

파일: `deploy-frontend.yml`

트리거:

- `main` 브랜치 push
- 수동 실행 `workflow_dispatch`

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
- 테스트 실패를 배포 차단으로 볼지, coverage 경고를 허용할지는 workflow별로 명시한다.
