import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import PostForm from '../PostForm.vue'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: vi.fn(),
}))

vi.mock('@/composables/usePost', () => ({
  usePost: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: vi.fn(),
}))

vi.mock('@/api', () => ({ default: { post: vi.fn() } }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn() } }))

const boardRef = ref({
  allowNsfw: false,
  isAdmin: false,
  categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
})
const isBoardLoadingRef = ref(false)
const isCreatePendingRef = ref(false)
const isDeleteDraftPendingRef = ref(false)
const isSaveDraftPendingRef = ref(false)

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: {
    id: { type: String, default: '' },
    modelValue: { type: String, default: '' },
    type: { type: String, default: 'text' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        id: props.id,
        type: props.type,
        value: props.modelValue,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      })
  },
})

const BaseSelectStub = defineComponent({
  name: 'BaseSelect',
  props: {
    id: { type: String, default: '' },
    label: { type: String, default: '' },
    modelValue: { type: [String, Number], default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h('div', [
        props.label ? h('label', { for: props.id }, props.label) : null,
        h(
          'select',
          {
            id: props.id,
            value: props.modelValue as string | number,
            onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
          },
          slots.default?.(),
        ),
      ])
  },
})

const BaseCheckboxStub = defineComponent({
  name: 'BaseCheckbox',
  props: {
    id: { type: String, default: '' },
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        id: props.id,
        type: 'checkbox',
        checked: props.modelValue,
        onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).checked),
      })
  },
})

const PostTagsStub = defineComponent({
  name: 'PostTags',
  props: {
    inputId: { type: String, default: 'post-tags-input' },
  },
  setup(props) {
    return () =>
      h('div', [
        h('label', { for: props.inputId }, 'tags'),
        h('input', { id: props.inputId }),
      ])
  },
})

const stubs = {
  BaseInput: BaseInputStub,
  BaseSelect: BaseSelectStub,
  BaseCheckbox: BaseCheckboxStub,
  BaseButton: { template: '<button :type="type || \'button\'"><slot /></button>', props: ['type'] },
  BaseModal: { template: '<section v-if="isOpen"><slot /><slot name="footer" /></section>', props: ['isOpen'] },
  BaseSegmentedControl: { template: '<div data-testid="segmented-control" />' },
  BaseSpinner: { template: '<div />' },
  EmoticonPicker: { template: '<div />' },
  PostEditorTipTap: { template: '<textarea />' },
  PostTags: PostTagsStub,
  Teleport: true,
}

const mountPostForm = (props: Partial<InstanceType<typeof PostForm>['$props']> = {}) => mount(PostForm, {
  props: {
    mode: 'create',
    boardUrl: 'free',
    postId: '1',
    ...props,
  },
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs,
  },
})

describe('PostForm metadata layout', () => {
  beforeEach(() => {
    vi.clearAllMocks()

    vi.mocked(useAuthStore).mockReturnValue({ user: { role: 'USER' } } as never)
    vi.mocked(useToastStore).mockReturnValue({ addToast: vi.fn() } as never)
    vi.mocked(useBoard).mockReturnValue({
      useBoardDetail: () => ({ data: boardRef, isLoading: isBoardLoadingRef }),
    } as never)
    vi.mocked(usePost).mockReturnValue({
      usePostDetail: () => ({ data: ref(null), isLoading: ref(false) }),
      useCreatePost: () => ({ mutate: vi.fn(), isPending: isCreatePendingRef }),
      useUpdatePost: () => ({ mutate: vi.fn(), isPending: ref(false) }),
      useSaveDraft: () => ({ isPending: isSaveDraftPendingRef, mutateAsync: vi.fn() }),
      useDeleteDraft: () => ({ isPending: isDeleteDraftPendingRef, mutateAsync: vi.fn() }),
    } as never)
  })

  it('renders the desktop metadata cards for the compose shell', () => {
    const wrapper = mountPostForm()

    expect(wrapper.findAll('.nv-compose-side-card').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.find('aside').classes()).toContain('lg:sticky')
  })

  it('renders create and edit titles', () => {
    const createWrapper = mountPostForm({ mode: 'create' })
    const editWrapper = mountPostForm({ mode: 'edit' })

    expect(createWrapper.text()).toContain('board.writePost.createTitle')
    expect(editWrapper.text()).toContain('board.writePost.editTitle')
  })

  it('renders overridden create title when provided', () => {
    const wrapper = mountPostForm({
      mode: 'create',
      createTitleOverride: '문의 작성',
    })

    expect(wrapper.text()).toContain('문의 작성')
  })

  it('hides board label and preview action when configured', () => {
    const wrapper = mountPostForm({
      hideBoardLabel: true,
      hidePreview: true,
    })

    expect(wrapper.text()).not.toContain('free')
    expect(wrapper.text()).not.toContain('board.writePost.actions.preview')
  })

  it('connects mobile and desktop tag labels to unique inputs', () => {
    const wrapper = mountPostForm()
    const mobileTagInput = wrapper.get('#post-tags-input-mobile')
    const desktopTagInput = wrapper.get('#post-tags-input-desktop')

    expect(wrapper.findAll('#post-tags-input-mobile')).toHaveLength(1)
    expect(wrapper.findAll('#post-tags-input-desktop')).toHaveLength(1)
    expect(wrapper.get('label[for="post-tags-input-mobile"]').attributes('for')).toBe('post-tags-input-mobile')
    expect(wrapper.get('label[for="post-tags-input-desktop"]').attributes('for')).toBe('post-tags-input-desktop')
    expect(mobileTagInput.attributes('id')).toBe('post-tags-input-mobile')
    expect(desktopTagInput.attributes('id')).toBe('post-tags-input-desktop')
  })

  it('connects mobile and desktop category labels to unique selects', () => {
    const wrapper = mountPostForm()
    const mobileCategorySelect = wrapper.get('#category-mobile')
    const desktopCategorySelect = wrapper.get('#category')

    expect(wrapper.findAll('#category-mobile')).toHaveLength(1)
    expect(wrapper.findAll('#category')).toHaveLength(1)
    expect(wrapper.get('label[for="category-mobile"]').text()).toBe('common.category')
    expect(wrapper.get('label[for="category"]').text()).toBe('common.category')
    expect(mobileCategorySelect.element.tagName).toBe('SELECT')
    expect(desktopCategorySelect.element.tagName).toBe('SELECT')
  })
})
