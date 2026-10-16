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

function fixture(t, runAttempt = 1) {
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
  const staging = `tmp/noviis-backend-${expected}-7-${runAttempt}`
  write(`${staging}/app.jar`, 'new jar')
  write(`${staging}/RELEASE_METADATA`, `commit_sha=${expected}\nrun_id=7\nrun_number=3\nrun_attempt=1\n`)
  const checksum = () => write(`${staging}/SHA256SUMS`, ['app.jar', 'RELEASE_METADATA'].map((name) =>
    `${createHash('sha256').update(read(`${staging}/${name}`)).digest('hex')}  ${name}\n`,
  ).join(''))
  checksum()
  write('www/app/.noviis-release', expected)
  write('www/app/index.html', 'new frontend')
  write('www/app.rollback-7-1/.noviis-release', `${previous}\r\n`)
  write('www/app.rollback-7-1/index.html', 'previous frontend')
  return { directory, write, read, staging, checksum }
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
        if [ "\${MOCK_RECOVERY_STOP_FAIL:-false}" = true ] && [ "$(cat "$FIXTURE/app/app.jar")" = 'new jar' ]; then return 1; fi
        printf inactive > "$FIXTURE/process-state"
        if [ "\${MOCK_STOP_PARTIAL_FAILURE:-false}" = true ] && [ ! -f "$FIXTURE/stop-failed" ]; then
          printf failed > "$FIXTURE/stop-failed"
          return 1
        fi
        return 0 ;;
      show)
        if [ "$4" = --property=MainPID ]; then
          printf '%s\\n' "\${MOCK_STOP_PID:-0}"
        else
          printf '%s' "$*" > "$FIXTURE/diagnostic-command"
          [ "\${MOCK_DIAGNOSTIC_FAIL:-false}" != true ] || return 1
          printf 'ActiveState=%s\\nExecMainStatus=0\\nNRestarts=0\\n' "$(cat "$FIXTURE/process-state")"
        fi
        return 0 ;;
      is-active)
        [ "\${MOCK_STILL_ACTIVE:-false}" = true ] || [ "$(cat "$FIXTURE/process-state")" = active ]
        return $? ;;
      start)
        if [ "\${MOCK_START_FAIL:-false}" = true ] && [ "$(cat "$FIXTURE/app/app.jar")" != 'previous jar' ]; then
          return 1
        fi
        printf active > "$FIXTURE/process-state"
        if [ "$(cat "$FIXTURE/app/app.jar")" = 'previous jar' ]; then
          printf '%s' "$PREVIOUS_SHA" > "$FIXTURE/runtime-sha"
        else
          printf '%s' "\${MOCK_START_SHA:-$EXPECTED_SHA}" > "$FIXTURE/runtime-sha"
        fi
        return 0 ;;
      daemon-reload) [ "\${MOCK_RELOAD_FAIL:-false}" != true ]; return $? ;;
      *) return 64 ;;
    esac
  fi
  case "$1" in
    install)
      if [ "\${MOCK_PREPARE_FAIL:-false}" = true ] && [ "$5" = "$FIXTURE/app/app.jar.next" ]; then return 1; fi
      "$@" || return $?
      if [ "\${MOCK_PREPARE_CORRUPT:-false}" = true ] && [ "$5" = "$FIXTURE/app/app.jar.next" ]; then
        printf corrupt > "$5"
      fi ;;
    mv)
      [ "\${MOCK_SWAP_FAIL:-false}" != true ] || return 1
      "$@" || return $?
      if [ "\${MOCK_BACKUP_CORRUPT:-false}" = true ]; then
        printf corrupt > "$FIXTURE/app/app.jar.rollback"
        return 1
      fi
      [ "\${MOCK_SWAP_RESULT_FAIL:-false}" != true ] || return 1
      if [ "\${MOCK_SWAP_TERM:-false}" = true ]; then kill -TERM $$; fi ;;
    test|sha256sum|rm|cat|tr) "$@" ;;
    *) return 64 ;;
  esac
}
curl() {
  case "$*" in
    */actuator/health*)
      [ "$(cat "$FIXTURE/process-state")" = active ] || return 22
      if [ "$(cat "$FIXTURE/app/app.jar")" = 'new jar' ]; then
        [ "\${MOCK_HEALTH_FAIL:-false}" != true ] || return 7
        probes=$(cat "$FIXTURE/health-probes" 2>/dev/null || printf 0)
        probes=$((probes + 1))
        printf '%s' "$probes" > "$FIXTURE/health-probes"
        [ "$probes" -gt "\${MOCK_HEALTH_DELAY:-0}" ] || return 7
      fi
      printf '{"status":"UP"}' ;;
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
    .replaceAll('${{ inputs.release_metadata_attempt }}', String(options.metadataAttempt ?? 1))
    .replaceAll('${{ github.run_id }}', '7')
    .replaceAll('${{ github.run_number }}', '3')
    .replaceAll('${{ github.run_attempt }}', String(options.runAttempt ?? 1))
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

