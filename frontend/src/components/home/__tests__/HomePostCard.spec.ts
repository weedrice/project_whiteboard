import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
  boardName: '자유스페이스',
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
  beforeEach(() => {
    push.mockClear()
  })

  it('renders formatted excerpt HTML and uses the no-media featured line limit', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost(),
        variant: 'featured',
      },
    })

    const body = wrapper.get('.nv-home-card-body')
    const content = body.get('.nv-home-curation-body')

    expect(wrapper.get('.nv-home-card').classes()).toContain('nv-elevated-surface')
    expect(content.classes()).toContain('nv-rich-content')
    expect(content.classes()).toContain('prose')
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

  it('omits media previews when they are disabled', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost({
          firstMediaType: 'image',
          firstMediaUrl: '/api/v1/files/1',
        }),
        variant: 'compact',
        showMediaPreview: false,
      },
    })

    expect(wrapper.find('.nv-home-media').exists()).toBe(false)
    expect(wrapper.find('img[src="/api/v1/files/1"]').exists()).toBe(false)
    expect(wrapper.get('.nv-home-card-body').classes()).toContain('nv-home-card-body-no-media')
  })

  it('omits the body while preserving the post title when body rendering is disabled', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost(),
        variant: 'compact',
        showBody: false,
      },
    })

    expect(wrapper.get('.nv-home-card-title').text()).toBe('오늘의 큐레이션')
    expect(wrapper.find('.nv-home-card-body').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('강조 문단')
    expect(wrapper.text()).not.toContain('요약')
  })

  it('keeps grid cards on the lightweight feed preview renderer', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost(),
        variant: 'grid',
      },
    })

    const body = wrapper.get('.nv-home-card-body')
    const content = body.get('.prose-feed')

    expect(content.classes()).toContain('prose-feed')
    expect(body.classes()).not.toContain('nv-rich-content')
    expect(body.classes()).not.toContain('prose')
  })

  it('renders blockquote HTML when the featured post has no media', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost({
          contentsExcerpt: '<blockquote><p>quoted text</p></blockquote>',
          firstMediaType: undefined,
          firstMediaUrl: undefined,
          thumbnailUrl: undefined,
        }),
        variant: 'featured',
      },
    })

    const body = wrapper.get('.nv-home-card-body')
    const content = body.get('.nv-home-curation-body')

    expect(body.classes()).toContain('nv-home-card-body-featured-no-media')
    expect(content.classes()).toContain('nv-rich-content')
    expect(body.find('blockquote').exists()).toBe(true)
    expect(body.find('blockquote p').text()).toBe('quoted text')
  })

  it('renders summary through the sanitized HTML path when an excerpt is not available', () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost({
          contentsExcerpt: undefined,
          summary: '<p><em>fallback</em> text</p><script>alert("xss")</script>',
        }),
        variant: 'featured',
      },
    })

    const body = wrapper.get('.nv-home-card-body')
    const content = body.get('.nv-home-curation-body')

    expect(content.classes()).toContain('nv-rich-content')
    expect(content.classes()).toContain('prose')
    expect(body.find('em').text()).toBe('fallback')
    expect(body.html()).not.toContain('<script>')
  })

  it('defers video iframe rendering until the preview is requested', async () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost({
          firstMediaType: 'video',
          firstMediaUrl: 'https://www.youtube.com/watch?v=video-id',
        }),
      },
    })

    expect(wrapper.find('iframe').exists()).toBe(false)

    await wrapper.get('button[aria-label="home.card.videoPreview"]').trigger('click')

    const iframe = wrapper.get('iframe')
    expect(iframe.attributes('src')).toBe('https://www.youtube-nocookie.com/embed/video-id')
    expect(iframe.attributes('sandbox')).toBe('allow-scripts allow-same-origin allow-presentation')
    expect(iframe.attributes('allow')).toBe('encrypted-media; picture-in-picture')
    expect(iframe.attributes('loading')).toBe('lazy')
    expect(iframe.attributes('referrerpolicy')).toBe('strict-origin-when-cross-origin')
    expect(iframe.attributes('allow')).not.toContain('clipboard-write')
    expect(iframe.attributes('allow')).not.toContain('autoplay')
    expect(push).not.toHaveBeenCalled()
  })

  it('uses a real title link and keeps the article out of link semantics', async () => {
    const wrapper = mount(HomePostCard, {
      props: {
        post: makePost(),
      },
    })

    const article = wrapper.get('article')
    const titleLink = wrapper.get('.nv-home-card-title-link')

    expect(article.attributes('role')).toBeUndefined()
    expect(article.attributes('tabindex')).toBeUndefined()
    expect(titleLink.attributes('href')).toBe('/board/free/post/101/')

    await article.trigger('click')
    expect(push).not.toHaveBeenCalled()

    await titleLink.trigger('click')

    expect(push).toHaveBeenCalledWith('/board/free/post/101/')
  })
})
