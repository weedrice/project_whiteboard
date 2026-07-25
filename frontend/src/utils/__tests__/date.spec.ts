import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    formatDateOnlyLongOrDash,
    formatDateTimeOrDash,
    formatRelativeDate,
    formatTimeAgo,
    withServerOffset
} from '../date'

describe('date utilities', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it('formats date time values with the shared Korean formatter', () => {
        // 서버 시각은 KST 기준이므로 실행 환경(UTC)에서는 9시간 이른 시각으로 표시된다.
        // 표시 지역을 사용자 기준으로 바꾸는 것은 G1의 범위다.
        const formatted = formatDateTimeOrDash('2026-05-26T01:02:03+09:00')

        expect(formatted).toContain('2026')
        expect(formatted).toContain('05')
        expect(formatted).toContain('16:02:03')
    })

    it('formats long date-only values for detail screens', () => {
        const formatted = formatDateOnlyLongOrDash('2026-05-26T01:02:03')

        expect(formatted).toContain('2026')
        expect(formatted).toContain('5')
        expect(formatted).toContain('26')
    })

    it('returns dash for missing or invalid values', () => {
        expect(formatDateTimeOrDash(undefined)).toBe('-')
        expect(formatDateTimeOrDash('not-a-date')).toBe('-')
        expect(formatDateOnlyLongOrDash(null)).toBe('-')
        expect(formatDateOnlyLongOrDash('not-a-date')).toBe('-')
    })

    it('formats relative dates against the current day and ignores invalid values', () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-07-07T12:00:00.000Z'))

        // 같은 순간을 offset 유무와 관계없이 같게 읽는지 확인한다.
        expect(formatRelativeDate('2026-07-07T11:30:00+09:00')).toContain('02:30')
        expect(formatRelativeDate('2026-07-07T02:30:00Z')).toContain('02:30')
        expect(formatRelativeDate('2026-07-06T11:30:00+09:00')).toContain('2026')
        expect(formatRelativeDate('not-a-date')).toBe('')
        expect(formatRelativeDate('')).toBe('')
    })

    it('formats elapsed time and ignores invalid values', () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date('2026-07-07T12:00:00.000Z'))
        const t = (key: string, values?: Record<string, unknown>) =>
            values?.count == null ? key : `${key}:${values.count}`

        expect(formatTimeAgo('2026-07-07T11:59:30.000Z', t)).toBe('common.time.justNow')
        expect(formatTimeAgo('2026-07-07T11:55:00.000Z', t)).toBe('common.time.minutesAgo:5')
        expect(formatTimeAgo([2026, 7, 7, 10, 0, 0], t)).toBe('common.time.hoursAgo:2')
        expect(formatTimeAgo('not-a-date', t)).toBe('')
    })
})

describe('withServerOffset', () => {
    it('offset이 없는 date-time에 서버 기준 offset을 붙인다', () => {
        // ECMAScript 규격상 offset 없는 date-time은 브라우저 로컬로 해석된다.
        // 그대로 두면 같은 값이 사용자 지역마다 다른 순간을 가리킨다.
        expect(withServerOffset('2026-07-25T12:00:00')).toBe('2026-07-25T12:00:00+09:00')
    })

    it('이미 offset이 있으면 건드리지 않는다', () => {
        expect(withServerOffset('2026-07-25T12:00:00+09:00')).toBe('2026-07-25T12:00:00+09:00')
        expect(withServerOffset('2026-07-25T12:00:00-04:00')).toBe('2026-07-25T12:00:00-04:00')
        expect(withServerOffset('2026-07-25T03:00:00Z')).toBe('2026-07-25T03:00:00Z')
    })

    it('날짜만 있는 값에는 붙이지 않는다', () => {
        // date-only는 규격상 UTC 자정으로 해석되며 출석 달력이 이 동작에 의존한다.
        expect(withServerOffset('2026-07-25')).toBe('2026-07-25')
    })
})

describe('서버 시각을 지역과 무관하게 같은 순간으로 읽는다', () => {
    it('offset 유무와 관계없이 같은 순간을 가리킨다', () => {
        const withOffset = new Date(withServerOffset('2026-07-25T12:00:00+09:00')).getTime()
        const withoutOffset = new Date(withServerOffset('2026-07-25T12:00:00')).getTime()
        const asUtc = new Date(withServerOffset('2026-07-25T03:00:00Z')).getTime()

        expect(withoutOffset).toBe(withOffset)
        expect(asUtc).toBe(withOffset)
    })
})
