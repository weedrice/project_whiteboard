# GitHub Actions 운영 계약

## 통합 CI와 배포

`ci.yml`은 `main`·`develop` push와 pull request를 검증한다. 수동 배포 입력의 기본값은 모두 `false`이며 production 배포는 `main`에서만 허용한다. 변경 감지 뒤 선택된 backend, frontend, ops job이 모두 성공해야 `ci-gate`를 통과한다. 선택된 필수 job이 `skipped`여도 gate는 실패한다.

검증 job은 다음 책임을 가진다.

- Backend: Java 21, Gradle test, JaCoCo coverage verification
- PostgreSQL: Flyway 호환성 검사, 전체 migration 적용, 실제 PostgreSQL application-context smoke
- Frontend: Node 22, lint, i18n·UI 규약, type-check, coverage, build, Playwright E2E·접근성
- Ops: actionlint, Prometheus rule fixture, Grafana JSON, shell, sudoers, systemd, migration·activation fixture
- CI gate: 선택 여부와 실제 job 결과를 대조하고 우회된 `skipped` 또는 실패를 차단

자동·수동 배포는 검증이 끝난 동일 실행에서 release artifact를 한 번 생성한다. 권한 없는 candidate job이 빌드하고, Gradle/npm을 실행하지 않는 별도 release job만 OIDC·attestation 쓰기 권한으로 candidate digest를 서명한다. artifact 이름은 영역, `run_id`, `run_attempt`, commit SHA를 모두 포함하며 reusable deployment workflow는 그 정확한 이름만 내려받는다. 서명된 metadata의 `run_number/run_attempt`가 배포 세대 순서이며 `run_id`는 정확한 실행 식별에만 사용한다. 재실행이 이전 attempt의 artifact를 재사용하면 안 된다.

backend와 frontend가 함께 변경되면 backend를 먼저 활성화한다. SSH action 결과만으로 성공을 판정하지 않고 별도 readback 연결에서 관리 health, build-info, root-owned active-state digest를 다시 검증한 경우에만 reusable backend workflow가 `activated_sha`를 출력한다. frontend도 내부·공개 release endpoint를 별도 연결에서 재확인하며, 전달받은 backend SHA가 자신의 대상 SHA와 같은지 확인한 뒤 결과를 확정한다. 따라서 activation 직후 전송 채널이 끊겨도 실제 활성 상태는 reconciliation되고, 반대로 성공 문자열만 남은 실패는 배포 성공이 되지 않는다.

contract migration은 자동 배포하지 않는다. `main`의 수동 실행, `allow_contract_migration=true`, 비어 있지 않은 검증된 snapshot ID, tracked design note, production environment 승인과 GitHub run evidence가 모두 필요하다. Snapshot 조회용 OIDC 권한은 별도 `contract-evidence` job에만 부여하며 SSH activation job에는 전달하지 않는다. Run evidence는 같은 SHA의 임의 deployment가 아니라 해당 run의 성공한 `deploy-backend` reusable job ID와 그 job URL을 기록한 `production` deployment status가 정확히 결합돼야 한다. 신규 적용 기록은 원격 run을 검증하고 `docs/ops/contract-evidence/`의 durable manifest로 보존한다. `backend/scripts/check-migration-compatibility.sh`와 evidence verifier가 base commit 또는 승인 증거를 확인하지 못하면 fail-closed 처리한다.

## 활성화와 정리

활성화 스크립트는 provenance, checksum, commit metadata, 서비스 health를 검증한 뒤 `ACTIVATED_SHA=<sha>`를 출력한다. 이 시점 이후 release 보존 정리, 상태 진단, incoming 삭제 실패는 건강한 release를 rollback하지 않는다. 대신 `CLEANUP_DEBT=...` 경고와 workflow cleanup 결과로 후속 조치한다. 실패 진단의 애플리케이션 로그·journal 원문은 Actions 출력으로 보내지 않고 host의 root-only 진단 파일에만 저장한다.