for (const [name, env] of [
  ['new JAR copy fails', { MOCK_PREPARE_FAIL: 'true' }],
  ['prepared JAR digest differs', { MOCK_PREPARE_CORRUPT: 'true' }],
  ['daemon reload fails', { MOCK_RELOAD_FAIL: 'true' }],
]) {
  test(`backend preparation keeps the previous service running when ${name}`, (t) => {
    const f = fixture(t)
    fails(runStep(f, backend, 'activate', { env }))
    assert.equal(f.read('process-state'), 'active')
    assert.equal(f.read('app/app.jar'), 'previous jar')
    assert.equal(f.read('runtime-sha'), previous)
  })
}

for (const [name, env] of [
  ['stop returns failure after stopping', { MOCK_STOP_PARTIAL_FAILURE: 'true' }],
  ['JAR swap fails', { MOCK_SWAP_FAIL: 'true' }],
  ['JAR swap succeeds but reports failure', { MOCK_SWAP_RESULT_FAIL: 'true' }],
  ['termination follows the JAR swap', { MOCK_SWAP_TERM: 'true' }],
]) {
  for (const contract of [false, true]) {
    test(`backend recovers pre-start failure when ${name} (contract=${contract})`, (t) => {
      const f = fixture(t)
      const result = runStep(f, backend, 'activate', { env, contract })
      fails(result)
      assert.equal(f.read('process-state'), 'active')
      assert.equal(f.read('app/app.jar'), 'previous jar')
      assert.equal(f.read('runtime-sha'), previous)
      assert.match(result.stderr, /Previous backend restored and healthy/)
    })
  }
}

for (const contract of [false, true]) {
  test(`backend start command failure respects the contract rollback boundary (contract=${contract})`, (t) => {
    const f = fixture(t)
    fails(runStep(f, backend, 'activate', { env: { MOCK_START_FAIL: 'true' }, contract }))
    assert.equal(f.read('process-state'), contract ? 'inactive' : 'active')
    assert.equal(f.read('app/app.jar'), contract ? 'new jar' : 'previous jar')
  })
}

test('backend recovery rejects a changed backup before restoring any JAR', (t) => {
  const f = fixture(t)
  const result = runStep(f, backend, 'activate', { env: { MOCK_BACKUP_CORRUPT: 'true' } })
  fails(result)
  assert.equal(f.read('process-state'), 'inactive')
  assert.equal(f.read('app/app.jar'), 'new jar')
  assert.match(result.stderr, /Backend recovery failed; operator recovery is required/)
})

test('backend recovery does not replace a JAR when the new process cannot stop', (t) => {
  const f = fixture(t)
  const result = runStep(f, backend, 'activate', {
    env: { MOCK_START_SHA: unrelated, MOCK_RECOVERY_STOP_FAIL: 'true' },
  })
  fails(result)
  assert.equal(f.read('process-state'), 'active')
  assert.equal(f.read('app/app.jar'), 'new jar')
  assert.match(result.stderr, /Backend recovery failed; operator recovery is required/)
})

test('backend without a previous JAR stops a failed first release for operator recovery', (t) => {
  const f = fixture(t)
  fs.unlinkSync(path.join(f.directory, 'app/app.jar'))
  f.write('process-state', 'inactive')
  const result = runStep(f, backend, 'activate', { env: { MOCK_START_FAIL: 'true' } })
  fails(result)
  assert.equal(f.read('process-state'), 'inactive')
  assert.equal(f.read('app/app.jar'), 'new jar')
  assert.match(result.stderr, /No previous backend JAR is available; operator recovery is required/)
})

test('backend activation and independent readback require the running release', (t) => {
  const f = fixture(t)
  const activation = runStep(f, backend, 'activate')
  succeeds(activation)
  assert.match(activation.stdout, new RegExp(`ACTIVATED_SHA=${expected}`))
  succeeds(runStep(f, backend, 'reconcile'))
  f.write('runtime-sha', previous)
  fails(runStep(f, backend, 'reconcile'))
})

