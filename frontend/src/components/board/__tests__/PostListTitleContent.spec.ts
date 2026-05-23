import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { PostSummary } from '@/types'
import PostListTitleContent from '../PostListTitleContent.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

describe('PostListTitleContent', () => {
  const createPost = (overrides: Partial<PostSummary> = {}): PostSummary => ({
    postId: 1,
    title: 'Post title',
    createdAt: '2026-04-15T00:00:00',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    author: {
      userId: 1,
      displayName: 'Author'
    },
    ...overrides
  })

  it('shows inquiry status with the normalized board url fallback', () => {
    const wrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          inquiryAnswered: true
        }),
        boardUrl: 'inquiry'
      },
      global: {
        mocks: {
          $t: (key: string) => key
        }
      }
    })

    expect(wrapper.text()).toContain('board.inquiryStatus.answered')
  })

  it('suppresses the general category badge but keeps real categories', () => {
    const wrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          category: {
            categoryId: 2,
            name: 'QnA',
            sortOrder: 1,
            isActive: true,
            minWriteRole: 'USER'
          }
        })
      }
    })

    expect(wrapper.text()).toContain('QnA')

    const generalWrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          category: {
            categoryId: 1,
            name: '일반',
            sortOrder: 1,
            isActive: true,
            minWriteRole: 'USER'
          }
        })
      }
    })

    expect(generalWrapper.text()).not.toContain('일반')
  })

  it('shows the image indicator for lowercase media types', () => {
    const wrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          firstMediaType: 'image'
        })
      }
    })

    expect(wrapper.findAll('svg')).toHaveLength(1)
  })
})
