import { describe, expect, it } from 'vitest'
import { firstQueryValue, getSingleQueryValue } from '@/utils/routeQueryValue'

describe('routeQueryValue', () => {
  it('returns a string value for search query fields', () => {
    expect(firstQueryValue(' search ')).toBe(' search ')
    expect(firstQueryValue(['first', 'second'])).toBe('first')
    expect(firstQueryValue([])).toBe('')
    expect(firstQueryValue(null)).toBe('')
    expect(firstQueryValue(undefined)).toBe('')
  })

  it('returns null for missing or empty single query values', () => {
    expect(getSingleQueryValue('token')).toBe('token')
    expect(getSingleQueryValue(['first', 'second'])).toBe('first')
    expect(getSingleQueryValue('')).toBeNull()
    expect(getSingleQueryValue([''])).toBeNull()
    expect(getSingleQueryValue(null)).toBeNull()
    expect(getSingleQueryValue(undefined)).toBeNull()
  })
})