test('backend readiness tolerates delayed startup within the existing probe budget', (t) => {
  const f = fixture(t)
  succeeds(runStep(f, backend, 'activate', { env: { MOCK_HEALTH_DELAY: '3' } }))
  assert.equal(f.read('health-probes'), '4')
})

for (const contract of [false, true]) {
  for (const diagnosticsFail of [false, true]) {
    test(`unavailable backend health preserves recovery and exit status (contract=${contract}, diagnosticsFail=${diagnosticsFail})`, (t) => {
      const f = fixture(t)
      const result = runStep(f, backend, 'activate', {
        contract,
        env: { MOCK_HEALTH_FAIL: 'true', MOCK_DIAGNOSTIC_FAIL: String(diagnosticsFail) },
      })
      fails(result)
      assert.equal(result.status, 1)
      assert.match(result.stderr, /health_unavailable after 45 probes/)
      assert.match(result.stderr, /phase=readiness exit=1/)
      assert.equal(f.read('diagnostic-command'), 'systemctl show app --property=ActiveState,SubState,Result,ExecMainCode,ExecMainStatus,NRestarts,MainPID')
      assert.match(result.stderr, diagnosticsFail ? /diagnostics unavailable/ : /ActiveState=active/)
      assert.equal(f.read('process-state'), contract ? 'inactive' : 'active')
      assert.equal(f.read('app/app.jar'), contract ? 'new jar' : 'previous jar')
      assert.doesNotMatch(result.stdout, /ACTIVATED_SHA=/)
    })
  }
}

for (const field of ['commit_sha', 'run_id', 'run_number', 'run_attempt']) {
  test(`backend readback rejects mismatched ${field} even with a valid checksum`, (t) => {
    const f = fixture(t)
    succeeds(runStep(f, backend, 'activate'))
    f.write(`${f.staging}/RELEASE_METADATA`, f.read(`${f.staging}/RELEASE_METADATA`).replace(new RegExp(`${field}=.*`), `${field}=999`))
    f.checksum()
    const result = runStep(f, backend, 'reconcile')
    fails(result)
    assert.match(result.stderr, /phase=artifact/)
    assert.doesNotMatch(result.stdout, /ACTIVATED_SHA=/)
  })
}

test('backend readback uses the producer attempt when consuming an earlier candidate', (t) => {
  const f = fixture(t, 2)
  succeeds(runStep(f, backend, 'activate', { runAttempt: 2 }))
  succeeds(runStep(f, backend, 'reconcile', { runAttempt: 2, metadataAttempt: 1 }))
  fails(runStep(f, backend, 'reconcile', { runAttempt: 2, metadataAttempt: 2 }))
})

test('backend readback rejects corrupted staged metadata', (t) => {
  const f = fixture(t)
  succeeds(runStep(f, backend, 'activate'))
  f.write(`${f.staging}/RELEASE_METADATA`, `${f.read(`${f.staging}/RELEASE_METADATA`)}tampered=true\n`)
  const result = runStep(f, backend, 'reconcile')
  fails(result)
  assert.doesNotMatch(result.stdout, /ACTIVATED_SHA=/)
})

for (const phase of ['service', 'health', 'runtime_identity']) {
  test(`backend readback reports ${phase} failure without publishing success`, (t) => {
    const f = fixture(t)
    succeeds(runStep(f, backend, 'activate'))
    if (phase === 'service') f.write('process-state', 'inactive')
    if (phase === 'runtime_identity') f.write('runtime-sha', unrelated)
    const result = runStep(f, backend, 'reconcile', { env: { MOCK_HEALTH_FAIL: String(phase === 'health') } })
    fails(result)
    assert.match(result.stderr, new RegExp(`phase=${phase}`))
    assert.doesNotMatch(result.stdout, /ACTIVATED_SHA=/)
  })
}

for (const outcome of ['success', 'failure', 'skipped', 'cancelled']) {
  test(`backend activation output requires successful independent readback: ${outcome}`, (t) => {
    const f = fixture(t)
    const step = backend.jobs.deploy.steps.find((item) => item.id === 'activation-result')
    f.write('github-output', '')
    const result = spawnSync(process.env.BASH_EXECUTABLE ?? 'bash', ['-s'], {
      cwd: f.directory, input: step.run, encoding: 'utf8', timeout: 10000,
      env: { ...process.env, EXPECTED_SHA: expected, RECONCILIATION_OUTCOME: outcome, GITHUB_OUTPUT: shellPath(path.join(f.directory, 'github-output')) },
    })
    if (outcome === 'success') succeeds(result)
    else fails(result)
    assert.equal(f.read('github-output'), outcome === 'success' ? `activated_sha=${expected}\n` : '')
  })
}

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
