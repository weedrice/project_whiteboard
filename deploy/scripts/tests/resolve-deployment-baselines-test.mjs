import assert from 'node:assert/strict'
import test from 'node:test'
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

test('resolves the most recent successful deployment independently for each component', async () => {
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
})

test('returns missing baselines when no completed runs exist', async () => {
  const empty = await resolveDeploymentBaselines({
    repository: 'weedrice/project_whiteboard',
    token: 'fixture-token',
    fetchImpl: async () => response({ workflow_runs: [] }),
  })
  assert.deepEqual(empty, { backend: null, frontend: null })
})

test('fails closed when the GitHub API rejects the request', async () => {
  await assert.rejects(
    resolveDeploymentBaselines({
      repository: 'weedrice/project_whiteboard',
      token: 'fixture-token',
      fetchImpl: async () => response({ message: 'rate limited' }, { ok: false, status: 403 }),
    }),
    /GitHub API request failed \(403\)/,
  )
})

test('follows all job pages before falling back to an older deployment run', async () => {
  const requests = []
  const nextJobs = 'https://api.github.com/repos/fixture/project/actions/runs/30/jobs?filter=all&per_page=100&page=2'
  const resolved = await resolveDeploymentBaselines({
    repository: 'fixture/project',
    token: 'fixture-token',
    fetchImpl: async (url) => {
      requests.push(url)
      if (url.includes('/workflows/')) return response({ workflow_runs: [
        { id: 30, head_sha: backendSha }, { id: 10, head_sha: unrelatedSha },
      ] })
      if (url === nextJobs) return response({ jobs: [
        { name: 'deploy-backend / deploy', conclusion: 'success' },
        { name: 'deploy-frontend / deploy', conclusion: 'success' },
      ] })
      if (url.includes('/runs/30/jobs?')) return response({
        jobs: Array.from({ length: 100 }, () => ({ name: 'validation', conclusion: 'success' })),
      }, { link: `<${nextJobs}>; rel="next"` })
      if (url.includes('/runs/10/jobs?')) return response({ jobs: [
        { name: 'deploy-backend / deploy', conclusion: 'success' },
        { name: 'deploy-frontend / deploy', conclusion: 'success' },
      ] })
      throw new Error(`Unexpected request: ${url}`)
    },
  })
  assert.deepEqual(resolved, { backend: backendSha, frontend: backendSha })
  assert.deepEqual(requests.slice(1), [nextJobs.replace('&page=2', ''), nextJobs])
})

test('preserves a newer component baseline while traversing older run and job pages', async () => {
  const nextRuns = 'https://api.github.com/repos/fixture/project/actions/workflows/ci.yml/runs?page=2'
  const nextJobs = 'https://api.github.com/repos/fixture/project/actions/runs/10/jobs?page=2'
  const resolved = await resolveDeploymentBaselines({
    repository: 'fixture/project', token: 'fixture-token',
    fetchImpl: async (url) => {
      if (url === nextRuns) return response({ workflow_runs: [{ id: 10, head_sha: backendSha }] })
      if (url.includes('/workflows/')) return response({
        workflow_runs: [{ id: 30, head_sha: frontendSha }],
      }, { link: `<${nextRuns}>; rel="next"` })
      if (url.includes('/runs/30/jobs')) return response({ jobs: [
        { name: 'deploy-frontend / deploy', conclusion: 'success' },
      ] })
      if (url === nextJobs) return response({ jobs: [
        { name: 'deploy-backend / deploy', conclusion: 'success' },
        { name: 'deploy-frontend / deploy', conclusion: 'success' },
      ] })
      if (url.includes('/runs/10/jobs')) return response({ jobs: [
        { name: 'deploy-backend / deploy', conclusion: 'failure' },
      ] }, { link: `<${nextJobs}>; rel="next"` })
      throw new Error(`Unexpected request: ${url}`)
    },
  })
  assert.deepEqual(resolved, { backend: backendSha, frontend: frontendSha })
})

test('fails closed when a later job page cannot be read', async () => {
  await assert.rejects(resolveDeploymentBaselines({
    repository: 'fixture/project', token: 'fixture-token',
    fetchImpl: async (url) => {
      if (url.includes('/workflows/')) return response({ workflow_runs: [{ id: 30, head_sha: backendSha }] })
      if (url.includes('page=2')) return response({ message: 'unavailable' }, { ok: false, status: 503 })
      return response({ jobs: [] }, { link: '<https://api.github.com/fixture/jobs?page=2>; rel="next"' })
    },
  }), /GitHub API request failed \(503\)/)
})