backend/frontend artifact는 payload·metadata·SBOM·SHA-256 manifest의 digest를 담은 `RELEASE_ENVELOPE`, envelope provenance attestation, 실제 payload를 대상으로 한 SBOM attestation을 포함한다. 서버는 envelope attestation을 먼저 검증하고 내부 digest와 payload SBOM attestation을 각각 확인하므로 deploy 계정이 checksum과 SBOM을 함께 바꿔도 활성화할 수 없다. frontend SBOM은 source tree가 아니라 실제 배포 `dist`를 대상으로 생성한다. attestation bundle 다운로드는 bounded exponential backoff로 재시도하며 소진되면 release 생성을 실패시킨다. 배포 직전 최신 `origin/main`을 fetch하고 대상 이후의 변경 경로를 영역별로 비교한다. backend와 frontend에 무관한 문서 변경은 이미 검증된 배포를 막지 않지만 해당 영역 또는 공통 운영 경로가 바뀐 stale artifact는 차단한다. SSH와 SCP는 독립적으로 확인한 host fingerprint를 필수로 사용한다. production deploy concurrency는 활성 실행을 취소하지 않고 최신 대기 실행 하나를 보존해 직렬화한다.

backend activator는 이전 JAR을 보존하고 서비스 stop, atomic JAR 교체, 8081 management health와 build-info 검증을 수행한다. root-only active-state는 `pending`으로 시작해 연속 health 검증 후에만 `stable`이 되며 systemd도 시작 전 JAR digest와 상태를 독립 검증한다. 이전 `run_number/run_attempt`의 재생은 root-only 일회성 break-glass 사유가 없으면 거부한다. frontend activator도 root state를 기록하며 별도 verifier가 symlink, release metadata, envelope digest와 실행 세대를 독립 readback한다. frontend-only 배포는 서명된 API contract revision이 현재 stable backend와 일치해야 한다. release 정리는 mtime 기준 최신 5개를 보존하고 realpath가 release root 밖이면 삭제하지 않는다. incoming 정리는 단순 성공 플래그가 아니라 root-owned helper가 실제 디렉터리를 다시 열거해 orphan 수와 가장 오래된 age를 기록하며, 잔재가 있으면 cleanup debt를 유지한다.

## SEO

production frontend release는 `SEO_STRICT=true`로 sitemap과 prerender를 생성한다. API 조회 실패, 게시글 URL 0건, URL과 prerender 개수 불일치는 release 생성을 실패시킨다. `.noviis-seo-release.json`에 commit SHA, 전체 URL 수, 게시글 URL 수, prerender 수와 sitemap SHA-256을 기록한다. 배포 후 검증과 정기 monitor도 게시글 URL 0건을 거부하고 public sitemap의 개수·digest·release SHA를 이 manifest와 대조한다.

배포 전에는 provider 존재 여부, Google credential 묶음, custom HTTPS origin allowlist를 먼저 검증한다. 배포 후 sitemap 제출과 `seo-monitor.yml`의 정기 제출은 `seo-submit-production` concurrency group으로 직렬화한다. production 제출은 Google 또는 custom provider가 최소 하나 없거나 제출이 실패하면 warning으로 완화하지 않고 workflow를 실패시키며 `frontend/seo_submission` debt를 기록한다. 성공한 재실행은 debt를 해제한다. 이 실패는 이미 검증된 frontend 활성화를 되돌리지는 않으므로 운영자는 실패한 제출 job을 재실행한다. Google refresh credential 세 값은 all-or-none이며, custom endpoint는 별도 HTTPS origin allowlist와 globally routable DNS 검증을 통과한 IP로 연결을 고정하되 원 hostname의 TLS SNI·Host를 유지한다. Node의 단일·다중 주소 lookup 계약 모두 같은 검증 IP만 반환하며 redirect와 DNS rebinding은 허용하지 않는다. 외부 응답 body는 오류 로그에 포함하지 않는다. 정기 제출의 인증 오류, 429, 5xx, timeout은 job 실패다.

## Ops 검증

`ops-config-test`는 actionlint에 더해 YAML AST 기반 권한·concurrency·artifact identity 계약, Prometheus config/rules/fixtures, metric manifest, Grafana JSON, shell, systemd, migration policy, activation fixture를 검증한다. 기존 테이블의 신규 인덱스는 bounded `lock_timeout`, `CREATE INDEX CONCURRENTLY`, Flyway 비트랜잭션 sidecar를 모두 갖춰야 한다. Prometheus·Grafana의 승인 버전과 host exporter의 최소 호환 버전은 `deploy/monitoring/tool-versions.env`에 기록한다. 운영 host는 Prometheus·Grafana의 동일 native 버전과 최소 버전 이상의 배포판 host exporter를 사용한다.

