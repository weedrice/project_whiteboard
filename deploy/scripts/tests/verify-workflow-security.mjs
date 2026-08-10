import fs from 'node:fs'
import path from 'node:path'
import { parseDocument } from '../../../frontend/node_modules/yaml/dist/index.js'

const root = path.resolve(process.argv[2] ?? path.join(import.meta.dirname, '../../..'))

function load(relativePath) {
  const document = parseDocument(fs.readFileSync(path.join(root, relativePath), 'utf8'))
  if (document.errors.length > 0) {
    throw new Error(`${relativePath}: ${document.errors.map((error) => error.message).join('; ')}`)
  }
  return document.toJS()
}

function loadText(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8')
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function permissions(job) {
  return job?.permissions ?? {}
}

function stepRuns(job, fragment) {
  return (job?.steps ?? []).some((step) => typeof step.run === 'string' && step.run.includes(fragment))
}

function containsSecretInheritance(value) {
  if (value === 'inherit') return true
  if (Array.isArray(value)) return value.some(containsSecretInheritance)
  return value && typeof value === 'object'
    ? Object.entries(value).some(([key, child]) => key === 'secrets' && child === 'inherit' || containsSecretInheritance(child))
    : false
}

function freshnessEntries() {
  return loadText('deploy/release-freshness-paths.txt')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const fields = line.split(/\s+/)
      assert(fields.length === 2, `invalid freshness manifest entry: ${line}`)
      assert(['common', 'backend', 'frontend'].includes(fields[0]), `invalid freshness scope: ${fields[0]}`)
      return { scope: fields[0], pattern: fields[1] }
    })
}

function globMatches(pattern, value) {
  const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replaceAll('**', '\u0000')
    .replaceAll('*', '[^/]*')
    .replaceAll('\u0000', '.*')
  return new RegExp(`^${escaped}$`).test(value)
}

function isFreshnessBound(entries, component, file) {
  return entries.some(({ scope, pattern }) => (scope === 'common' || scope === component) && globMatches(pattern, file))
}

