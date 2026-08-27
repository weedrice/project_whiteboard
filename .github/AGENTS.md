# GitHub Automation AGENTS.md

## Scope And Source Of Truth

This directory controls CI, release construction, attestations, production deployment, and scheduled SEO monitoring. Treat workflow edits as production-sensitive.

- Read `.github/workflows/README.md` before editing a workflow.
- Treat the reusable deployment workflows as the current production implementation. Do not infer that the separate hardened activator profile under `deploy/` is installed or invoked unless a coordinated adoption change makes that explicit.
- Keep that README synchronized with trigger, scope, permission, timeout, artifact, deployment, rollback, and secret-contract changes.
- Editing workflow files does not authorize dispatching a workflow, rerunning production jobs, approving an environment, or deploying.

## Permissions And Supply Chain

- Keep the workflow-level default at `contents: read` and grant additional permissions only to the job that requires them.
- Do not grant privileged permissions to jobs that execute pull-request-controlled repository code.
- Restrict `id-token: write`, attestations, artifact metadata, deployments, and actions history access to the existing narrowly scoped jobs that need them.
- Pin every third-party Action to a reviewed full commit SHA and retain a human-readable release comment.
- Do not use floating tags, branches, `secrets: inherit`, persisted production Git credentials, or unreviewed downloaded executables.
- Pin downloaded tools and container images by the repository's established version and digest contracts, and verify checksums before execution where the workflow already requires them.
- Never echo secrets or place secret values in command arguments, artifacts, caches, release metadata, fixtures, or debug output.

## Expression And Shell Safety

- Pass untrusted GitHub context values through an `env` entry and quote them in the shell. Do not interpolate pull-request titles, branch names, issue text, or user-controlled inputs directly into `run` scripts.
- Keep production deployment restricted to `main` and the protected `production` environment.
- Preserve explicit secret mapping for reusable workflows.
- Keep timeouts and concurrency explicit. Production deployment must serialize without cancelling the active run.

## CI Scope And Gates

- Validation scope and deployment scope are different. Do not make a documentation- or test-only change create a release candidate accidentally.
- Preserve cumulative deployment scope from the component's last successful production deployment, so a failed runtime change is not forgotten by a later successful commit.
- When changing paths consumed by backend, frontend, migrations, ops, or deployment, update path filters, job needs, gate expectations, and `deploy/release-freshness-paths.txt` together as applicable.
- Selected validation jobs must succeed; a required job that is silently skipped must fail the gate.
- Keep backend migration validation, PostgreSQL upgrade compatibility, and deployment contract outputs connected.

## Artifact And Deployment Contracts

- Candidate jobs build without release privileges. Only the release jobs may add OIDC-backed attestations and release metadata.
- Pass the immutable artifact ID returned by the producer rather than recomputing an artifact name.
- Preserve payload, metadata, SBOM, manifest, envelope, and run identity across consumer boundaries. In the current inline production workflows, attestations are verified on the GitHub runner before upload; EC2 rechecks checksum and metadata integrity plus an independent readback, but does not perform host-side attestation verification.
- Host-side trusted-root attestation verification belongs to the separate hardened profile. Do not describe it as current production behavior until the workflows and hosts adopt that profile together.
- Deploy backend before frontend when both are selected, and verify the activated backend SHA before finalizing the frontend deployment.
- Contract migrations may deploy after integrated CI and production environment protection. If startup fails after a contract migration, do not automatically roll back to the previous JAR.

## Validation

Run the repository workflow-security fixture for workflow, permissions, concurrency, artifact, freshness, or deployment-logic changes:

```bash
bash deploy/scripts/tests/verify-workflow-security-test.sh
```

When Docker is available, run the pinned actionlint command from the `ops-config-test` job in `.github/workflows/ci.yml`. The two documented parser ignores are temporary compatibility exceptions; do not broaden them, and remove them when the pinned actionlint version supports those fields.

Review the relevant change-detection outputs, `needs` graph, job `if` expressions, permission blocks, timeout values, and gate result handling manually. YAML parsing alone does not prove deployment safety.

## Dependency Automation

- Preserve the reviewed ecosystem directories, schedules, grouping, runtime baselines, and documented ignore exceptions in `dependabot.yml` unless the same change provides evidence for altering them.
- When changing a Gradle, npm, Docker, or Actions update policy, verify the affected module lockfile or pinned reference and run the narrowest relevant module or workflow check.
- Editing Dependabot configuration does not authorize merging or enabling a generated dependency update.
