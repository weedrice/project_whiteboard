import { describe, expect, it } from 'vitest'
import { optionalTrimmedText, trimText } from '../inputNormalization'

describe('inputNormalization', () => {
  it('trims nullable text-like values to a string', () => {
    expect(trimText('  value  ')).toBe('value')
    expect(trimText(123)).toBe('123')
    expect(trimText(null)).toBe('')
    expect(trimText(undefined)).toBe('')
  })

  it('returns undefined for blank optional text', () => {
    expect(optionalTrimmedText('  value  ')).toBe('value')
    expect(optionalTrimmedText('   ')).toBeUndefined()
    expect(optionalTrimmedText(null)).toBeUndefined()
  })
})
