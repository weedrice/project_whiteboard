import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import HomePostCard from '../HomePostCard.vue'
import type { FeedPost } from '@/types'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push,
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/utils/date', () => ({
  formatTimeAgo: () => '방금 전',
}))

const makePost = (overrides: Partial<FeedPost> = {}): FeedPost => ({
  postId: 101,
  boardUrl: 'free',
  boardName: '자유게시판',
  authorName: '작성자',
  author: {
    userId: 1,
    displayName: '작성자',
    authorType: 'USER',
  },
  title: '오늘의 큐레이션',
  summary: '요약',
  contentsExcerpt: '<h2>소제목</h2><p><strong>강조</strong> 문단</p><ul><li>목록</li></ul>',
  viewCount: 10,
  likeCount: 2,
  commentCount: 1,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  liked: false,
  scrapped: false,
  subscribed: false,
  createdAt: '2026-05-19T10:00:00',
  ...overrides,
})

describe('HomePostCard', () => {
  it('renders formatted excerpt HTML and uses the no-media featured line limit', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost(),
        variant: 'featured',
      },
    })

    const body = wrapper.get('.nv-home-card-body')

    expect(body.classes()).toContain('prose-feed')
    expect(body.classes()).toContain('nv-home-card-body-featured-no-media')
    expect(body.html()).toContain('<h2>소제목</h2>')
    expect(body.html()).toContain('<strong>강조</strong>')
    expect(body.find('ul').exists()).toBe(true)
    expect(body.find('li').text()).toBe('목록')
  })

  it('uses the tighter featured line limit when media is present', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost({
          firstMediaType: 'image',
          firstMediaUrl: '/api/v1/files/1',
        }),
        variant: 'featured',
      },
    })

    expect(wrapper.get('.nv-home-card-body').classes()).toContain('nv-home-card-body-featured-with-media')
  })
})
