import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import MyPageDashboard from '../MyPageDashboard.vue'

const mocks = vi.hoisted(() => ({
  profile: {
    __v_isRef: true,
    value: {
    displayName: 'Tester',
    email: 'tester@example.com',
    isEmailVerified: false,
    createdAt: '2026-01-01T00:00:00',
    lastLoginAt: '2026-01-02T00:00:00',
    },
  },
  isVerifyModalOpen: { __v_isRef: true, value: true },
  emailVerification: {
    email: 'tester@example.com',
    code: '123456',
    isCodeSent: true,
    loading: false,
    resendCooldown: 0,
    timeLeft: 120,
  },
  myPostsError: { __v_isRef: true, value: '' },
  myCommentsError: { __v_isRef: true, value: '' },
  isInquiryDetailOpen: { __v_isRef: true, value: false },
  selectedInquiryPost: { __v_isRef: true, value: null as null | { postId: number; title: string; contents: string; createdAt: string } },
}))

vi.mock('vue-i18n', () => ({
  createI18n: vi.fn(() => ({
    global: {
      t: (key: string) => key,
    },
  })),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/features/user/dashboard/useMyPageDashboardResource', () => ({
  useMyPageDashboardResource: () => ({
    profile: mocks.profile,
    myAgents: ref([]),
    myPosts: ref([]),
    myPostsTotalCount: ref(0),
    myPostsTotalPages: ref(0),
    myPostsCurrentPage: ref(0),
    myPostsSize: ref(15),
    myPostsSort: ref('LATEST'),
    myCommentItems: ref([]),
    myCommentsTotalCount: ref(0),
    myCommentsTotalPages: ref(0),
    myCommentsCurrentPage: ref(0),
    myCommentsSize: ref(15),
    isLoading: ref(false),
    error: ref(''),
    myPostsError: mocks.myPostsError,
    myCommentsError: mocks.myCommentsError,
    fetchMyProfile: vi.fn(),
    fetchMyAgents: vi.fn(),
    fetchMyPosts: vi.fn(),
    handleMyPostsPageChange: vi.fn(),
    handleMyPostsSortChange: vi.fn(),
    handleMyCommentsPageChange: vi.fn(),
    getAgentStatusLabel: vi.fn((status: string) => status),
    loadDashboard: vi.fn(),
  }),
}))

vi.mock('@/features/user/dashboard/useInquiryDetailModal', () => ({
  useInquiryDetailModal: () => ({
    isInquiryDetailOpen: mocks.isInquiryDetailOpen,
    selectedInquiryPost: mocks.selectedInquiryPost,
    isInquiryDetailLoading: ref(false),
    inquiryDetailError: ref(''),
    isDeletingInquiry: ref(false),
    isInquiryPostItem: vi.fn(() => false),
    openMyInquiryPost: vi.fn(),
    closeInquiryModal: vi.fn(),
    deleteInquiryPost: vi.fn(),
  }),
}))

vi.mock('@/composables/useEmailVerificationFlow', () => ({
  useEmailVerificationFlow: () => ({
    isVerifyModalOpen: mocks.isVerifyModalOpen,
    emailVerification: mocks.emailVerification,
    formatVerifyTime: vi.fn((seconds: number) => `${seconds}s`),
    isValidEmail: vi.fn((value: string) => value.includes('@')),
    openVerifyModal: vi.fn(),
    closeVerifyModal: vi.fn(),
    sendVerifyCode: vi.fn(),
    verifyEmailCode: vi.fn(),
  }),
}))

vi.mock('@/utils/image', () => ({
  getOptimizedProfileImageUrl: (value: string) => value,
  handleImageError: vi.fn(),
}))

vi.mock('@/utils/date', () => ({
  formatDate: (value: string) => value,
}))

vi.mock('@/utils/commentContent', () => ({
  renderCommentContentHtml: (value: string) => value,
}))

vi.mock('@/utils/imageFallback', () => ({
  applyImageFallback: vi.fn(),
}))

