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

  it('locks comment editing and cancellation while a create request is pending', async () => {
    const wrapper = mountCommentForm({ parentId: 20 })
    isCreating.value = true
    await wrapper.vm.$nextTick()

    expect(wrapper.get('form').attributes()).toMatchObject({
      inert: 'true',
      'aria-busy': 'true',
    })
    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('closes transient mention and emoticon controls when submission starts', async () => {
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

    await wrapper.get('button[aria-label="board.writePost.toolbar.emoticon"]').trigger('click')
    await textarea.setValue('hello @al')
    const textareaElement = textarea.element as HTMLTextAreaElement
    textareaElement.setSelectionRange(textareaElement.value.length, textareaElement.value.length)
    await textarea.trigger('keyup')
    await flushPromises()
    expect(textarea.attributes('aria-expanded')).toBe('true')

    await wrapper.get('form').trigger('submit')

    expect(textarea.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('button[aria-label="board.writePost.toolbar.emoticon"]').attributes('aria-pressed')).toBe('false')
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

  it.each([null, 20])('keeps text, mentions and caret after sending an emoticon (parent %s)', async (parentId) => {
    const wrapper = mountCommentForm({
      parentId,
      initialContent: 'hello @Alice',
      initialMentions: [{ userId: 7, displayName: 'Alice', profileImageUrl: null }],
    })
    document.body.appendChild(wrapper.element)
    const textarea = wrapper.get('textarea').element
    textarea.focus()
    textarea.setSelectionRange(3, 3)
    wrapper.getComponent({ name: 'EmoticonPicker' }).vm.$emit('select', { imageUrl: '/sticker.png' })
    isCreating.value = true
    await wrapper.vm.$nextTick()
    expect(createComment.mock.calls[0]?.[0].data).toEqual({ content: '![emoticon](/sticker.png)', parentId })
    createComment.mock.calls[0]?.[1].onSuccess({ data: { success: true, data: { commentId: 44 } } })
    isCreating.value = false
    await flushPromises()
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(wrapper.emitted('emoticon-success')).toHaveLength(1)
    expect(textarea.value).toBe('hello @Alice')
    expect(document.activeElement).toBe(textarea)
    expect(textarea.selectionStart).toBe(3)
    await wrapper.get('form').trigger('submit')
    expect(createComment.mock.calls[1]?.[0].data).toEqual({ content: 'hello @Alice', parentId, mentionedUserIds: [7] })
    createComment.mock.calls[1]?.[1].onSuccess({ data: { success: true, data: { commentId: 45 } } })
    await wrapper.vm.$nextTick()
    expect(textarea.value).toBe('')
    expect(wrapper.emitted('success')).toHaveLength(1)
    wrapper.unmount()
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

    expect(apiMocks.getMentionCandidates).toHaveBeenCalledWith('al', expect.objectContaining({
      signal: expect.any(AbortSignal),
    }))

    expect(textarea.attributes()).toMatchObject({
      role: 'combobox',
      'aria-autocomplete': 'list',
      'aria-haspopup': 'listbox',
      'aria-expanded': 'true',
      'aria-controls': 'comment-new-10-mention-listbox',
      'aria-activedescendant': 'comment-new-10-mention-listbox-option-7',
    })

    await wrapper.get('.mention-suggestion-item').trigger('click')
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

  it('clears the comment draft and mention state when the post identity changes', async () => {
    const wrapper = mountCommentForm({
      initialContent: 'hello @Alice',
      initialMentions: [
        { userId: 7, displayName: 'Alice', profileImageUrl: null },
      ],
    })

    await wrapper.get('textarea').setValue('draft for the previous post @Alice')
    await wrapper.setProps({ postId: 11, initialContent: '', initialMentions: [] })

    expect(wrapper.get('textarea').element).toHaveProperty('value', '')

    await wrapper.get('textarea').setValue('new post text @Alice')
    await wrapper.get('form').trigger('submit')

    expect(createComment).toHaveBeenCalledWith(
      {
        postId: 11,
        data: {
          content: 'new post text @Alice',
          parentId: null,
        },
      },
      expect.any(Object),
    )
  })

  it('ignores a previous post response after the form identity changes', async () => {
    const wrapper = mountCommentForm()
    await wrapper.get('textarea').setValue('previous post comment')
    await wrapper.get('form').trigger('submit')
    const previousCallbacks = createComment.mock.calls[0]?.[1]

    await wrapper.setProps({ postId: 11, initialContent: '', initialMentions: [] })
    await wrapper.get('textarea').setValue('new post draft')
    previousCallbacks.onSuccess({ data: { success: true, data: { earnedPoints: 5 } } })

    expect(wrapper.get('textarea').element).toHaveProperty('value', 'new post draft')
    expect(addToast).not.toHaveBeenCalled()
    expect(wrapper.emitted('success')).toBeUndefined()
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

  it.each([
    { mode: 'creating', commentId: null, initialMentions: [], prefix: 'hello ', expectedIds: [8] },
    { mode: 'editing', commentId: 30, initialMentions: [], prefix: 'hello ', expectedIds: [8] },
    {
      mode: 'editing with an existing mention',
      commentId: 30,
      initialMentions: [{ userId: 7, displayName: 'Alice', profileImageUrl: null }],
      prefix: 'hello @Alice ',
      expectedIds: [7, 8],
    },
  ])('selects and submits a new mention while $mode', async ({ commentId, initialMentions, prefix, expectedIds }) => {
    apiMocks.getMentionCandidates.mockResolvedValue({
      data: {
        success: true,
        data: [{ userId: 8, displayName: 'Bob', profileImageUrl: null }],
      },
    })
    const wrapper = mountCommentForm({ commentId, initialContent: prefix, initialMentions })
    const textarea = wrapper.get('textarea')
    const typedContent = `${prefix}@Bo`

    await textarea.setValue(typedContent)
    textarea.element.setSelectionRange(typedContent.length, typedContent.length)
    await textarea.trigger('keyup', { key: 'o' })
    await flushPromises()

    expect(apiMocks.getMentionCandidates).toHaveBeenCalledWith('Bo', expect.any(Object))
    await wrapper.get('[role="option"]').trigger('click')
    await flushPromises()
    expect(textarea.element.value).toBe(`${prefix}@Bob `)
    await wrapper.get('form').trigger('submit')

    const data = { content: `${prefix}@Bob`, mentionedUserIds: expectedIds }
    const mutation = commentId ? updateComment : createComment
    const otherMutation = commentId ? createComment : updateComment
    const variables = commentId
      ? { commentId, postId: 10, data }
      : { postId: 10, data: { ...data, parentId: null } }
    expect(mutation).toHaveBeenCalledWith(variables, expect.any(Object))
    expect(otherMutation).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each(['new', 'reply', 'edit'] as const)('replaces removed mentions after selecting ten users in a %s comment', async mode => {
    const previousMentions = Array.from({ length: 10 }, (_, index) => ({
      userId: index + 1, displayName: `Old${index}`, profileImageUrl: null,
    }))
    const wrapper = mountCommentForm(mode === 'edit'
      ? { commentId: 30, initialContent: previousMentions.map(user => `@${user.displayName}`).join(' '), initialMentions: previousMentions }
      : mode === 'reply' ? { parentId: 20 } : {})
    const textarea = wrapper.get('textarea')
    const selectCandidate = async (displayName: string, userId: number, text: string) => {
      apiMocks.getMentionCandidates.mockResolvedValue({ data: { success: true, data: [{ userId, displayName, profileImageUrl: null }] } })
      await textarea.setValue(text)
      textarea.element.setSelectionRange(text.length, text.length)
      await textarea.trigger('keyup')
      await flushPromises()
      await wrapper.get('[role="option"]').trigger('click')
      await flushPromises()
    }
    if (mode !== 'edit') {
      for (const user of previousMentions) {
        await selectCandidate(user.displayName, user.userId, `${textarea.element.value}@${user.displayName}`)
      }
    }
    await selectCandidate('Bob', 99, '@Old0 @Bo')
    expect(textarea.element.value).toBe('@Old0 @Bob ')
    await wrapper.get('form').trigger('submit')
    const mutation = mode === 'edit' ? updateComment : createComment
    expect(mutation).toHaveBeenCalledWith(expect.objectContaining({
      postId: 10,
      data: expect.objectContaining({ content: '@Old0 @Bob', mentionedUserIds: [1, 99] }),
    }), expect.any(Object))
  })

  it('keeps the ten-user limit and does not infer ids from plain text', async () => {
    const mentions = Array.from({ length: 10 }, (_, index) => ({
      userId: index + 1, displayName: `Old${index}`, profileImageUrl: null,
    }))
    const content = `${mentions.map(user => `@${user.displayName}`).join(' ')} @Unselected`
    const wrapper = mountCommentForm({ commentId: 30, initialContent: content, initialMentions: mentions })
    const textarea = wrapper.get('textarea')
    apiMocks.getMentionCandidates.mockResolvedValue({ data: { success: true, data: [{ userId: 99, displayName: 'Bob' }] } })
    const typed = `${content} @Bo`
    await textarea.setValue(typed)
    textarea.element.setSelectionRange(typed.length, typed.length)
    await textarea.trigger('keyup')
    await flushPromises()
    await wrapper.get('[role="option"]').trigger('click')
    await flushPromises()
    await wrapper.get('form').trigger('submit')
    expect(updateComment).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ mentionedUserIds: mentions.map(user => user.userId) }),
    }), expect.any(Object))
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
