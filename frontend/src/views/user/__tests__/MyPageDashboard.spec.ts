import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
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

vi.mock('@/composables/useMyPageDashboardResource', () => ({
  useMyPageDashboardResource: () => ({
    profile: mocks.profile,
    myAgents: ref([]),
    myPosts: ref([]),
    myPostsTotalCount: ref(0),
    myPostsCurrentPage: ref(0),
    myPostsSize: ref(15),
    myPostsSort: ref('LATEST'),
    myCommentItems: ref([]),
    myCommentsTotalCount: ref(0),
    myCommentsCurrentPage: ref(0),
    myCommentsSize: ref(15),
    isLoading: ref(false),
    error: ref(''),
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

vi.mock('@/composables/useInquiryDetailModal', () => ({
  useInquiryDetailModal: () => ({
    isInquiryDetailOpen: ref(false),
    selectedInquiryPost: ref(null),
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

vi.mock('@/utils/postContentHtml', () => ({
  renderPostContentHtml: (value: string) => value,
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

describe('MyPageDashboard', () => {
  it('labels email verification inputs for the modal flow', () => {
    const wrapper = mount(MyPageDashboard, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
        stubs: {
          BaseButton: { template: '<button type="button"><slot /></button>' },
          BaseInput: BaseInputStub,
          BaseModal: { props: ['isOpen'], template: '<section v-if="isOpen"><slot /><slot name="footer" /></section>' },
          BaseSkeleton: true,
          CommentList: true,
          EmptyState: true,
          Pagination: true,
          PostList: true,
          ProfileEditor: true,
          RouterLink: { template: '<a><slot /></a>' },
        },
      },
    })

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
})
