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

function assertExactKeys(actual, expected, message) {
  const keys = Object.keys(actual ?? {}).sort()
  assert(JSON.stringify(keys) === JSON.stringify([...expected].sort()), `${message}: ${keys.join(', ')}`)
}

const ci = load('.github/workflows/ci.yml')
const backend = load('.github/workflows/deploy-backend.yml')
const frontend = load('.github/workflows/deploy-frontend.yml')
const seo = load('.github/workflows/seo-monitor.yml')
const freshness = freshnessEntries()

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

for (const [name, workflow, group] of [
  ['backend deploy', backend, 'deploy-production'],
  ['frontend deploy', frontend, 'deploy-production'],
  ['SEO submit', seo, 'seo-submit-production'],
]) {
  assert(workflow.concurrency?.group === group, `${name} concurrency group changed`)
  assert(workflow.concurrency?.['cancel-in-progress'] === false, `${name} may not cancel an active production operation`)
  assert(workflow.concurrency?.queue === 'max', `${name} must retain the latest queued operation`)
}

for (const name of ['backend-postgres-migration', 'ops-config-test']) {
  const granted = permissions(ci.jobs[name])
  assert(granted.contents === 'read', `${name} requires read-only contents`)
  for (const forbidden of ['actions', 'deployments', 'id-token', 'attestations']) {
    assert(!(forbidden in granted), `${name} received forbidden ${forbidden} permission`)
  }
}

for (const name of ['candidate-backend', 'candidate-frontend']) {
  const granted = permissions(ci.jobs[name])
  assert(granted.contents === 'read', `${name} requires read-only contents`)
  assert(!('id-token' in granted) && !('attestations' in granted), `${name} may not sign artifacts`)
}

for (const name of ['release-backend', 'release-frontend']) {
  const granted = permissions(ci.jobs[name])
  assert(granted['id-token'] === 'write' && granted.attestations === 'write', `${name} signing permissions changed`)
}

const trusted = ci.jobs['trusted-contract-evidence']
assert(String(trusted.if).includes("github.ref == 'refs/heads/main'"), 'contract evidence must be restricted to main')
assert(permissions(trusted).actions === 'read' && permissions(trusted).deployments === 'read', 'contract evidence read permissions changed')
assert((trusted.steps ?? []).some((step) => step.env?.VERIFY_CONTRACT_RUNS === 'true'), 'contract evidence must verify deployment runs')

assert(permissions(backend.jobs['contract-evidence'])['id-token'] === 'write', 'contract evidence job requires OIDC')
assert(!('id-token' in permissions(backend.jobs.deploy)), 'backend activation may not receive OIDC')
assert(!('id-token' in permissions(frontend.jobs.deploy)), 'frontend activation may not receive OIDC')
assert(stepRuns(backend.jobs.deploy, 'verify-deployment-freshness.sh') && stepRuns(backend.jobs.deploy, ' backend'), 'backend deployment must use the path-aware freshness verifier')
assert(stepRuns(frontend.jobs.deploy, 'verify-deployment-freshness.sh') && stepRuns(frontend.jobs.deploy, ' frontend'), 'frontend deployment must use the path-aware freshness verifier')

assert(ci.jobs['deploy-backend'].with.release_artifact_name.includes('${{ github.run_id }}-${{ github.run_attempt }}-${{ github.sha }}'), 'backend artifact identity is not attempt-specific')
assert(ci.jobs['deploy-frontend'].with.release_artifact_name.includes('${{ github.run_id }}-${{ github.run_attempt }}-${{ github.sha }}'), 'frontend artifact identity is not attempt-specific')
assert(!containsSecretInheritance(ci) && !containsSecretInheritance(backend) && !containsSecretInheritance(frontend), 'reusable workflows may not inherit every secret')

assertCheckoutDoesNotPersistCredentials(backend, 'backend deploy')
assertCheckoutDoesNotPersistCredentials(frontend, 'frontend deploy')
assertCheckoutDoesNotPersistCredentials(seo, 'SEO monitor')
assertExactKeys(backend.on?.workflow_call?.secrets, [
  'EC2_HOST', 'EC2_SSH_KEY', 'EC2_HOST_FINGERPRINT', 'AWS_CONTRACT_EVIDENCE_ROLE_ARN',
  'AWS_REGION', 'RDS_PRODUCTION_DB_IDENTIFIER', 'AWS_EXPECTED_ACCOUNT_ID',
  'RDS_SNAPSHOT_KMS_KEY_ARN', 'RDS_ENGINE_MAJOR_VERSION',
], 'backend reusable workflow secret allowlist changed')
assertExactKeys(frontend.on?.workflow_call?.secrets, [
  'EC2_HOST', 'EC2_SSH_KEY', 'EC2_HOST_FINGERPRINT', 'GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN',
  'GOOGLE_SEARCH_CONSOLE_CLIENT_ID', 'GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET',
  'GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN', 'CUSTOM_SITEMAP_SUBMIT_URL', 'CUSTOM_SITEMAP_SUBMIT_ALLOWED_ORIGINS',
], 'frontend reusable workflow secret allowlist changed')

for (const [name, releaseJob] of [['backend', ci.jobs['deploy-backend']], ['frontend', ci.jobs['deploy-frontend']]]) {
  assert((releaseJob.needs ?? []).includes('ci-gate'), `${name} deployment bypasses ci-gate`)
  assert((releaseJob.needs ?? []).some((need) => need === `release-${name}`), `${name} deployment bypasses verified release artifacts`)
  assert(String(releaseJob.if).includes("github.ref == 'refs/heads/main'"), `${name} production deployment is not main-only`)
}

const preflight = seo.jobs['seo-preflight']
assert(stepRuns(preflight, 'refs/heads/main'), 'manual SEO runs must be restricted to main')
assert(seo.jobs['verify-endpoints'].needs === 'seo-preflight', 'SEO endpoint verification must depend on preflight')

console.log('Workflow AST security contracts passed')
