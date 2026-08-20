# GitHub Actions 운영 계약

## 통합 CI와 배포

`ci.yml`은 `main`·`develop` push와 pull request를 검증한다. 수동 배포 입력의 기본값은 모두 `false`이며 production 배포는 `main`에서만 허용한다. 변경 감지 뒤 선택된 backend, frontend, ops job이 모두 성공해야 `ci-gate`를 통과한다. 선택된 필수 job이 `skipped`여도 gate는 실패한다.

검증 범위와 배포 범위는 별도로 판정한다. CI·배포 workflow, 문서, backend/frontend 테스트만 변경되면 관련 검증 job은 실행하지만 candidate·release·production 배포 job은 실행하지 않는다. 자동 배포 범위는 현재 push 하나가 아니라 GitHub Actions 이력에서 찾은 component별 마지막 성공 production 배포 SHA부터 현재 SHA까지의 누적 diff로 계산한다. 따라서 runtime 변경의 CI가 실패하고 테스트·설정만 수정한 후속 커밋이 성공해도 미배포 runtime 변경은 다음 성공 실행의 배포 범위에 남으며, 해당 component 검증도 다시 실행한다. 성공 배포 이력을 찾지 못하면 안전하게 해당 component를 검증·배포 대상으로 선택하고 contract migration으로 취급해 rollback 제한을 적용한다. `docs/ops/api-contract-revision.txt` 변경은 API 계약 동기화를 위해 backend와 frontend 배포 범위를 함께 선택한다. 수동 `workflow_dispatch`의 명시적 backend 배포도 마지막 성공 backend 배포 SHA를 조회하며, 경로 감지 결과와 무관하게 해당 검증·배포 체인을 실행한다.

검증 job은 다음 책임을 가진다.

- Backend: Java 25, Gradle test, JaCoCo coverage verification
- PostgreSQL: Flyway 호환성·현재 schema smoke와 이전 revision→현재 revision upgrade smoke를 독립 job으로 실행
- Frontend: Node 24, lint, i18n·UI 규약, type-check, coverage, build, Playwright E2E·접근성
- Ops: actionlint, Prometheus rule fixture, Grafana JSON, shell, sudoers, systemd, migration·activation fixture
- CI gate: 선택 여부와 실제 job 결과를 대조하고 우회된 `skipped` 또는 실패를 차단
- Deployment gate: CI 성공 뒤 요청된 backend/frontend production 배포가 `success`가 아니면 workflow를 실패시켜 contract 승인 누락이나 조건식 skip을 숨기지 않음

자동·수동 배포는 검증이 끝난 동일 실행에서 release artifact를 한 번 생성한다. 권한 없는 candidate job이 빌드하고, Gradle/npm을 실행하지 않는 별도 release job만 OIDC·attestation 쓰기 권한으로 candidate digest를 서명한다. artifact 이름은 영역, `run_id`, `run_attempt`, commit SHA를 모두 포함하지만 job 간 전달은 이름을 다시 계산하지 않고 `upload-artifact`가 반환한 immutable artifact ID를 사용한다. 서명된 metadata의 `run_attempt`는 candidate를 실제로 만든 attempt이며 consumer는 producer output으로 전달된 값을 검증한다. 따라서 실패한 job만 재실행해 `github.run_attempt`가 증가해도 성공한 이전 producer artifact를 정확한 ID로 안전하게 이어받고, 전체 재실행에서는 새 artifact ID가 생성된다.

backend와 frontend가 함께 변경되면 backend를 먼저 활성화한다. backend는 별도 readback 연결에서 설치된 JAR digest, systemd 활성 상태와 8081 management health를 다시 검증한 경우에만 `activated_sha`를 출력한다. frontend도 `/var/www/app`의 release identity와 내부·공개 release endpoint를 별도 연결에서 재확인하며, 전달받은 backend SHA가 자신의 대상 SHA와 같은지 확인한 뒤 결과를 확정한다.

contract migration도 검증을 통과하면 일반 backend 변경과 동일하게 `main` push에서 배포하며 별도 `workflow_dispatch` 승인 입력을 요구하지 않는다. production environment 보호 규칙은 그대로 적용한다. 수동 DB snapshot이나 AWS 증거 검증은 배포 조건으로 사용하지 않는다. 적용이 끝난 migration filename만 `docs/ops/applied-contract-migrations.txt`에 별도 변경으로 기록한다.