function referencedReleaseFiles(workflow) {
  const text = JSON.stringify(workflow)
  return [...text.matchAll(/(?:hashFiles\('|(?:bash|sudo)\s+)(deploy\/(?:scripts|systemd|sudoers)\/[^'"\s})]+)/g)]
    .map((match) => match[1])
    .filter((value) => !value.includes('/tests/'))
}

function assertCheckoutDoesNotPersistCredentials(workflow, workflowName) {
  for (const [jobName, job] of Object.entries(workflow.jobs ?? {})) {
    if (job.environment !== 'production') continue
    for (const step of job.steps ?? []) {
      if (typeof step.uses === 'string' && step.uses.startsWith('actions/checkout@')) {
        assert(step.with?.['persist-credentials'] === false, `${workflowName}/${jobName} checkout persists credentials`)
      }
    }
  }
}

function assertTrustedRepositoryScriptCheckout(job, jobName, scriptPath) {
  const steps = job?.steps ?? []
  const scriptStepIndex = steps.findIndex((step) =>
    typeof step.run === 'string' && step.run.includes(scriptPath),
  )
  assert(scriptStepIndex >= 0, `${jobName} no longer executes ${scriptPath}`)

  const checkout = steps.slice(0, scriptStepIndex).findLast((step) =>
    typeof step.uses === 'string' && step.uses.startsWith('actions/checkout@'),
  )
  assert(checkout, `${jobName} executes ${scriptPath} before checking out the repository`)
  assert(checkout.with?.ref === '${{ github.sha }}', `${jobName} repository script checkout is not pinned to github.sha`)
  assert(checkout.with?.['persist-credentials'] === false, `${jobName} repository script checkout persists credentials`)

  const sparseCheckout = String(checkout.with?.['sparse-checkout'] ?? '')
    .split(/\r?\n/)
    .map((entry) => entry.trim())
    .filter(Boolean)
  assert(sparseCheckout.includes(scriptPath), `${jobName} sparse checkout does not include ${scriptPath}`)
  assert(checkout.with?.['sparse-checkout-cone-mode'] === false, `${jobName} repository script checkout must use non-cone sparse mode`)
}

function assertExactKeys(actual, expected, message) {
  const keys = Object.keys(actual ?? {}).sort()
  assert(JSON.stringify(keys) === JSON.stringify([...expected].sort()), `${message}: ${keys.join(', ')}`)
}

function pathFilterEntries(filterSource, filterName) {
  const lines = String(filterSource).split(/\r?\n/)
  const start = lines.findIndex((line) => line.trim() === `${filterName}:`)
  assert(start >= 0, `missing path filter: ${filterName}`)
  const baseIndent = lines[start].match(/^\s*/)[0].length
  const entries = []
  for (const line of lines.slice(start + 1)) {
    if (line.trim() && line.match(/^\s*/)[0].length <= baseIndent) break
    const match = line.match(/^\s+- ['"](.+)['"]$/)
    if (match) entries.push(match[1])
  }
  return entries
}

function matchesEveryPathRule(entries, file) {
  return entries.every((entry) => entry.startsWith('!')
    ? !globMatches(entry.slice(1), file)
    : globMatches(entry, file))
}

const ci = load('.github/workflows/ci.yml')
const backend = load('.github/workflows/deploy-backend.yml')
const frontend = load('.github/workflows/deploy-frontend.yml')
const seo = load('.github/workflows/seo-monitor.yml')
const freshness = freshnessEntries()
const ciSource = loadText('.github/workflows/ci.yml')
const seoSource = loadText('.github/workflows/seo-monitor.yml')

const contractRevisionFilterEntries = ciSource.match(
  /^\s+- 'docs\/ops\/api-contract-revision\.txt'\s*$/gm,
) ?? []
assert(contractRevisionFilterEntries.length === 5,
  'API contract revision must select backend, frontend, ops validation, and both cumulative deployment scopes')

const validationFilter = ci.jobs.changes.steps.find((step) => step.id === 'filter')
const deploymentBaselines = ci.jobs.changes.steps.find((step) => step.id === 'deployment-baselines')
const backendDeployFilter = ci.jobs.changes.steps.find((step) => step.id === 'backend-deploy-filter')
const frontendDeployFilter = ci.jobs.changes.steps.find((step) => step.id === 'frontend-deploy-filter')
assert(validationFilter && deploymentBaselines && backendDeployFilter && frontendDeployFilter,
  'validation, deployment baseline, and component deployment filters must remain separate')
assert(stepRuns(ci.jobs.changes, 'deploy/scripts/resolve-deployment-baselines.mjs'),
  'deployment scope no longer resolves the last successful component deployments')
assert(permissions(ci.jobs.changes).actions === 'read',
  'deployment baseline resolution requires read-only Actions history access')
assert(ci.jobs.changes.steps.find((step) => step.name === 'Checkout')?.with?.['fetch-depth'] === 0,
  'cumulative deployment comparison requires full Git history')
for (const filter of [backendDeployFilter, frontendDeployFilter]) {
  assert(filter.with?.['predicate-quantifier'] === 'every', 'deployment exclusions require every-rule matching')
  assert(filter.with?.base?.includes('deployment-baselines.outputs.'),
    'deployment filter must compare from the last successful component deployment')
  assert(filter.with?.ref === '${{ github.sha }}', 'deployment filter must compare through the current commit')
}
const backendValidationPaths = pathFilterEntries(validationFilter.with?.filters, 'backend')
const frontendValidationPaths = pathFilterEntries(validationFilter.with?.filters, 'frontend')
const backendDeployPaths = pathFilterEntries(backendDeployFilter.with?.filters, 'backend')
const frontendDeployPaths = pathFilterEntries(frontendDeployFilter.with?.filters, 'frontend')
const backendContractDeployPaths = pathFilterEntries(backendDeployFilter.with?.filters, 'contract')
const frontendContractDeployPaths = pathFilterEntries(frontendDeployFilter.with?.filters, 'contract')
assert(backendValidationPaths.includes('.github/workflows/ci.yml'), 'CI workflow changes must validate backend')
assert(frontendValidationPaths.includes('.github/workflows/ci.yml'), 'CI workflow changes must validate frontend')
assert(!backendDeployPaths.some((entry) => entry.includes('.github/workflows/')), 'workflow-only changes must not deploy backend')
assert(!frontendDeployPaths.some((entry) => entry.includes('.github/workflows/')), 'workflow-only changes must not deploy frontend')
assert(backendDeployPaths.includes('backend/**') && backendDeployPaths.includes('!backend/src/test/**'), 'backend deployment scope must exclude tests')
assert(frontendDeployPaths.includes('frontend/**') && frontendDeployPaths.includes('!frontend/**/__tests__/**')
  && frontendDeployPaths.includes('!frontend/**/*.spec.*'), 'frontend deployment scope must exclude tests')
assert(matchesEveryPathRule(backendDeployPaths, 'backend/src/main/java/com/example/App.java'),
  'backend runtime changes must select deployment')
assert(!matchesEveryPathRule(backendDeployPaths, 'backend/src/test/java/com/example/AppTest.java'),
  'backend test-only changes must not select deployment')
assert(matchesEveryPathRule(frontendDeployPaths, 'frontend/src/views/Home.vue'),
  'frontend runtime changes must select deployment')
assert(!matchesEveryPathRule(frontendDeployPaths, 'frontend/src/views/__tests__/Home.spec.ts'),
  'frontend test-only changes must not select deployment')
assert(!matchesEveryPathRule(frontendDeployPaths, 'frontend/e2e/home.spec.ts'),
  'frontend E2E-only changes must not select deployment')
assert(JSON.stringify(backendContractDeployPaths) === JSON.stringify(['docs/ops/api-contract-revision.txt'])
  && JSON.stringify(frontendContractDeployPaths) === JSON.stringify(['docs/ops/api-contract-revision.txt']),
  'API contract revision must coordinate cumulative backend and frontend deployment')
assert(ci.jobs.changes.outputs.backend_deploy === '${{ steps.deploy-scope.outputs.backend }}', 'backend deploy output bypasses deployment scope resolver')
assert(ci.jobs.changes.outputs.frontend_deploy === '${{ steps.deploy-scope.outputs.frontend }}', 'frontend deploy output bypasses deployment scope resolver')
const deployScopeSource = JSON.stringify(ci.jobs.changes.steps.find((step) => step.id === 'deploy-scope'))
assert(deployScopeSource.includes('BACKEND_BASE_FOUND') && deployScopeSource.includes('FRONTEND_BASE_FOUND'),
  'missing deployment history must force a safe component deployment')
const validationScopeSource = JSON.stringify(ci.jobs.changes.steps.find((step) => step.id === 'scope'))
assert(validationScopeSource.includes('BACKEND_DEPLOY_PENDING')
  && validationScopeSource.includes('FRONTEND_DEPLOY_PENDING'),
  'cumulative deployment candidates must also run their component validation')
assert(!ciSource.includes('backend_changed') && !ciSource.includes('frontend_changed'), 'legacy validation/deployment coupling remains')
for (const [jobName, outputName] of [
  ['candidate-backend', 'backend_deploy'],
  ['deploy-backend', 'backend_deploy'],
  ['candidate-frontend', 'frontend_deploy'],
  ['frontend-deploy-readiness', 'frontend_deploy'],
]) {
  const jobSource = JSON.stringify(ci.jobs[jobName])
  assert(jobSource.includes(`needs.changes.outputs.${outputName}`),
    `${jobName} does not use its deployment-only change scope`)
}
for (const jobName of [
  'candidate-backend',
  'release-backend',
  'candidate-frontend',
  'release-frontend',
  'deploy-backend',
  'frontend-deploy-readiness',
  'deploy-frontend',
  'deployment-gate',
]) {
  assert(String(ci.jobs[jobName].if).includes('!cancelled()'),
    `${jobName} may inherit skipped validation jobs through the CI gate`)
}
const frontendReadiness = ci.jobs['frontend-deploy-readiness']
const frontendReadinessSource = JSON.stringify(frontendReadiness)
assert(frontendReadinessSource.includes("needs.changes.outputs.backend_deploy"),
  'frontend deployment must wait only for an actual backend deployment change')
assert(String(frontendReadiness.if).includes('always()'),
  'frontend deployment must tolerate an intentionally skipped backend deployment')
assert((frontendReadiness.needs ?? []).includes('deploy-backend')
  && (frontendReadiness.needs ?? []).includes('release-frontend')
  && (frontendReadiness.needs ?? []).includes('ci-gate'),
  'frontend readiness must preserve release, CI gate, and backend deployment ordering')
assert(ci.jobs['deploy-frontend'].needs === 'frontend-deploy-readiness'
  && String(ci.jobs['deploy-frontend'].if).includes("needs.frontend-deploy-readiness.outputs.deploy == 'true'"),
  'frontend deployment must consume the explicit readiness decision')
const deploymentGate = ci.jobs['deployment-gate']
const deploymentGateSource = JSON.stringify(deploymentGate)
assert(String(deploymentGate.if).includes('always()')
  && (deploymentGate.needs ?? []).includes('deploy-backend')
  && (deploymentGate.needs ?? []).includes('deploy-frontend'),
  'deployment gate must inspect skipped or failed component deployments')
assert(deploymentGateSource.includes('BACKEND_DEPLOY_RESULT')
  && deploymentGateSource.includes('FRONTEND_DEPLOY_RESULT')
  && deploymentGateSource.includes('exit 1'),
  'requested deployments must not finish green when deployment was skipped')
for (const protectedOpsPath of [
  '.github/CODEOWNERS',
  'docs/ops/postgres-backup-restore.md',
  'docs/ops/**',
]) {
  assert(ciSource.includes(`- '${protectedOpsPath}'`), `${protectedOpsPath} must select ops validation`)
}

for (const workflowPath of [
  '.github/workflows/ci.yml',
  '.github/workflows/deploy-backend.yml',
  '.github/workflows/deploy-frontend.yml',
  '.github/workflows/seo-monitor.yml',
]) {
  assert(!loadText(workflowPath).includes('runs-on: ubuntu-latest'),
    `${workflowPath} must pin an explicit Ubuntu runner image`)
}

for (const required of [
  'deploy/release-freshness-paths.txt',
  'deploy/scripts/verify-deployment-freshness.sh',
  'deploy/scripts/verify-release-provenance.sh',
  'deploy/sudoers/noviis-deploy',
  '.github/workflows/ci.yml',
]) {
  assert(isFreshnessBound(freshness, 'backend', required) && isFreshnessBound(freshness, 'frontend', required), `${required} is missing from the common freshness boundary`)
}
for (const [component, workflow] of [['backend', backend], ['frontend', frontend]]) {
  for (const file of referencedReleaseFiles(workflow)) {
    assert(isFreshnessBound(freshness, component, file), `${component} workflow references an unbounded release file: ${file}`)
  }
}

for (const [name, workflow, group, queue] of [
  ['backend deploy', backend, 'deploy-production', 'single'],
  ['frontend deploy', frontend, 'deploy-production', 'single'],
  ['SEO monitor', seo, 'seo-monitor-production', 'max'],
]) {
  assert(workflow.concurrency?.group === group, `${name} concurrency group changed`)
  assert(workflow.concurrency?.['cancel-in-progress'] === false, `${name} may not cancel an active production operation`)
  assert(workflow.concurrency?.queue === queue, `${name} concurrency queue policy changed`)
}

for (const name of ['backend-postgres-migration', 'backend-postgres-upgrade', 'ops-config-test']) {
  const granted = permissions(ci.jobs[name])
  assert(granted.contents === 'read', `${name} requires read-only contents`)
  for (const forbidden of ['actions', 'deployments', 'id-token', 'attestations']) {
    assert(!(forbidden in granted), `${name} received forbidden ${forbidden} permission`)
  }
}
assert(!stepRuns(ci.jobs['backend-postgres-migration'], 'previous-backend-upgrade'),
  'current-schema PostgreSQL smoke must not also run the previous-revision upgrade')
assert(stepRuns(ci.jobs['backend-postgres-upgrade'], 'previous-backend-upgrade'),
  'isolated PostgreSQL upgrade smoke is missing its previous revision worktree')
assert(ci.jobs['ci-gate'].needs.includes('backend-postgres-upgrade'),
  'CI gate must include the isolated PostgreSQL upgrade smoke job')

for (const name of ['candidate-backend', 'candidate-frontend']) {
  const granted = permissions(ci.jobs[name])
  assert(granted.contents === 'read', `${name} requires read-only contents`)
  assert(!('id-token' in granted) && !('attestations' in granted), `${name} may not sign artifacts`)
}

for (const name of ['release-backend', 'release-frontend']) {
  const granted = permissions(ci.jobs[name])
  assert(granted['id-token'] === 'write' && granted.attestations === 'write', `${name} signing permissions changed`)
  assertTrustedRepositoryScriptCheckout(ci.jobs[name], name, 'deploy/scripts/download-attestation-with-retry.sh')
}

const signingPermissionAllowlist = new Map([
  ['release-backend', new Set(['id-token', 'attestations', 'artifact-metadata'])],
  ['release-frontend', new Set(['id-token', 'attestations', 'artifact-metadata'])],
])
for (const [jobName, job] of Object.entries(ci.jobs ?? {})) {
  const allowed = signingPermissionAllowlist.get(jobName) ?? new Set()
  const granted = permissions(job)
  for (const permission of ['id-token', 'attestations', 'artifact-metadata']) {
    if (granted[permission] === 'write') {
      assert(allowed.has(permission), `${jobName} received unapproved ${permission}: write`)
    }
  }
}

assert(!('id-token' in permissions(backend.jobs.deploy)), 'backend activation may not receive OIDC')
assert(!('id-token' in permissions(frontend.jobs.deploy)), 'frontend activation may not receive OIDC')
assert(!('trusted-contract-evidence' in ci.jobs), 'remote contract evidence verification must remain removed')
assert(!('contract-evidence' in backend.jobs) && !('consume-contract-evidence' in backend.jobs),
  'backend deployment must not require organization-scale contract evidence jobs')
for (const removedContractEvidenceField of [
  'backup_snapshot_id',
  'contract_change_ticket',
  'AWS_CONTRACT_EVIDENCE_READ_ROLE_ARN',
  'AWS_CONTRACT_EVIDENCE_CONSUME_ROLE_ARN',
]) {
  assert(!ciSource.includes(removedContractEvidenceField)
    && !loadText('.github/workflows/deploy-backend.yml').includes(removedContractEvidenceField),
  `${removedContractEvidenceField} must remain outside the simplified deployment workflow`)
}
const contractDeployCondition = String(ci.jobs['deploy-backend'].if)
assert(contractDeployCondition.includes("needs.ci-gate.outputs.contract_migration != 'true'"),
  'contract migrations must remain blocked by default')
assert(contractDeployCondition.includes("github.event_name == 'workflow_dispatch'")
  && contractDeployCondition.includes('inputs.allow_contract_migration'),
'contract migrations must require an explicit manual approval')
assert(stepRuns(backend.jobs.deploy, 'verify-deployment-freshness.sh') && stepRuns(backend.jobs.deploy, ' backend'), 'backend deployment must use the path-aware freshness verifier')
assert(stepRuns(frontend.jobs.deploy, 'verify-deployment-freshness.sh') && stepRuns(frontend.jobs.deploy, ' frontend'), 'frontend deployment must use the path-aware freshness verifier')

assert(ci.jobs['deploy-backend'].with.release_artifact_name.includes('${{ github.run_id }}-${{ github.run_attempt }}-${{ github.sha }}'), 'backend artifact identity is not attempt-specific')
assert(ci.jobs['deploy-frontend'].with.release_artifact_name.includes('${{ github.run_id }}-${{ github.run_attempt }}-${{ github.sha }}'), 'frontend artifact identity is not attempt-specific')
assert(!containsSecretInheritance(ci) && !containsSecretInheritance(backend) && !containsSecretInheritance(frontend), 'reusable workflows may not inherit every secret')

assertCheckoutDoesNotPersistCredentials(backend, 'backend deploy')
assertCheckoutDoesNotPersistCredentials(frontend, 'frontend deploy')
assertCheckoutDoesNotPersistCredentials(seo, 'SEO monitor')
assertExactKeys(backend.on?.workflow_call?.secrets, [
  'EC2_HOST', 'EC2_SSH_KEY', 'EC2_USER',
], 'backend reusable workflow secret allowlist changed')
assertExactKeys(frontend.on?.workflow_call?.secrets, [
  'EC2_HOST', 'EC2_SSH_KEY', 'EC2_USER',
], 'frontend reusable workflow secret allowlist changed')
for (const [name, workflow] of [['backend', backend], ['frontend', frontend]]) {
  const source = loadText(`.github/workflows/deploy-${name}.yml`)
  assert(!source.includes('username: noviis-deploy'), `${name} deployment bypasses the configured EC2 user`)
  assert(source.includes('username: ${{ secrets.EC2_USER }}'), `${name} deployment does not use EC2_USER`)
}

const backendDeployment = ci.jobs['deploy-backend']
assert((backendDeployment.needs ?? []).includes('ci-gate'), 'backend deployment bypasses ci-gate')
assert((backendDeployment.needs ?? []).includes('release-backend'), 'backend deployment bypasses verified release artifacts')
assert(String(backendDeployment.if).includes("github.ref == 'refs/heads/main'"), 'backend production deployment is not main-only')
assert(frontendReadinessSource.includes("github.ref") && frontendReadinessSource.includes('refs/heads/main'),
  'frontend production deployment is not main-only')

const preflight = seo.jobs['seo-preflight']
assert(stepRuns(preflight, 'refs/heads/main'), 'manual SEO runs must be restricted to main')
assert(seo.jobs['verify-endpoints'].needs === 'seo-preflight', 'SEO endpoint verification must depend on preflight')
const scheduledSeoVerify = seo.jobs['verify-endpoints'].steps.find((step) => step.name === 'Verify SEO endpoints')
assert(scheduledSeoVerify?.env?.SEO_VERIFY_ROTATION_SEED === '${{ github.run_id }}-${{ github.run_attempt }}',
  'scheduled SEO verification must rotate its sample by workflow run identity')
assertExactKeys(seo.jobs, ['seo-preflight', 'verify-endpoints'], 'SEO monitor must remain verification-only')
assert(!seoSource.includes('${{ secrets.') && !seoSource.includes('submit'),
  'SEO monitor must not depend on submission credentials or jobs')

console.log('Workflow AST security contracts passed')
