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

const ci = load('.github/workflows/ci.yml')
const backend = load('.github/workflows/deploy-backend.yml')
const frontend = load('.github/workflows/deploy-frontend.yml')
const seo = load('.github/workflows/seo-monitor.yml')

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

const preflight = seo.jobs['seo-preflight']
assert(stepRuns(preflight, 'refs/heads/main'), 'manual SEO runs must be restricted to main')
assert(seo.jobs['verify-endpoints'].needs === 'seo-preflight', 'SEO endpoint verification must depend on preflight')

console.log('Workflow AST security contracts passed')