## 활성화와 정리

Contract migration 여부는 release metadata와 envelope에 서명된다. 새 contract release의 서비스 시작을 시도한 뒤 실패하면 이전 JAR의 schema 호환성을 증명할 수 없으므로 자동 rollback하지 않는다. 서비스는 중지되며 운영자가 DB 복구 또는 contract-compatible artifact를 명시적으로 선택해야 한다.

배포 workflow는 GitHub runner에서 checksum, commit metadata와 attestation을 검증하고 EC2의 실행별 `/tmp` staging 경로로 전송한다. EC2에서도 checksum을 다시 확인한 뒤 기존 서비스 경로에 반영하며, 임시 staging 경로는 성공 여부와 관계없이 정리한다.

backend/frontend artifact는 payload·metadata·SBOM·SHA-256 manifest의 digest를 담은 `RELEASE_ENVELOPE`, envelope provenance attestation, 실제 payload를 대상으로 한 SBOM attestation을 포함한다. frontend SBOM은 source tree가 아니라 실제 배포 `dist`를 대상으로 생성한다. attestation bundle 다운로드는 bounded exponential backoff로 재시도하며 소진되면 release 생성을 실패시킨다. 배포 직전 최신 `origin/main`을 fetch하고 대상 이후의 변경 경로를 영역별로 비교한다. backend와 frontend에 무관한 문서 변경은 이미 검증된 배포를 막지 않지만 해당 영역 또는 공통 운영 경로가 바뀐 stale artifact는 차단한다. production deploy concurrency는 활성 실행을 취소하지 않고 최신 대기 실행 하나를 보존해 직렬화한다.

backend는 기존 JAR을 `app.jar.rollback`으로 보존하고 서비스 stop, JAR 교체, 8081 management health 검증을 수행한다. 일반 변경의 시작 실패는 이전 JAR로 자동 복원하고, contract migration은 이전 schema 호환성을 보장할 수 없으므로 자동 rollback하지 않는다. frontend는 현재 `/var/www/app`을 실행별 rollback 경로로 옮긴 뒤 새 파일을 활성화한다. 내부 및 공개 검증이 끝나면 backup을 제거하고, 검증 실패 시 이전 디렉터리를 복원한다. 별도 상시 설치 helper와 root-owned 배포 상태 파일은 요구하지 않는다.

## SEO

production frontend release는 `SEO_STRICT=true`로 sitemap과 prerender를 생성한다. API 조회 실패, 게시글 URL 0건, URL과 prerender 개수 불일치는 release 생성을 실패시킨다. sitemap과 prerender는 공통 `SEO_POST_URL_CAPACITY` 계약을 사용하며 기본 2,000개의 최신 게시글 URL만 포함한다. 전체 sitemap은 프로토콜 상한 50,000 URL을 넘지 못한다. `.noviis-seo-release.json`에 commit SHA, 전체 URL 수, 게시글 URL 수, prerender 수, 용량 상한과 sitemap SHA-256을 기록한다. 배포 후 검증과 정기 monitor는 `/.noviis-release`의 현재 활성 SHA를 manifest와 항상 결합한다. 배포 직후는 SHA 기반 결정적 표본을 사용하고, 정기 monitor는 SHA와 workflow run identity를 결합한 순환 표본으로 sitemap 앞부분만 반복 검사하는 편향을 피한다.

production 배포와 정기 monitor는 공개 SEO endpoint 검증만 수행한다. 검색 엔진 제출 API와 제출 자격 증명은 운영하지 않는다.

## Ops 검증

Grafana dashboard는 JSON parse뿐 아니라 panel/refId 중복, backend query scope와 모든 PromQL을 검사한다. systemd unit과 monitoring drop-in은 `hardening-contract.json`의 exact directive 및 writable-path 계약을 통과해야 한다.

