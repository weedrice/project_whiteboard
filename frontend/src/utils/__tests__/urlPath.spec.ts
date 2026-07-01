import { describe, expect, it } from 'vitest'
import { encodePathSegment } from '../urlPath'

describe('urlPath', () => {
  it('encodes one dynamic URL path segment', () => {
    expect(encodePathSegment('free board')).toBe('free%20board')
    expect(encodePathSegment('a/b')).toBe('a%2Fb')
    expect(encodePathSegment(12)).toBe('12')
  })
})
