import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import test from 'node:test'
import { parseDocument } from '../../../frontend/node_modules/yaml/dist/index.js'

const root = path.resolve(import.meta.dirname, '../../..')
const fixtureRoot = path.join(root, 'backend/build/tmp/inline-deployment-fixtures')
const expected = 'a'.repeat(40)
const previous = 'b'.repeat(40)
const unrelated = 'c'.repeat(40)
const backend = parseDocument(fs.readFileSync(path.join(root, '.github/workflows/deploy-backend.yml'), 'utf8')).toJS()
const frontend = parseDocument(fs.readFileSync(path.join(root, '.github/workflows/deploy-frontend.yml'), 'utf8')).toJS()
const shellPath = (value) => process.platform === 'win32'
  ? value.replaceAll('\\', '/').replace(/^([A-Za-z]):/, (_, drive) => `/${drive.toLowerCase()}`)
  : value

function fixture(t) {
  fs.mkdirSync(fixtureRoot, { recursive: true })
  const directory = fs.mkdtempSync(path.join(fixtureRoot, 'case-'))
  t.after(() => {
    assert.ok(path.resolve(directory).startsWith(`${path.resolve(fixtureRoot)}${path.sep}`))
    fs.rmSync(directory, { recursive: true, force: true })
  })
  const write = (file, value) => {
    const target = path.join(directory, file)
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, value, 'utf8')
  }
  const read = (file) => fs.readFileSync(path.join(directory, file), 'utf8')
  write('app/app.jar', 'previous jar')
  write('process-state', 'active')
  write('runtime-sha', previous)
  const staging = `tmp/noviis-backend-${expected}-7-1`
  write(`${staging}/app.jar`, 'new jar')
  write(`${staging}/RELEASE_METADATA`, `commit_sha=${expected}\n`)
  write(`${staging}/SHA256SUMS`, ['app.jar', 'RELEASE_METADATA'].map((name) =>
    `${createHash('sha256').update(read(`${staging}/${name}`)).digest('hex')}  ${name}\n`,
  ).join(''))
  write('www/app/.noviis-release', expected)
  write('www/app/index.html', 'new frontend')
  write('www/app.rollback-7-1/.noviis-release', `${previous}\r\n`)
  write('www/app.rollback-7-1/index.html', 'previous frontend')
  return { directory, write, read }
}

// Only filesystem commands inside the isolated fixture execute. Service and HTTP
// operations are functions, so no sudo, systemd, deployment host or network is used.
const mocks = `
export PATH=/usr/bin:/bin:$PATH
sudo() {
  if [ "$1" = systemctl ]; then
    case "$2" in
      stop)
        [ "\${MOCK_STOP_FAIL:-false}" != true ] || return 1
        printf inactive > "$FIXTURE/process-state"
        return 0 ;;
      show) printf '%s\\n' "\${MOCK_STOP_PID:-0}"; return 0 ;;
      is-active)
        [ "\${MOCK_STILL_ACTIVE:-false}" = true ] || [ "$(cat "$FIXTURE/process-state")" = active ]
        return $? ;;
      start)
        printf active > "$FIXTURE/process-state"
        if [ "$(cat "$FIXTURE/app/app.jar")" = 'previous jar' ]; then
          printf '%s' "$PREVIOUS_SHA" > "$FIXTURE/runtime-sha"
        else
          printf '%s' "\${MOCK_START_SHA:-$EXPECTED_SHA}" > "$FIXTURE/runtime-sha"
        fi
        return 0 ;;
      daemon-reload) return 0 ;;
      *) return 64 ;;
    esac
  fi
  case "$1" in install|test|sha256sum|mv|rm|cat|tr) "$@" ;; *) return 64 ;; esac
}
curl() {
  case "$*" in
    */actuator/health*) [ "$(cat "$FIXTURE/process-state")" = active ] || return 22; printf '{"status":"UP"}' ;;
    */actuator/info*) printf '{"build":{"commit":"%s"}}' "$(cat "$FIXTURE/runtime-sha")" ;;
    *--resolve*) cat "$FIXTURE/www/app/.noviis-release" ;;
    *) [ "\${MOCK_PUBLIC_FAIL:-false}" != true ] || return 22; cat "$FIXTURE/www/app/.noviis-release" ;;
  esac
}
sleep() { :; }
`

function runStep(f, workflow, idOrName, options = {}) {
  const step = workflow.jobs.deploy.steps.find((item) => item.id === idOrName || item.name === idOrName)
  assert.ok(step?.with?.script, `missing script: ${idOrName}`)
  const base = shellPath(f.directory)
  const script = step.with.script
    .replaceAll('${{ inputs.expected_sha }}', expected)
    .replaceAll('${{ inputs.contract_migration }}', String(options.contract ?? false))
    .replaceAll('${{ github.run_id }}', '7')
    .replaceAll('${{ github.run_number }}', '3')
    .replaceAll('${{ github.run_attempt }}', '1')
    .replaceAll('/tmp/noviis-', `${base}/tmp/noviis-`)
    .replaceAll('/opt/app/backend', `${base}/app`)
    .replaceAll('/var/www', `${base}/www`)
  assert.ok(!script.includes('${{'), 'unresolved workflow expression')
  return spawnSync(process.env.BASH_EXECUTABLE ?? 'bash', ['-s'], {
    cwd: f.directory,
    input: `${mocks}\n${script}`,
    encoding: 'utf8',
    timeout: 15000,
    env: { ...process.env, FIXTURE: base, EXPECTED_SHA: expected, PREVIOUS_SHA: previous, ...options.env },
  })
}

