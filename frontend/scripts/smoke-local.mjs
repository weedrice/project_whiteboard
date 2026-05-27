const frontendOrigin = process.env.FRONTEND_ORIGIN || 'http://localhost:5173'
const apiBase = process.env.API_BASE || `${frontendOrigin}/api/v1`
const loginId = process.env.SMOKE_LOGIN_ID || 'admin'
const password = process.env.SMOKE_PASSWORD

const spaRoutes = [
  '/',
  '/auth/login',
  '/mypage',
  '/mypage/points',
  '/mypage/scraps',
  '/mypage/messages',
  '/mypage/notifications',
  '/mypage/reports',
  '/mypage/blocked',
  '/mypage/recent',
  '/admin/dashboard',
  '/admin/users',
  '/admin/reports',
  '/admin/security',
  '/admin/inquiries',
  '/admin/settings',
  '/admin/error-logs',
]

const apiPaths = [
  '/users/me',
  '/users/me/notification-settings',
  '/points/me',
  '/points/me/history?page=0&size=5',
  '/users/me/scraps?page=0&size=5',
  '/users/me/blocks?page=0&size=5',
  '/users/me/history/views?page=0&size=5',
  '/messages/received?page=0&size=5',
  '/messages/sent?page=0&size=5',
  '/reports/me?page=0&size=5',
  '/admin/users?page=0&size=5',
  '/admin/reports?page=0&size=5',
  '/admin/ip-blocks?page=0&size=5',
  '/admin/inquiries?page=0&size=5',
  '/admin/configs',
  '/admin/error-logs?page=0&size=5',
  '/admin/stats',
  '/home/landing?period=24h',
]

function requirePassword() {
  if (password) return

  console.error('SMOKE_PASSWORD is required. Example: SMOKE_PASSWORD=password npm run smoke:local')
  process.exit(1)
}

function isOk(status) {
  return status >= 200 && status < 300
}

async function fetchStatus(url, options = {}) {
  try {
    const response = await fetch(url, options)
    return response
  } catch (error) {
    return {
      ok: false,
      status: 'ERROR',
      statusText: error instanceof Error ? error.message : String(error),
      text: async () => '',
      json: async () => ({}),
    }
  }
}

async function runSpaSmoke() {
  const results = []

  for (const route of spaRoutes) {
    const response = await fetchStatus(`${frontendOrigin}${route}`)
    const body = isOk(response.status) ? await response.text() : ''
    results.push({
      target: route,
      status: response.status,
      ok: isOk(response.status) && body.includes('id="app"'),
    })
  }

  return results
}

async function login() {
  const response = await fetchStatus(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Origin: frontendOrigin,
      Referer: `${frontendOrigin}/auth/login`,
    },
    body: JSON.stringify({ loginId, password }),
  })

  if (!isOk(response.status)) {
    return { status: response.status, token: null }
  }

  const payload = await response.json()
  return {
    status: response.status,
    token: payload?.data?.accessToken || payload?.accessToken || null,
  }
}

async function runApiSmoke(token) {
  const results = []
  const headers = {
    Authorization: `Bearer ${token}`,
    Origin: frontendOrigin,
    Referer: `${frontendOrigin}/`,
  }

  for (const path of apiPaths) {
    const response = await fetchStatus(`${apiBase}${path}`, { headers })
    results.push({
      target: path,
      status: response.status,
      ok: isOk(response.status),
    })
  }

  return results
}

function printResults(label, results) {
  console.log(`\n${label}`)
  console.table(results.map(({ target, status, ok }) => ({ target, status, ok })))
}

function assertAllPassed(results) {
  return results.every((result) => result.ok)
}

requirePassword()

const spaResults = await runSpaSmoke()
printResults('SPA routes', spaResults)

const authResult = await login()
const loginResults = [{ target: '/auth/login', status: authResult.status, ok: isOk(authResult.status) && !!authResult.token }]
printResults('Login', loginResults)

const apiResults = authResult.token ? await runApiSmoke(authResult.token) : []
printResults('API routes', apiResults)

if (!assertAllPassed(spaResults) || !assertAllPassed(loginResults) || !assertAllPassed(apiResults)) {
  console.error('\nSmoke failed.')
  process.exit(1)
}

console.log('\nSmoke passed.')
