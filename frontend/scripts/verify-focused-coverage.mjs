import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import istanbulCoverage from 'istanbul-lib-coverage'

const { createCoverageMap } = istanbulCoverage

export const DEFAULT_FOCUSED_THRESHOLDS = new Map([
  ['src/queryClient.ts', 80],
  ['src/composables/useAttendance.ts', 80],
  ['src/features/user/profile/useAccountDeletion.ts', 80],
  ['src/features/board/posts/detail/usePostDetailKeyboardShortcuts.ts', 80],
  ['src/views/user/DraftList.vue', 70],
  ['src/views/auth/FindAccountPage.vue', 70],
  ['src/views/user/UserProfilePage.vue', 70],
])

export function verifyFocusedCoverage(coverageData, thresholds = DEFAULT_FOCUSED_THRESHOLDS) {
  const coverageMap = createCoverageMap(coverageData)
  const normalizedFiles = new Map(
    coverageMap.files().map((file) => [file.replaceAll('\\', '/'), file]),
  )
  const results = []

  for (const [target, minimum] of thresholds) {
    const normalizedTarget = target.replaceAll('\\', '/')
    const file = [...normalizedFiles.entries()]
      .find(([normalizedFile]) => normalizedFile.endsWith(normalizedTarget))?.[1]
    if (!file) {
      throw new Error(`Focused coverage file is missing: ${target}`)
    }
    const statements = coverageMap.fileCoverageFor(file).toSummary().statements.pct
    if (typeof statements !== 'number' || statements < minimum) {
      throw new Error(
        `Focused statement coverage for ${target} is ${statements}%; minimum is ${minimum}%.`,
      )
    }
    results.push({ target, statements, minimum })
  }
  return results
}

export function runFocusedCoverageCheck({ cwd = process.cwd() } = {}) {
  const coveragePath = path.resolve(cwd, 'coverage', 'coverage-final.json')
  if (!existsSync(coveragePath)) {
    throw new Error('coverage/coverage-final.json is missing. Run coverage first.')
  }
  const results = verifyFocusedCoverage(JSON.parse(readFileSync(coveragePath, 'utf8')))
  process.stdout.write(
    `Focused statement coverage OK: ${results.map(({ target, statements }) => `${target} ${statements}%`).join(', ')}.\n`,
  )
  return results
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : null
if (invokedPath === import.meta.url) {
  try {
    runFocusedCoverageCheck()
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`)
    process.exitCode = 1
  }
}
