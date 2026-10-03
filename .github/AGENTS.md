# GitHub Automation AGENTS.md

## Scope And Sources

This directory owns CI, release construction, attestations, production deployment, dependency updates, and scheduled SEO monitoring.

- Read [workflows/README.md](workflows/README.md) before editing workflows; keep trigger, scope, permission, timeout, artifact, deployment, rollback, and secret contracts synchronized.
- For deployment changes, follow [deploy/AGENTS.md](../deploy/AGENTS.md) for activation models, host restrictions, and rollback boundaries. The reusable workflows are current production; the hardened profile is separately tested.
- File edits do not authorize workflow dispatch, production-job reruns, environment approval, deployment, or merging/enabling dependency updates.

## Permissions And Shell Safety

- Keep workflow defaults at `contents: read`; grant privileged permissions only to the narrowly scoped jobs that need them, never to jobs executing pull-request-controlled code.
- Preserve narrow access to OIDC, attestations, artifact metadata, deployments, and Actions history.
- Pin third-party Actions to reviewed full commit SHAs with readable release comments. Do not use floating references, `secrets: inherit`, persisted production Git credentials, or unreviewed downloaded executables.
- Pin downloaded tools/container images using established version/digest contracts, retaining checksum verification before execution where required.
- Pass untrusted GitHub context through `env` and quote it in the shell; never interpolate PR titles, branches, issue text, or user-controlled inputs directly into `run`.
- Preserve explicit reusable-workflow secret mappings. Never expose secret values in arguments, artifacts, caches, metadata, fixtures, or debug output.

## CI And Release Contracts

- Validation scope and deployment scope differ: documentation/test-only changes must not accidentally create release candidates.
- Compute cumulative deployment scope from the component's last successful production deployment, so later successful commits retain earlier undeployed runtime changes.
- When consumed paths change, update path filters, job needs, gate expectations, and [release freshness paths](../deploy/release-freshness-paths.txt) as applicable.
- Selected validation jobs must succeed; silently skipped required jobs must fail the gate. Keep migration validation, PostgreSQL upgrade compatibility, and deployment outputs connected.
- Candidate jobs build without release privileges; release jobs alone add OIDC-backed attestations and metadata. Pass the producer's immutable artifact ID instead of recomputing its name.
- Preserve payload, metadata, SBOM, manifest, envelope, and producer run identity across consumers.
- Current inline workflows verify attestations on the GitHub runner; EC2 verifies checksum/metadata and independent readback. Host trusted-root attestation verification belongs to the hardened profile.
- Keep deployment restricted to `main` and the protected `production` environment, with explicit timeouts and serialization that never cancels an active production run.
- Deploy backend before frontend when both are selected, verifying the activated backend SHA before finalizing frontend.
- Contract migrations may deploy after integrated CI and production environment protection. Follow the activation rollback boundary in the deployment instructions; do not add an unsupported separate deployment gate.

## Verification

Run from the repository root through Bash/WSL or CI containers as appropriate:

```bash
bash deploy/scripts/tests/verify-workflow-security-test.sh
```

- Run that fixture for workflow, permission, concurrency, artifact, freshness, or deployment-logic changes.
- With Docker, run pinned actionlint from `ops-config-test` in [ci.yml](workflows/ci.yml). Preserve only the two documented parser ignores; remove them when the pinned version supports those fields.
- Manually review change-detection outputs, `needs`, job `if` expressions, permissions, timeouts, and gate result handling. YAML parsing alone does not prove deployment safety.

## Dependency Automation

Preserve [dependabot.yml](dependabot.yml) ecosystem directories, schedules, grouping, runtime baselines, and documented ignore exceptions unless evidence supports their change. Verify affected lockfiles/pinned references and run the relevant module or workflow check when changing Gradle, npm, Docker, or Actions update policy.
