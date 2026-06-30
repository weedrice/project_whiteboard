import { ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import CommentList from '../CommentList.vue'

const { authState, commentsValue, commentsState, deleteComment } = vi.hoisted(() => ({
  authState: {
    isAuthenticated: true,
  },
  commentsValue: {
    content: [
      { commentId: 1, content: '첫 댓글' },
    ],
    totalElements: 1,
  },
  commentsState: {
    error: null as Error | null,
  },
  deleteComment: vi.fn(),
}))

let capturedCommentParams: Ref<{ page: number; size: number }> | null = null

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: Record<string, number>) => ({
        'comment.loadFailed': '댓글을 불러오지 못했습니다.',
        'comment.loadMore': `댓글 더 보기 (${params?.remaining}개 남음)`,
      })[key] ?? key,
    }),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: vi.fn(),
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true),
  }),
}))

vi.mock('@/composables/useComment', () => ({
  useComment: () => ({
    useComments: (_postId: Ref<string | number>, params: Ref<{ page: number; size: number }>) => {
      capturedCommentParams = params
      return {
        data: ref(commentsValue),
        isLoading: ref(false),
        error: ref(commentsState.error),
      }
    },
    useDeleteComment: () => ({
      mutate: deleteComment,
    }),
  }),
}))

const mountCommentList = () => mount(CommentList, {
  props: {
    postId: 1,
    boardUrl: 'free',
  },
  global: {
    mocks: {
      $t: (key: string, params?: Record<string, number>) => (
        key === 'comment.loadMore' ? `댓글 더 보기 (${params?.remaining}개 남음)` : key
      ),
    },
    stubs: {
      RouterLink: RouterLinkStub,
      CommentForm: {
        template: '<div data-testid="comment-form">form</div>',
      },
      CommentItem: {
        props: ['comment'],
        template: '<div data-testid="comment-item">{{ comment.content }}</div>',
      },
      BaseSkeleton: true,
      BaseButton: {
        emits: ['click'],
        template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
      },
    },
  },
})

describe('CommentList', () => {
  beforeEach(() => {
    authState.isAuthenticated = true
    commentsState.error = null
    commentsValue.content = [
      { commentId: 1, content: '첫 댓글' },
    ]
    commentsValue.totalElements = 1
    capturedCommentParams = null
  })

  it('renders the composer before the comment list', () => {
    const wrapper = mountCommentList()
    const html = wrapper.html()

    expect(wrapper.find('#comment-composer').exists()).toBe(true)
    expect(html.indexOf('data-testid="comment-form"')).toBeLessThan(html.indexOf('data-testid="comment-item"'))
  })

  it('shows the login prompt in the top composer area for guests', () => {
    authState.isAuthenticated = false
    commentsValue.content = []
    commentsValue.totalElements = 0

    const wrapper = mountCommentList()

    expect(wrapper.find('#comment-composer').text()).toContain('common.login')
    expect(wrapper.text()).toContain('comment.empty')
  })

  it('shows an explicit error state when comments fail to load', () => {
    commentsState.error = new Error('failed')

    const wrapper = mountCommentList()

    expect(wrapper.text()).toContain('댓글을 불러오지 못했습니다.')
  })

  it('shows a load-more action when more comments exist and increases page size', async () => {
    commentsValue.content = Array.from({ length: 50 }, (_, index) => ({
      commentId: index + 1,
      content: `comment ${index + 1}`,
    }))
    commentsValue.totalElements = 75

    const wrapper = mountCommentList()

    expect(wrapper.text()).toContain('댓글 더 보기 (25개 남음)')

    await wrapper.findAll('button').at(-1)?.trigger('click')

    expect(capturedCommentParams?.value).toEqual({ page: 0, size: 100 })
  })
})
