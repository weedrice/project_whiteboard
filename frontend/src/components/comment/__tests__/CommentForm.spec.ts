import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CommentForm from '../CommentForm.vue'

const createComment = vi.fn()
const updateComment = vi.fn()
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

vi.mock('@/composables/useComment', () => ({
  useComment: () => ({
    useCreateComment: () => ({ mutate: createComment, isPending: isCreating }),
    useUpdateComment: () => ({ mutate: updateComment, isPending: isUpdating }),
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}))

vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn() },
}))

const BaseTextareaStub = defineComponent({
  name: 'BaseTextarea',
  props: {
    modelValue: { type: String, default: '' },
    id: { type: String, default: '' },
    name: { type: String, default: '' },
    label: { type: String, default: '' },
    hideLabel: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('div', [
        props.label ? h('label', { for: props.id, class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
        h('textarea', {
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
        data: {
          content: 'updated body',
        },
      },
      expect.any(Object),
    )
  })
})
