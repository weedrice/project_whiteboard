import assert from 'node:assert/strict'
import { resolveDeploymentBaselines } from '../resolve-deployment-baselines.mjs'

const backendSha = '1'.repeat(40)
const frontendSha = '2'.repeat(40)
const unrelatedSha = '3'.repeat(40)

function response(data, { link = null, ok = true, status = 200 } = {}) {
  return {
    ok,
    status,
    headers: new Headers(link ? { link } : {}),
    json: async () => data,
    text: async () => JSON.stringify(data),
  }
}

const requests = []
const fetchImpl = async (url, options) => {
  requests.push({ url, options })
  if (url.includes('/actions/workflows/ci.yml/runs?')) {
    return response({
      workflow_runs: [
        { id: 30, head_sha: frontendSha },
        { id: 20, head_sha: unrelatedSha },
        { id: 10, head_sha: backendSha },
      ],
    })
  }
  if (url.includes('/actions/runs/30/jobs?')) {
    return response({ jobs: [{ name: 'deploy-frontend / deploy', conclusion: 'success' }] })
  }
  if (url.includes('/actions/runs/20/jobs?')) {
    return response({ jobs: [{ name: 'deploy-backend', conclusion: 'skipped' }] })
  }
  if (url.includes('/actions/runs/10/jobs?')) {
    return response({ jobs: [{ name: 'deploy-backend / deploy', conclusion: 'success' }] })
  }
  throw new Error(`Unexpected request: ${url}`)
}

const resolved = await resolveDeploymentBaselines({
  repository: 'weedrice/project_whiteboard',
  token: 'fixture-token',
  fetchImpl,
})

assert.deepEqual(resolved, { backend: backendSha, frontend: frontendSha })
assert.equal(requests.length, 4)
assert(requests.every(({ options }) => options.headers.Authorization === 'Bearer fixture-token'))
assert(requests[0].url.includes('status=completed'))
assert(requests.slice(1).every(({ url }) => url.includes('filter=all')))

const empty = await resolveDeploymentBaselines({
  repository: 'weedrice/project_whiteboard',
  token: 'fixture-token',
  fetchImpl: async () => response({ workflow_runs: [] }),
})
assert.deepEqual(empty, { backend: null, frontend: null })

await assert.rejects(
  resolveDeploymentBaselines({
    repository: 'weedrice/project_whiteboard',
    token: 'fixture-token',
    fetchImpl: async () => response({ message: 'rate limited' }, { ok: false, status: 403 }),
  }),
  /GitHub API request failed \(403\)/,
)

console.log('Deployment baseline resolver tests passed')