`ops-config-test`는 actionlint에 더해 YAML AST 기반 권한·concurrency·artifact identity 계약, 부분 재실행의 producer artifact ID 전달, 누적 backend 배포 기준과 migration 기준의 일치, Deployment Gate 원인 진단, Prometheus config/rules/fixtures, metric manifest, Grafana JSON, shell, systemd, migration policy, activation fixture를 검증한다. 기존 테이블의 신규 인덱스는 bounded `lock_timeout`, `CREATE INDEX CONCURRENTLY`, Flyway 비트랜잭션 sidecar를 모두 갖춰야 한다. Prometheus·Grafana의 승인 버전과 host exporter의 최소 호환 버전은 `deploy/monitoring/tool-versions.env`에 기록한다. 운영 host는 Prometheus·Grafana의 동일 native 버전과 최소 버전 이상의 배포판 host exporter를 사용한다.

non-Agent `@Scheduled` 메서드는 `scheduled-jobs.txt`와 freshness rule이 일치해야 한다. sudoers는 `visudo -cf`와 허용·거부 command matrix를 모두 통과해야 한다. systemd 메모리 상한은 운영 측정 기록과 staging 검증이 없으면 추가하지 않는다.

Grafana 관리 비밀번호는 `/etc/noviis/monitoring.env`와 root-only 회전 helper로만 관리한다. helper는 loopback User API를 사용하고 비밀번호를 argv나 shell history에 넣지 않는다. Prometheus rule은 로컬에서 평가되지만 외부 Alertmanager receiver는 아직 없으므로 firing 자체가 Slack 또는 이메일 전달을 의미하지 않는다.

## 권한과 유지보수

Backend/frontend production deploy는 `queue: single`, `cancel-in-progress: false`로 현재 활성 실행을 취소하지 않으면서 최신 pending 하나만 보존한다. 정기 SEO 검증은 실행 이력을 빠뜨리지 않도록 별도 `queue: max` group을 사용한다.

workflow 기본 권한은 `contents: read`이며 attestation과 artifact metadata 권한은 필요한 release job에만 부여한다. `changes` job만 마지막 성공 배포 실행을 조회하기 위해 `actions: read`를 추가로 사용한다. PR에서 repository script를 실행하는 PostgreSQL·ops job에는 `actions: read`, `deployments: read`, `id-token: write`를 부여하지 않는다. third-party Action은 검토한 release의 full commit SHA로 고정한다. workflow, activation script, sudoers, migration 정책 변경은 CODEOWNERS review 대상이다.

배포 freshness 경계의 source of truth는 `deploy/release-freshness-paths.txt`다. workflow가 참조하는 activation·verification·provenance 파일이 이 manifest에서 빠지면 ops CI가 실패한다. Contract 배포는 수동 실행의 명시적 승인 없이는 시작되지 않으며 production checkout은 Git credential을 보존하지 않는다.

Pinned actionlint 1.7.7은 GitHub의 2026 `concurrency.queue`와 `artifact-metadata` permission schema를 아직 알지 못하므로 CI는 그 두 exact parser diagnostics만 무시한다. 별도 YAML AST 계약이 production deploy의 `queue: single`, SEO 검증의 `queue: max`, 최소 permission, main-only deploy, secret allowlist를 검증한다. actionlint가 두 필드를 지원하는 버전으로 갱신되면 ignore도 같은 변경에서 제거한다.

주요 timeout은 change detection·gate·SEO preflight 5분, backend test 45분, frontend test 60분, PostgreSQL·ops 30분, backend/frontend candidate 각각 20분·25분, backend/frontend release 각각 10분, deploy 30분, SEO 검증 15분이다. YAML에서 값을 바꾸면 이 문서도 같은 변경에서 갱신한다.

## Production environment와 Secrets

GitHub `production` environment는 `main` branch restriction, required reviewer, self-review 금지를 별도로 설정한다. repository 파일만으로 environment 보호 규칙이 생성되는 것은 아니다. 실제 production 배포와 의도적인 rollback 시험은 운영 권한 단계에서 수행한다.

배포 연결에 필요한 secret:

- `EC2_HOST`
- `EC2_SSH_KEY`
- `EC2_USER`

secret 값은 workflow 로그, fixture, 문서, release metadata에 기록하지 않는다. reusable workflow에는 필요한 secret만 명시적으로 매핑하며 `secrets: inherit`를 사용하지 않는다.
