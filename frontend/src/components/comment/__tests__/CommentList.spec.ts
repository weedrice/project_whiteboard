import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import CommentList from '../CommentList.vue'

const { authState, commentsValue, commentsState, deleteComment } = vi.hoisted(() => ({
  authState: {
    isAuthenticated: true
  },
  commentsValue: {
    content: [
      { commentId: 1, content: '첫 댓글' }
    ]
  },
  commentsState: {
    error: null as Error | null
  },
  deleteComment: vi.fn()
}))

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: vi.fn()
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true)
  })
}))

vi.mock('@/composables/useComment', () => ({
  useComment: () => ({
    useComments: () => ({
      data: ref(commentsValue),
      isLoading: ref(false),
      error: ref(commentsState.error)
    }),
    useDeleteComment: () => ({
      mutate: deleteComment
    })
  })
}))

describe('CommentList', () => {
  beforeEach(() => {
    authState.isAuthenticated = true
    commentsState.error = null
    commentsValue.content = [
      { commentId: 1, content: '첫 댓글' }
    ]
  })

  it('renders the composer before the comment list', () => {
    const wrapper = mount(CommentList, {
      props: {
        postId: 1,
        boardUrl: 'free'
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentForm: {
            template: '<div data-testid="comment-form">form</div>'
          },
          CommentItem: {
            props: ['comment'],
            template: '<div data-testid="comment-item">{{ comment.content }}</div>'
          },
          BaseSkeleton: true
        }
      }
    })

    const html = wrapper.html()

    expect(wrapper.find('#comment-composer').exists()).toBe(true)
    expect(html.indexOf('data-testid="comment-form"')).toBeLessThan(html.indexOf('data-testid="comment-item"'))
  })

  it('shows the login prompt in the top composer area for guests', () => {
    authState.isAuthenticated = false
    commentsValue.content = []

    const wrapper = mount(CommentList, {
      props: {
        postId: 1,
        boardUrl: 'free'
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentForm: true,
          CommentItem: true,
          BaseSkeleton: true
        }
      }
    })

    expect(wrapper.find('#comment-composer').text()).toContain('common.login')
    expect(wrapper.text()).toContain('comment.empty')
  })

  it('shows an explicit error state when comments fail to load', () => {
    commentsState.error = new Error('failed')

    const wrapper = mount(CommentList, {
      props: {
        postId: 1,
        boardUrl: 'free'
      },
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          CommentForm: true,
          CommentItem: true,
          BaseSkeleton: true
        }
      }
    })

    expect(wrapper.text()).toContain('댓글을 불러오지 못했습니다.')
  })
})