function succeeds(result) {
  assert.equal(result.error, undefined)
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`)
}
function fails(result) {
  assert.equal(result.error, undefined)
  assert.notEqual(result.status, 0, result.stdout)
}

for (const [name, env] of [
  ['stop command fails', { MOCK_STOP_FAIL: 'true' }],
  ['MainPID remains after stop', { MOCK_STOP_PID: '42' }],
  ['service remains active after stop', { MOCK_STILL_ACTIVE: 'true' }],
]) {
  test(`backend refuses JAR replacement when ${name}`, (t) => {
    const f = fixture(t)
    fails(runStep(f, backend, 'activate', { env }))
    assert.equal(f.read('app/app.jar'), 'previous jar')
    assert.equal(f.read('runtime-sha'), previous)
  })
}

test('backend activation and independent readback require the running release', (t) => {
  const f = fixture(t)
  const activation = runStep(f, backend, 'activate')
  succeeds(activation)
  assert.match(activation.stdout, new RegExp(`ACTIVATED_SHA=${expected}`))
  succeeds(runStep(f, backend, 'reconcile'))
  f.write('runtime-sha', previous)
  fails(runStep(f, backend, 'reconcile'))
})

test('healthy wrong backend revision fails and restores a non-contract release', (t) => {
  const f = fixture(t)
  fails(runStep(f, backend, 'activate', { env: { MOCK_START_SHA: unrelated } }))
  assert.equal(f.read('app/app.jar'), 'previous jar')
  assert.equal(f.read('runtime-sha'), previous)
})

test('contract startup identity failure keeps the new JAR stopped without rollback', (t) => {
  const f = fixture(t)
  fails(runStep(f, backend, 'activate', { contract: true, env: { MOCK_START_SHA: unrelated } }))
  assert.equal(f.read('app/app.jar'), 'new jar')
  assert.equal(f.read('process-state'), 'inactive')
})

test('frontend public readback failure can roll back before activation-result succeeds', (t) => {
  const f = fixture(t)
  fails(runStep(f, frontend, 'reconcile', { env: { MOCK_PUBLIC_FAIL: 'true' } }))
  const rollback = frontend.jobs.deploy.steps.find((step) => step.name === 'Roll back frontend after verification failure')
  assert.equal(rollback.if, "failure() && steps.activate.outcome != 'skipped'")
  succeeds(runStep(f, frontend, rollback.name))
  assert.equal(f.read('www/app/.noviis-release').trim(), previous)
  assert.equal(f.read('www/app/index.html'), 'previous frontend')
})

test('frontend rollback refuses to overwrite a different active release', (t) => {
  const f = fixture(t)
  f.write('www/app/.noviis-release', unrelated)
  fails(runStep(f, frontend, 'Roll back frontend after verification failure'))
  assert.equal(f.read('www/app/.noviis-release'), unrelated)
  assert.equal(f.read('www/app.rollback-7-1/.noviis-release').trim(), previous)
})

test('frontend rollback validates the backup identity before deleting the active release', (t) => {
  const f = fixture(t)
  f.write('www/app.rollback-7-1/.noviis-release', 'invalid')
  fails(runStep(f, frontend, 'Roll back frontend after verification failure'))
  assert.equal(f.read('www/app/.noviis-release'), expected)
})

test('frontend rollback without a previous release leaves the target for operator recovery', (t) => {
  const f = fixture(t)
  const backup = path.resolve(f.directory, 'www/app.rollback-7-1')
  assert.ok(backup.startsWith(`${path.resolve(f.directory)}${path.sep}`))
  fs.rmSync(backup, { recursive: true })
  fails(runStep(f, frontend, 'Roll back frontend after verification failure'))
  assert.equal(f.read('www/app/.noviis-release'), expected)
})

test('persistent public verification failure is reported after restoring the previous frontend', (t) => {
  const f = fixture(t)
  fails(runStep(f, frontend, 'Roll back frontend after verification failure', { env: { MOCK_PUBLIC_FAIL: 'true' } }))
  assert.equal(f.read('www/app/.noviis-release').trim(), previous)
})


const ci = parseDocument(fs.readFileSync(path.join(root, '.github/workflows/ci.yml'), 'utf8')).toJS()
const smokeStep = ci.jobs['backend-postgres-migration'].steps.find((step) => step.name === 'Run PostgreSQL application context smoke test')
const smokeReportCheck = smokeStep.run.split("python3 - <<'PY'\n")[1].split('\nPY')[0]
for (const [name, attributes, expectedSuccess] of [
  ['missing report', null, false],
  ['all tests skipped', 'tests="2" skipped="2" failures="0" errors="0"', false],
  ['zero tests', 'tests="0" skipped="0" failures="0" errors="0"', false],
  ['executed successful test', 'tests="2" skipped="1" failures="0" errors="0"', true],
  ['test error', 'tests="2" skipped="0" failures="0" errors="1"', false],
]) {
  test(`PostgreSQL CI report guard: ${name}`, (t) => {
    const f = fixture(t)
    if (attributes !== null) {
      f.write('build/test-results/postgresSmokeTest/TEST-com.weedrice.whiteboard.PostgresApplicationContextSmokeTest.xml', `<testsuite ${attributes}/>`)
    }
    const result = spawnSync(process.platform === 'win32' ? 'python' : 'python3', ['-c', smokeReportCheck], {
      cwd: f.directory, encoding: 'utf8', timeout: 10000,
    })
    if (expectedSuccess) succeeds(result)
    else fails(result)
  })
}
