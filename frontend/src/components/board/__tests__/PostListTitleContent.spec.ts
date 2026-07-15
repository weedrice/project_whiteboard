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

  it('shows inquiry status when the parent marks the post as inquiry', () => {
    const wrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          inquiryAnswered: true
        }),
        showInquiryStatus: true
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
          }
        })
      }
    })

    expect(generalWrapper.text()).not.toContain('일반')
  })

  it('shows the image indicator for summary image flags', () => {
    const wrapper = mount(PostListTitleContent, {
      props: {
        post: createPost({
          hasImage: true
        })
      }
    })

    expect(wrapper.findAll('svg')).toHaveLength(1)
  })
})