non-Agent `@Scheduled` 메서드는 `scheduled-jobs.txt`와 freshness rule이 일치해야 한다. sudoers는 `visudo -cf`와 허용·거부 command matrix를 모두 통과해야 한다. systemd 메모리 상한은 운영 측정 기록과 staging 검증이 없으면 추가하지 않는다.

Grafana 관리 비밀번호는 `/etc/noviis/monitoring.env`와 root-only 회전 helper로만 관리한다. helper는 loopback User API를 사용하고 비밀번호를 argv나 shell history에 넣지 않는다. Prometheus rule은 로컬에서 평가되지만 외부 Alertmanager receiver는 아직 없으므로 firing 자체가 Slack 또는 이메일 전달을 의미하지 않는다.

## 권한과 유지보수

workflow 기본 권한은 `contents: read`이며 attestation, OIDC, artifact metadata 권한은 필요한 release/deploy job에만 부여한다. PR에서 repository script를 실행하는 PostgreSQL·ops job에는 `actions: read`나 `deployments: read`를 부여하지 않는다. 적용 완료 contract evidence의 GitHub API 검증은 보호된 `main`의 push 또는 수동 실행에서만 별도 job으로 수행한다. third-party Action은 검토한 release의 full commit SHA로 고정한다. workflow, activation script, sudoers, migration 정책 변경은 CODEOWNERS review 대상이다.

배포 freshness 경계의 source of truth는 `deploy/release-freshness-paths.txt`다. workflow가 참조하는 activation·verification·provenance 파일이 이 manifest에서 빠지면 ops CI가 실패한다. Contract 배포는 snapshot ID뿐 아니라 SHA에 결합된 change ticket을 요구하며, production checkout은 Git credential을 보존하지 않는다.

Contract evidence OIDC role은 production snapshot describe·tag 조회와 검증 완료 snapshot에 `noviis:contract-consumed` 태그를 추가하는 권한만 가진다. 일반 backend activation job에는 AWS OIDC 권한을 전달하지 않는다.

Pinned actionlint 1.7.7은 GitHub의 2026 `concurrency.queue`와 `artifact-metadata` permission schema를 아직 알지 못하므로 CI는 그 두 exact parser diagnostics만 무시한다. 별도 YAML AST 계약이 `queue: max`, 최소 permission, main-only deploy, secret allowlist를 검증한다. actionlint가 두 필드를 지원하는 버전으로 갱신되면 ignore도 같은 변경에서 제거한다.

주요 timeout은 change detection·gate·SEO preflight 5분, contract evidence 10분, backend test 45분, frontend test 60분, PostgreSQL·ops 30분, release 20–25분, deploy 30분, SEO 검증 15분·제출 10분이다. YAML에서 값을 바꾸면 이 문서도 같은 변경에서 갱신한다.

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
- `AWS_EXPECTED_ACCOUNT_ID`
- `RDS_SNAPSHOT_KMS_KEY_ARN`
- `RDS_ENGINE_MAJOR_VERSION`

SEO 제출은 아래 Google credential 묶음 또는 custom provider 묶음 중 최소 하나가 필요하다. Google refresh credential 세 값은 모두 설정하거나 모두 비워야 한다.

- `GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_ID`
- `GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET`
- `GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN`
- `CUSTOM_SITEMAP_SUBMIT_URL`
- `CUSTOM_SITEMAP_SUBMIT_ALLOWED_ORIGINS` (쉼표로 구분한 HTTPS origin allowlist)

`EC2_HOST_FINGERPRINT`는 배포 연결과 독립적인 채널에서 확인한 SHA-256 host-key fingerprint여야 한다. secret 값은 workflow 로그, fixture, 문서, release metadata에 기록하지 않는다. reusable workflow에는 필요한 secret만 명시적으로 매핑하며 `secrets: inherit`를 사용하지 않는다.
