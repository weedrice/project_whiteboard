import { describe, expect, it } from 'vitest'
import { formatInteger } from '../numberFormat'

describe('formatInteger', () => {
  it('formats integers with grouping separators', () => {
    expect(formatInteger(1234)).toBe('1,234')
  })

  it('formats missing values as zero', () => {
    expect(formatInteger(null)).toBe('0')
    expect(formatInteger(undefined)).toBe('0')
  })
})
