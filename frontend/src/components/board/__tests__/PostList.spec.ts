import { describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import PostList from '../PostList.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/utils/date', () => ({
  formatRelativeDate: () => 'now'
}))

describe('PostList', () => {
  it('preserves pagination query in post detail links when provided', () => {
    const wrapper = mount(PostList, {
      props: {
        posts: [
          {
            postId: 101,
            boardUrl: 'free',
            title: '페이지 유지 테스트',
            createdAt: '2026-04-15T00:00:00',
            viewCount: 1,
            likeCount: 2,
            commentCount: 3
          }
        ],
        linkQuery: {
          page: '3',
          q: 'vue'
        }
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          BaseTable: true,
          UserMenu: true
        }
      }
    })

    const postLink = wrapper.findComponent(RouterLinkStub)

    expect(postLink.exists()).toBe(true)
    expect(postLink.props('to')).toEqual({
      path: '/board/free/post/101',
      query: {
        page: '3',
        q: 'vue'
      }
    })
  })
})
