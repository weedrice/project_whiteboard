import fs from 'node:fs'
import { pathToFileURL } from 'node:url'

const deploymentJobs = {
  backend: /^deploy-backend(?: \/ deploy)?$/,
  frontend: /^deploy-frontend(?: \/ deploy)?$/,
}

function nextLink(value) {
  if (!value) return null
  for (const entry of value.split(',')) {
    const match = entry.match(/<([^>]+)>;\s*rel="([^"]+)"/)
    if (match?.[2] === 'next') return match[1]
  }
  return null
}

async function githubJson(url, { token, fetchImpl }) {
  const response = await fetchImpl(url, {
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'User-Agent': 'noviis-deployment-baseline-resolver',
      'X-GitHub-Api-Version': '2022-11-28',
    },
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`GitHub API request failed (${response.status}): ${body.slice(0, 500)}`)
  }
  return {
    data: await response.json(),
    next: nextLink(response.headers.get('link')),
  }
}

export async function resolveDeploymentBaselines({
  repository,
  workflow = 'ci.yml',
  branch = 'main',
  token,
  apiUrl = 'https://api.github.com',
  fetchImpl = globalThis.fetch,
}) {
  if (!repository) throw new Error('repository is required')
  if (!token) throw new Error('token is required')
  if (typeof fetchImpl !== 'function') throw new Error('fetch implementation is required')

  const resolved = { backend: null, frontend: null }
  const workflowId = encodeURIComponent(workflow)
  let runsUrl = `${apiUrl}/repos/${repository}/actions/workflows/${workflowId}/runs` +
    `?branch=${encodeURIComponent(branch)}&status=completed&per_page=100`

  while (runsUrl && (!resolved.backend || !resolved.frontend)) {
    const runsResponse = await githubJson(runsUrl, { token, fetchImpl })
    for (const run of runsResponse.data.workflow_runs ?? []) {
      if (!run?.id || !/^[0-9a-f]{40}$/i.test(run.head_sha ?? '')) continue

      const jobsUrl = `${apiUrl}/repos/${repository}/actions/runs/${run.id}/jobs?filter=all&per_page=100`
      const jobsResponse = await githubJson(jobsUrl, { token, fetchImpl })
      for (const job of jobsResponse.data.jobs ?? []) {
        if (job.conclusion !== 'success') continue
        for (const [component, pattern] of Object.entries(deploymentJobs)) {
          if (!resolved[component] && pattern.test(job.name ?? '')) {
            resolved[component] = run.head_sha
          }
        }
      }
      if (resolved.backend && resolved.frontend) break
    }
    runsUrl = runsResponse.next
  }

  return resolved
}

function appendOutput(outputFile, name, value) {
  fs.appendFileSync(outputFile, `${name}=${value}\n`, 'utf8')
}

async function main() {
  const outputFile = process.env.GITHUB_OUTPUT
  if (!outputFile) throw new Error('GITHUB_OUTPUT is required')

  const resolved = await resolveDeploymentBaselines({
    repository: process.env.GITHUB_REPOSITORY,
    workflow: process.env.DEPLOYMENT_WORKFLOW ?? 'ci.yml',
    branch: process.env.DEPLOYMENT_BRANCH ?? 'main',
    token: process.env.GH_TOKEN,
    apiUrl: process.env.GITHUB_API_URL ?? 'https://api.github.com',
  })

  for (const component of Object.keys(deploymentJobs)) {
    const sha = resolved[component]
    appendOutput(outputFile, `${component}_found`, String(Boolean(sha)))
    appendOutput(outputFile, `${component}_sha`, sha ?? '')
    console.log(`${component} deployment baseline: ${sha ?? 'not found; force deployment'}`)
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main()
}
