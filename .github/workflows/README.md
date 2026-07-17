# GitHub Actions 운영 계약

## 통합 CI와 배포

`ci.yml`은 `main`·`develop` push와 pull request를 검증한다. 수동 배포 입력의 기본값은 모두 `false`이며 production 배포는 `main`에서만 허용한다. 변경 감지 뒤 선택된 backend, frontend, ops job이 모두 성공해야 `ci-gate`를 통과한다. 선택된 필수 job이 `skipped`여도 gate는 실패한다.

검증 job은 다음 책임을 가진다.

- Backend: Java 21, Gradle test, JaCoCo coverage verification
- PostgreSQL: Flyway 호환성 검사, 전체 migration 적용, 실제 PostgreSQL application-context smoke
- Frontend: Node 22, lint, i18n·UI 규약, type-check, coverage, build, Playwright E2E·접근성
- Ops: actionlint, Prometheus rule fixture, Grafana JSON, shell, sudoers, systemd, migration·activation fixture
- CI gate: 선택 여부와 실제 job 결과를 대조하고 우회된 `skipped` 또는 실패를 차단

자동·수동 배포는 검증이 끝난 동일 실행에서 release artifact를 한 번 생성한다. artifact 이름은 영역, `run_id`, `run_attempt`, commit SHA를 모두 포함하며 reusable deployment workflow는 그 정확한 이름만 내려받는다. 재실행이 이전 attempt의 artifact를 재사용하면 안 된다.

backend와 frontend가 함께 변경되면 backend를 먼저 활성화한다. 관리 health와 build-info에서 SHA가 확인된 경우에만 reusable backend workflow가 `activated_sha`를 출력한다. frontend workflow는 전달받은 backend SHA가 자신의 대상 SHA와 같은지 확인한 뒤 결과를 확정한다.

contract migration은 자동 배포하지 않는다. `main`의 수동 실행, `allow_contract_migration=true`, 비어 있지 않은 검증된 snapshot ID, tracked design note, production environment 승인과 GitHub run evidence가 모두 필요하다. Run evidence는 같은 SHA의 임의 deployment가 아니라 해당 run의 성공한 `deploy-backend` reusable job ID와 그 job URL을 기록한 `production` deployment status가 정확히 결합돼야 한다. `backend/scripts/check-migration-compatibility.sh`와 evidence verifier가 base commit 또는 승인 증거를 확인하지 못하면 fail-closed 처리한다. 적용 완료 migration은 `docs/ops/applied-contract-migrations.txt`와 운영 변경 기록을 함께 갱신한다.

## 활성화와 정리

활성화 스크립트는 provenance, checksum, commit metadata, 서비스 health를 검증한 뒤 `ACTIVATED_SHA=<sha>`를 출력한다. 이 시점 이후 release 보존 정리, 상태 진단, incoming 삭제 실패는 건강한 release를 rollback하지 않는다. 대신 `CLEANUP_DEBT=...` 경고와 workflow cleanup 결과로 후속 조치한다. 실패 진단의 애플리케이션 로그·journal 원문은 Actions 출력으로 보내지 않고 host의 root-only 진단 파일에만 저장한다.

backend/frontend artifact는 SHA-256 manifest, SBOM, GitHub attestation bundle을 포함한다. 배포 직전 최신 `origin/main` SHA를 다시 확인하며 SSH와 SCP는 독립적으로 확인한 host fingerprint를 필수로 사용한다. production deploy concurrency는 취소 없이 직렬화한다.

backend activator는 이전 JAR을 보존하고 서비스 stop, atomic JAR 교체, 8081 management health와 build-info 검증을 수행한다. 검증 전 실패는 이전 JAR과 health를 복구한다. frontend activator는 이전 symlink를 기록하고 atomic switch 뒤 내부·공개 release endpoint 및 SEO metadata를 검증하며 실패 시 이전 symlink를 복구한다. release 정리는 mtime 기준 최신 5개를 보존하고 realpath가 release root 밖이면 삭제하지 않는다.

