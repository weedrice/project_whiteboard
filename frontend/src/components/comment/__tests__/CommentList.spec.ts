import { ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import CommentList from '../CommentList.vue'

const { authState, commentsValue, commentsState, deleteComment, fetchNextPage } = vi.hoisted(() => ({
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
    hasNextPage: false,
  },
  deleteComment: vi.fn(),
  fetchNextPage: vi.fn(),
}))

let capturedCommentParams: Ref<{ page: number; size: number; sort: string }> | null = null

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
    useInfiniteComments: (_postId: Ref<string | number>, params: Ref<{ page: number; size: number; sort: string }>) => {
      capturedCommentParams = params
      return {
        data: ref({ pages: [commentsValue], pageParams: [0] }),
        isLoading: ref(false),
        isFetchingNextPage: ref(false),
        hasNextPage: ref(commentsState.hasNextPage),
        fetchNextPage,
        error: ref(commentsState.error),
      }
    },
    useBestComments: () => ({
      data: ref([]),
    }),
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
        emits: ['delete'],
        props: ['comment'],
        template: '<div data-testid="comment-item"><span>{{ comment.content }}</span><button type="button" data-testid="delete-comment" @click="$emit(\'delete\', comment)">delete</button></div>',
      },
      BaseSkeleton: true,
      BaseButton: {
        emits: ['click'],
        template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
      },
      BaseSegmentedControl: {
        props: ['modelValue', 'options', 'label'],
        emits: ['update:modelValue'],
        template: '<div data-testid="sort-control"><button v-for="option in options" :key="option.value" type="button" @click="$emit(\'update:modelValue\', option.value)">{{ option.label }}</button></div>',
      },
    },
  },
})

describe('CommentList', () => {
  beforeEach(() => {
    authState.isAuthenticated = true
    commentsState.error = null
    commentsState.hasNextPage = false
    fetchNextPage.mockClear()
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

  it('shows a load-more action when more comments exist and fetches the next page', async () => {
    commentsValue.content = Array.from({ length: 50 }, (_, index) => ({
      commentId: index + 1,
      content: `comment ${index + 1}`,
    }))
    commentsValue.totalElements = 75
    commentsState.hasNextPage = true

    const wrapper = mountCommentList()

    expect(wrapper.text()).toContain('댓글 더 보기 (25개 남음)')

    await wrapper.findAll('button').at(-1)?.trigger('click')

    expect(capturedCommentParams?.value).toEqual({ page: 0, size: 50, sort: 'createdAt,asc' })
    expect(fetchNextPage).toHaveBeenCalledTimes(1)
  })

  it('deletes comments with the current post id for scoped cache invalidation', async () => {
    const wrapper = mountCommentList()

    await wrapper.get('[data-testid="delete-comment"]').trigger('click')
    await Promise.resolve()

    expect(deleteComment).toHaveBeenCalledWith(
      { commentId: 1, postId: 1 },
      expect.any(Object),
    )
  })
})
