import { describe, expect, it } from 'vitest'
import { isInquiryPostItem, resolvePostDetailRoute } from '../postNavigation'

describe('postNavigation', () => {
  it('builds post detail routes by route name', () => {
    expect(resolvePostDetailRoute({ postId: 42 }, 'free', { page: '2' })).toEqual({
      name: 'post-detail',
      params: {
        boardUrl: 'free',
        postId: 42
      },
      query: {
        page: '2'
      }
    })
  })

  it('detects inquiry posts with a fallback board url', () => {
    expect(isInquiryPostItem({}, ' inquiry ')).toBe(true)
    expect(isInquiryPostItem({ boardUrl: 'free' }, 'inquiry')).toBe(false)
  })
})
