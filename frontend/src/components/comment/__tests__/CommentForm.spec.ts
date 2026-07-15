import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommentForm from '../CommentForm.vue'

const createComment = vi.fn()
const updateComment = vi.fn()
const addToast = vi.fn()
const apiMocks = vi.hoisted(() => ({
  getMentionCandidates: vi.fn(),
}))
const isCreating = ref(false)
const isUpdating = ref(false)

vi.mock('vue-i18n', () => ({
  createI18n: vi.fn(() => ({
    global: {
      t: (key: string) => key,
    },
  })),
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/features/comments/queries/useComment', () => ({
  useComment: () => ({
    useCreateComment: () => ({ mutate: createComment, isPending: isCreating }),
    useUpdateComment: () => ({ mutate: updateComment, isPending: isUpdating }),
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}))

vi.mock('@/api/userAccountApi', () => ({
  userAccountApi: {
    getMentionCandidates: apiMocks.getMentionCandidates,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn() },
}))

const BaseTextareaStub = defineComponent({
  name: 'BaseTextarea',
  inheritAttrs: false,
  props: {
    modelValue: { type: String, default: '' },
    id: { type: String, default: '' },
    name: { type: String, default: '' },
    label: { type: String, default: '' },
    hideLabel: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { attrs, emit }) {
    return () =>
      h('div', [
        props.label ? h('label', { for: props.id, class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
        h('textarea', {
          ...attrs,
          id: props.id,
          name: props.name,
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
        }),
      ])
  },
})

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    type: { type: String, default: 'button' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          type: props.type,
          disabled: props.disabled,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const mountCommentForm = (props: Record<string, unknown> = {}) =>
  mount(CommentForm, {
    props: {
      postId: 10,
      ...props,
    },
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        BaseTextarea: BaseTextareaStub,
        BaseButton: BaseButtonStub,
        EmoticonPicker: true,
        Smile: true,
      },
    },
  })

describe('CommentForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    isCreating.value = false
    isUpdating.value = false
    apiMocks.getMentionCandidates.mockResolvedValue({
      data: {
        success: true,
        data: [],
      },
    })
  })

  it('keeps a hidden textarea label for new comments and replies', () => {
    const commentWrapper = mountCommentForm()
    const replyWrapper = mountCommentForm({ parentId: 20 })

    expect(commentWrapper.get('label').classes()).toContain('sr-only')
    expect(commentWrapper.get('label').text()).toBe('comment.writeComment')
    expect(replyWrapper.get('label').classes()).toContain('sr-only')
    expect(replyWrapper.get('label').text()).toBe('comment.writeReply')
  })

  it('uses context-specific textarea ids and names', () => {
    const commentWrapper = mountCommentForm({ postId: 'board/post:10' })
    const replyWrapper = mountCommentForm({ parentId: 20 })
    const editWrapper = mountCommentForm({ commentId: 30, initialContent: 'before' })

    expect(commentWrapper.get('textarea').attributes()).toMatchObject({
      id: 'comment-new-board-post-10',
      name: 'comment-content',
    })
    expect(replyWrapper.get('textarea').attributes('id')).toBe('comment-reply-20')
    expect(editWrapper.get('textarea').attributes('id')).toBe('comment-edit-30')
  })

  it('labels the emoticon toggle button and exposes pressed state', async () => {
    const wrapper = mountCommentForm()
    const button = wrapper.get('button[aria-label="board.writePost.toolbar.emoticon"]')

    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.attributes('title')).toBe('board.writePost.toolbar.emoticon')

    await button.trigger('click')

    expect(button.attributes('aria-pressed')).toBe('true')
  })

  it('keeps submit disabled and skips mutation for whitespace-only content', async () => {
    const wrapper = mountCommentForm({ parentId: 20 })

    await wrapper.get('textarea').setValue('   ')
    const submitButton = wrapper.findAll('button').at(-1)

    expect(submitButton?.attributes('disabled')).toBeDefined()
    await wrapper.get('form').trigger('submit')

    expect(createComment).not.toHaveBeenCalled()
    expect(updateComment).not.toHaveBeenCalled()
  })

  it('trims content before creating a comment', async () => {
    const wrapper = mountCommentForm({ parentId: 20 })

    await wrapper.get('textarea').setValue('  reply body  ')
    await wrapper.get('form').trigger('submit')

    expect(createComment).toHaveBeenCalledWith(
      {
        postId: 10,
        data: {
          content: 'reply body',
          parentId: 20,
        },
      },
      expect.any(Object),
    )
  })

  it('adds mentioned user ids when a mention candidate is selected', async () => {
    apiMocks.getMentionCandidates.mockResolvedValueOnce({
      data: {
        success: true,
        data: [
          { userId: 7, displayName: 'Alice', profileImageUrl: null },
        ],
      },
    })
    const wrapper = mountCommentForm()
    const textarea = wrapper.get('textarea')

    await textarea.setValue('hello @al')
    const textareaElement = textarea.element as HTMLTextAreaElement
    textareaElement.setSelectionRange(textareaElement.value.length, textareaElement.value.length)
    await textarea.trigger('keyup')
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(apiMocks.getMentionCandidates).toHaveBeenCalledWith('al')

    await wrapper.get('.mention-suggestion-item').trigger('mousedown')
    await flushPromises()
    await wrapper.get('form').trigger('submit')

    expect(createComment).toHaveBeenCalledWith(
      {
        postId: 10,
        data: {
          content: 'hello @Alice',
          parentId: null,
          mentionedUserIds: [7],
        },
      },
      expect.any(Object),
    )
  })

  it('trims content before updating a comment', async () => {
    const wrapper = mountCommentForm({
      commentId: 30,
      initialContent: ' before ',
    })

    await wrapper.get('textarea').setValue('  updated body  ')
    await wrapper.get('form').trigger('submit')

    expect(updateComment).toHaveBeenCalledWith(
      {
        commentId: 30,
        postId: 10,
        data: {
          content: 'updated body',
        },
      },
      expect.any(Object),
    )
  })

  it('keeps existing mention ids when editing leaves mention text in place', async () => {
    const wrapper = mountCommentForm({
      commentId: 30,
      initialContent: 'hello @Alice',
      initialMentions: [
        { userId: 7, displayName: 'Alice', profileImageUrl: null },
      ],
    })

    await wrapper.get('textarea').setValue('  hello @Alice updated  ')
    await wrapper.get('form').trigger('submit')

    expect(updateComment).toHaveBeenCalledWith(
      {
        commentId: 30,
        postId: 10,
        data: {
          content: 'hello @Alice updated',
          mentionedUserIds: [7],
        },
      },
      expect.any(Object),
    )
  })

  it('sends an empty mention id list when editing removes existing mention text', async () => {
    const wrapper = mountCommentForm({
      commentId: 30,
      initialContent: 'hello @Alice',
      initialMentions: [
        { userId: 7, displayName: 'Alice', profileImageUrl: null },
      ],
    })

    await wrapper.get('textarea').setValue('hello updated')
    await wrapper.get('form').trigger('submit')

    expect(updateComment).toHaveBeenCalledWith(
      {
        commentId: 30,
        postId: 10,
        data: {
          content: 'hello updated',
          mentionedUserIds: [],
        },
      },
      expect.any(Object),
    )
  })

  it('shows one local error toast when create fails before global error handling', async () => {
    const wrapper = mountCommentForm()

    await wrapper.get('textarea').setValue('comment body')
    await wrapper.get('form').trigger('submit')

    const options = createComment.mock.calls[0]?.[1]
    options.onError(new Error('failed'))

    expect(addToast).toHaveBeenCalledWith('comment.saveFailed', 'error')
  })

  it('skips local error toast when the global error handler already handled it', async () => {
    const wrapper = mountCommentForm({ commentId: 30, initialContent: 'before' })

    await wrapper.get('textarea').setValue('updated body')
    await wrapper.get('form').trigger('submit')

    const options = updateComment.mock.calls[0]?.[1]
    options.onError({ suppressGlobalErrorToast: true })

    expect(addToast).not.toHaveBeenCalled()
  })
})
