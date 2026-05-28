import { describe, expect, it } from 'vitest'
import { formatDateOnlyLongOrDash, formatDateTimeOrDash } from '../date'

describe('date utilities', () => {
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
})