const BaseInputStub = defineComponent({
  name: 'BaseInput',
  props: {
    modelValue: { type: String, default: '' },
    id: { type: String, default: '' },
    name: { type: String, default: '' },
    label: { type: String, default: '' },
    autocomplete: { type: String, default: '' },
    inputmode: { type: String, default: '' },
    hideLabel: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('div', [
        props.label ? h('label', { for: props.id, class: props.hideLabel ? 'sr-only' : '' }, props.label) : null,
        h('input', {
          id: props.id,
          name: props.name,
          autocomplete: props.autocomplete,
          inputmode: props.inputmode,
          disabled: props.disabled,
          value: props.modelValue,
          onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
        }),
      ])
  },
})

function mountDashboard() {
  return mount(MyPageDashboard, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        BaseButton: { template: '<button type="button"><slot /></button>' },
        BaseInput: BaseInputStub,
        BaseModal: { props: ['isOpen'], template: '<section v-if="isOpen"><slot /><slot name="footer" /></section>' },
        BaseSkeleton: true,
        MyPageSummaryCards: true,
        CommentList: true,
        EmptyState: { template: '<div data-testid="empty-state"><slot /></div>' },
        Pagination: true,
        PostList: true,
        ProfileEditor: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('MyPageDashboard', () => {
  beforeEach(() => {
    mocks.myPostsError.value = ''
    mocks.myCommentsError.value = ''
    mocks.isVerifyModalOpen.value = true
    mocks.isInquiryDetailOpen.value = false
    mocks.selectedInquiryPost.value = null
  })

  it('renders profile information rows and email verification action', () => {
    const wrapper = mountDashboard()

    expect(wrapper.text()).toContain('user.profile.displayName')
    expect(wrapper.text()).toContain('Tester')
    expect(wrapper.text()).toContain('user.profile.email')
    expect(wrapper.text()).toContain('tester@example.com')
    expect(wrapper.text()).toContain('user.profile.notVerified')
    expect(wrapper.text()).toContain('user.profile.agentCode')
    expect(wrapper.text()).toContain('user.profile.agentEmpty')
    expect(wrapper.text()).toContain('user.profile.joined')
    expect(wrapper.text()).toContain('2026-01-01T00:00:00')
    expect(wrapper.text()).toContain('user.profile.lastLogin')
    expect(wrapper.text()).toContain('2026-01-02T00:00:00')
  })

  it('labels email verification inputs for the modal flow', () => {
    const wrapper = mountDashboard()

    expect(wrapper.get('label[for="email-verification-email"]').text()).toBe('user.profile.email')
    expect(wrapper.get('#email-verification-email').attributes()).toMatchObject({
      name: 'emailVerificationEmail',
      autocomplete: 'email',
    })
    expect(wrapper.get('label[for="email-verification-code"]').text()).toBe('auth.codePlaceholder')
    expect(wrapper.get('#email-verification-code').attributes()).toMatchObject({
      name: 'emailVerificationCode',
      inputmode: 'numeric',
      autocomplete: 'one-time-code',
    })
  })

  it('does not render the my posts empty state while the posts error is shown', () => {
    mocks.myPostsError.value = 'posts failed'
    const wrapper = mountDashboard()

    expect(wrapper.text()).toContain('posts failed')
    expect(wrapper.findAll('[data-testid="empty-state"]')).toHaveLength(1)
  })

  it('does not render the my comments empty state while the comments error is shown', () => {
    mocks.myCommentsError.value = 'comments failed'
    const wrapper = mountDashboard()

    expect(wrapper.text()).toContain('comments failed')
    expect(wrapper.findAll('[data-testid="empty-state"]')).toHaveLength(1)
  })

  it('renders inquiry detail widget html through the shared post content view', () => {
    mocks.isInquiryDetailOpen.value = true
    mocks.selectedInquiryPost.value = {
      postId: 9,
      title: 'Inquiry title',
      contents: '<p>Unsafe</p><script>alert(1)</script>',
      createdAt: '2026-01-03T00:00:00',
    }

    const wrapper = mountDashboard()

    const frame = wrapper.get('iframe')
    expect(frame.attributes('sandbox')).toBe('allow-scripts')
    expect(frame.attributes('srcdoc')).toContain('<p>Unsafe</p><script>alert(1)</script>')
  })
})
