import { describe, expect, it } from 'vitest'
import { buildCanonicalUrl, normalizeSeoPath } from '@/utils/seoUrl'

describe('seoUrl', () => {
  it('uses one canonical origin and trailing slash policy for public listing and post routes', () => {
    expect(buildCanonicalUrl('/')).toBe('https://noviis.kr/')
    expect(buildCanonicalUrl('/boards')).toBe('https://noviis.kr/boards/')
    expect(buildCanonicalUrl('/board/free')).toBe('https://noviis.kr/board/free/')
    expect(buildCanonicalUrl('/board/free/post/42?from=home')).toBe('https://noviis.kr/board/free/post/42/')
  })

  it('removes duplicate slashes only at the end of routes outside the SEO listing policy', () => {
    expect(normalizeSeoPath('/emoticons/')).toBe('/emoticons')
    expect(normalizeSeoPath('/tag/vue/?page=2')).toBe('/tag/vue')
  })
})
