import { describe, expect, it } from 'vitest'
import { buildPostDetailPath, encodePathSegment, normalizePostDetailPath } from '../urlPath'

describe('urlPath', () => {
  it('encodes one dynamic URL path segment', () => {
    expect(encodePathSegment('free board')).toBe('free%20board')
    expect(encodePathSegment('a/b')).toBe('a%2Fb')
    expect(encodePathSegment(12)).toBe('12')
  })

  it('builds the canonical trailing-slash post detail path', () => {
    expect(buildPostDetailPath('free', 42)).toBe('/board/free/post/42/')
    expect(buildPostDetailPath('free board', 'a/b')).toBe('/board/free%20board/post/a%2Fb/')
    expect(buildPostDetailPath('free', 42, '#comment-7')).toBe('/board/free/post/42/#comment-7')
  })

  it('normalizes legacy post links without changing non-detail routes', () => {
    expect(normalizePostDetailPath('/board/free/post/42')).toBe('/board/free/post/42/')
    expect(normalizePostDetailPath('/board/free/post/42#comment-7')).toBe('/board/free/post/42/#comment-7')
    expect(normalizePostDetailPath('/board/free/post/42/?from=push')).toBe('/board/free/post/42/?from=push')
    expect(normalizePostDetailPath('/board/free/post/42/edit')).toBe('/board/free/post/42/edit')
  })
})