## SEO

production frontend release는 `SEO_STRICT=true`로 sitemap과 prerender를 생성한다. API 조회 실패, 게시글 URL 0건, URL과 prerender 개수 불일치는 release 생성을 실패시킨다. `.noviis-seo-release.json`에 commit SHA, 전체 URL 수, 게시글 URL 수, prerender 수와 sitemap SHA-256을 기록한다. 배포 후 검증과 정기 monitor도 게시글 URL 0건을 거부하고 public sitemap의 개수·digest·release SHA를 이 manifest와 대조한다.

배포 후 sitemap 제출과 `seo-monitor.yml`의 정기 제출은 `seo-submit-production` concurrency group으로 직렬화한다. 정기 제출의 인증 오류, 429, 5xx, timeout은 job 실패다. 배포 후 제출 실패는 이미 검증된 frontend를 rollback하지 않고 warning과 job summary에 남기며 정기 monitor가 재시도한다.

## Ops 검증

`ops-config-test`는 workflow 문법, Prometheus config/rules/fixtures, Grafana JSON, shell, systemd, migration policy, activation fixture를 검증한다. Prometheus 검증 버전은 `deploy/monitoring/tool-versions.env`에서 읽으며 운영 host도 같은 manifest의 native 버전을 사용한다.

non-Agent `@Scheduled` 메서드는 `scheduled-jobs.txt`와 freshness rule이 일치해야 한다. sudoers는 `visudo -cf`와 허용·거부 command matrix를 모두 통과해야 한다. systemd 메모리 상한은 운영 측정 기록과 staging 검증이 없으면 추가하지 않는다.

Grafana 관리 비밀번호는 `/etc/noviis/monitoring.env`와 root-only 회전 helper로만 관리한다. helper는 loopback User API를 사용하고 비밀번호를 argv나 shell history에 넣지 않는다. Prometheus rule은 로컬에서 평가되지만 외부 Alertmanager receiver는 아직 없으므로 firing 자체가 Slack 또는 이메일 전달을 의미하지 않는다.

## 권한과 유지보수

workflow 기본 권한은 `contents: read`이며 attestation, OIDC, artifact metadata 권한은 필요한 release/deploy job에만 부여한다. third-party Action은 검토한 release의 full commit SHA로 고정한다. workflow, activation script, sudoers, migration 정책 변경은 CODEOWNERS review 대상이다.

주요 timeout은 change detection·gate 5분, backend test 45분, frontend test 60분, PostgreSQL·ops 30분, release 20–25분, deploy 30분, SEO monitor 15분이다. YAML에서 값을 바꾸면 이 문서도 같은 변경에서 갱신한다.

## Production environment와 Secrets

GitHub `production` environment는 `main` branch restriction, required reviewer, self-review 금지를 별도로 설정한다. repository 파일만으로 environment 보호 규칙이 생성되는 것은 아니다. 실제 production 배포와 의도적인 rollback 시험은 운영 권한 단계에서 수행한다.

배포 연결에 필요한 secret:

- `EC2_HOST`
- `EC2_SSH_KEY`
- `EC2_HOST_FINGERPRINT`

contract evidence에 필요한 secret:

- `AWS_CONTRACT_EVIDENCE_ROLE_ARN`
- `AWS_REGION`
- `RDS_PRODUCTION_DB_IDENTIFIER`

SEO 제출에 필요한 선택적 secret:

- `GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_ID`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET`
- `GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN`
- `CUSTOM_SITEMAP_SUBMIT_URL`

`EC2_HOST_FINGERPRINT`는 배포 연결과 독립적인 채널에서 확인한 SHA-256 host-key fingerprint여야 한다. secret 값은 workflow 로그, fixture, 문서, release metadata에 기록하지 않는다. reusable workflow에는 필요한 secret만 명시적으로 매핑하며 `secrets: inherit`를 사용하지 않는다.
