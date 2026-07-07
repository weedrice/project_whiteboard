import { afterEach, describe, expect, it, vi } from 'vitest'
import {
    formatDateOnlyLongOrDash,
    formatDateTimeOrDash,
    formatRelativeDate,
    formatTimeAgo,
} from '../date'

describe('date utilities', () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it('formats date time values with the shared Korean formatter', () => {
        const formatted = formatDateTimeOrDash('2026-05-26T01:02:03')

        expect(formatted).toContain('2026')
        expect(formatted).toContain('05')
        expect(formatted).toContain('01:02:03')
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

        expect(formatRelativeDate('2026-07-07T11:30:00')).toContain('11:30')
        expect(formatRelativeDate('2026-07-06T11:30:00')).toContain('2026')
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
