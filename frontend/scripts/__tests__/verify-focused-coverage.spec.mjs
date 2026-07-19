import { describe, expect, it } from 'vitest'
import {
  DEFAULT_FOCUSED_THRESHOLDS,
  verifyFocusedCoverage,
} from '../verify-focused-coverage.mjs'

function coverageFor(file, counts) {
  return {
    [file]: {
      path: file,
      statementMap: Object.fromEntries(counts.map((_, index) => [index, {
        start: { line: index + 1, column: 0 },
        end: { line: index + 1, column: 1 },
      }])),
      s: Object.fromEntries(counts.map((count, index) => [index, count])),
      fnMap: {},
      f: {},
      branchMap: {},
      b: {},
    },
  }
}

describe('verifyFocusedCoverage', () => {
  it('targets the attendance implementation instead of its compatibility re-export', () => {
    expect(DEFAULT_FOCUSED_THRESHOLDS.has('src/features/user/attendance/useAttendance.ts')).toBe(true)
    expect(DEFAULT_FOCUSED_THRESHOLDS.has('src/composables/useAttendance.ts')).toBe(false)
  })

  it('returns results when the threshold is met', () => {
    const thresholds = new Map([['src/example.ts', 80]])

    expect(verifyFocusedCoverage(coverageFor('/repo/src/example.ts', [1, 1, 1, 1, 0]), thresholds))
      .toEqual([{ target: 'src/example.ts', statements: 80, minimum: 80 }])
  })

  it('fails when statement coverage is below the threshold', () => {
    const thresholds = new Map([['src/example.ts', 80]])

    expect(() => verifyFocusedCoverage(
      coverageFor('/repo/src/example.ts', [1, 1, 1, 0, 0]), thresholds,
    )).toThrow('is 60%; minimum is 80%')
  })

  it('fails when a configured file is missing', () => {
    expect(() => verifyFocusedCoverage({}, new Map([['src/missing.ts', 70]])))
      .toThrow('Focused coverage file is missing: src/missing.ts')
  })
})
