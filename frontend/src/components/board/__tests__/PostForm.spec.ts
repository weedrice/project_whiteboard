import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, computed } from 'vue'
import PostForm from '../PostForm.vue'

const mockPush = vi.fn()
const mockBack = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { boardUrl: 'free', postId: '1' } }),
  useRouter: () => ({ push: mockPush, back: mockBack })
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key })
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: () => ({ data: ref({ allowNsfw: false }), isLoading: ref(false) }),
    useBoardCategories: () => ({ data: ref([]), isLoading: ref(false) })
  })
}))

vi.mock('@/composables/usePost', () => ({
  usePost: () => ({
    usePostDetail: () => ({ data: ref(null), isLoading: ref(false) }),
    useCreatePost: () => ({ mutate: vi.fn(), isPending: ref(false) }),
    useUpdatePost: () => ({ mutate: vi.fn(), isPending: ref(false) })
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { role: 'USER' } })
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: vi.fn() })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: vi.fn().mockResolvedValue(false) })
}))

vi.mock('@vueup/vue-quill', () => ({
  QuillEditor: { template: '<div class="quill-editor-stub"></div>' },
  Quill: {}
}))

vi.mock('@/utils/emoticon-blot', () => ({ registerEmoticonBlot: vi.fn() }))

vi.mock('@/api', () => ({ default: { post: vi.fn() } }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn() } }))

const globalMountOptions = {
  global: {
    mocks: { $t: (key: string) => key },
    stubs: {
      BaseInput: true,
      BaseButton: true,
      BaseSelect: true,
      BaseCheckbox: true,
      BaseSpinner: true,
      PostTags: true,
      EmoticonPicker: true
    }
  }
}

describe('PostForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders create title when mode is create', () => {
    const wrapper = mount(PostForm, {
      props: { mode: 'create' },
      ...globalMountOptions
    })
    expect(wrapper.text()).toContain('board.writePost.createTitle')
  })

  it('renders edit title when mode is edit', () => {
    const wrapper = mount(PostForm, {
      props: { mode: 'edit' },
      ...globalMountOptions
    })
    expect(wrapper.text()).toContain('board.writePost.editTitle')
  })

  it('accepts mode prop create and edit', () => {
    const createWrapper = mount(PostForm, {
      props: { mode: 'create' },
      ...globalMountOptions
    })
    const editWrapper = mount(PostForm, {
      props: { mode: 'edit' },
      ...globalMountOptions
    })
    expect(createWrapper.props('mode')).toBe('create')
    expect(editWrapper.props('mode')).toBe('edit')
  })
})
