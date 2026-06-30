import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileEditor from '../ProfileEditor.vue'
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'

const mocks = vi.hoisted(() => ({
  agentsData: { value: { agents: [] as Array<Record<string, unknown>> }, __v_isRef: true },
  isAgentsLoading: { value: false, __v_isRef: true },
  isAgentsFetching: { value: false, __v_isRef: true },
  isClaiming: { value: false, __v_isRef: true },
  claimAgent: vi.fn(),
  suspendMyAgent: vi.fn(),
  activateMyAgent: vi.fn(),
  updateProfile: vi.fn(),
  uploadFile: vi.fn(),
  deleteAccount: vi.fn(),
  confirm: vi.fn(),
  addToast: vi.fn(),
  fetchUser: vi.fn(),
  logout: vi.fn(),
  user: {
    userId: 1,
    displayName: 'tester',
    isEmailVerified: true,
    profileImageUrl: '',
  } as Record<string, unknown>,
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

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
  }),
}))

vi.mock('@/composables/useUser', () => ({
  useUser: () => ({
    useDeleteAccount: () => ({
      mutateAsync: mocks.deleteAccount,
      isPending: { value: false },
    }),
    useMyAgents: () => ({
      data: mocks.agentsData,
      isLoading: mocks.isAgentsLoading,
      isFetching: mocks.isAgentsFetching,
    }),
    useClaimAgent: () => ({
      mutateAsync: mocks.claimAgent,
      isPending: mocks.isClaiming,
    }),
    useSuspendMyAgent: () => ({
      mutateAsync: mocks.suspendMyAgent,
    }),
    useActivateMyAgent: () => ({
      mutateAsync: mocks.activateMyAgent,
    }),
    useUpdateMyProfile: () => ({
      mutateAsync: mocks.updateProfile,
    }),
  }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: mocks.user,
    fetchUser: mocks.fetchUser,
    logout: mocks.logout,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
  },
}))

vi.mock('@/utils/image', () => ({
  getOptimizedProfileImageUrl: (url: string) => url,
}))

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(() => ''),
  extractValidationErrors: vi.fn(() => null),
  getFieldError: vi.fn(() => ''),
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

const baseButtonStub = {
  props: {
    disabled: Boolean,
    loading: Boolean,
    type: String,
    variant: String,
  },
  emits: ['click'],
  template: '<button :type="type || \'button\'" :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
}

const baseInputStub = {
  props: {
    modelValue: String,
    id: String,
    name: String,
    label: String,
    autocomplete: String,
    disabled: Boolean,
    error: String,
    placeholder: String,
    hideLabel: Boolean,
  },
  emits: ['update:modelValue'],
  template: `
    <div>
      <label v-if="label" :for="id" :class="{ 'sr-only': hideLabel }">{{ label }}</label>
      <input
        :id="id"
        :name="name"
        :autocomplete="autocomplete"
        :value="modelValue"
        :disabled="disabled"
        :placeholder="placeholder"
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <span v-if="error">{{ error }}</span>
    </div>
  `,
}

const baseModalStub = {
  props: ['isOpen', 'title'],
  template: '<div v-if="isOpen"><slot /><slot name="footer" /></div>',
}

const mountProfileEditor = () => mount(ProfileEditor, {
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseButton: baseButtonStub,
      BaseInput: baseInputStub,
      BaseModal: baseModalStub,
    },
  },
})

const findButtonByText = (wrapper: ReturnType<typeof mount>, text: string) => {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (!button) {
    throw new Error(`Button not found: ${text}`)
  }
  return button
}

describe('ProfileEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.agentsData.value = { agents: [] }
    mocks.isAgentsLoading.value = false
    mocks.isAgentsFetching.value = false
    mocks.isClaiming.value = false
    mocks.user.isEmailVerified = true
    mocks.activateMyAgent.mockResolvedValue({ success: true })
    mocks.claimAgent.mockResolvedValue({ success: true })
    mocks.updateProfile.mockResolvedValue({ success: true })
    mocks.uploadFile.mockResolvedValue({
      data: {
        success: true,
        data: { fileId: 11 },
      },
    })
    mocks.confirm.mockResolvedValue(true)
  })

  it('shows the agent code registration field when the current agent is suspended', () => {
    mocks.agentsData.value = {
      agents: [
        {
          agentId: 7,
          name: 'Agent Seven',
          description: '',
          status: 'SUSPENDED',
          createdAt: '2026-04-22T10:00:00',
        },
      ],
    }

    const wrapper = mountProfileEditor()

    const input = wrapper.find('input[placeholder="user.profile.agentPlaceholder"]')
    const registerButton = findButtonByText(wrapper, 'user.profile.agentRegister')

    expect(input.exists()).toBe(true)
    expect(input.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('Agent Seven')
    expect(wrapper.text()).not.toContain('활성화')
    expect(registerButton.exists()).toBe(true)
  })

  it('requires email verification for agent claiming even when a suspended agent exists', () => {
    mocks.user.isEmailVerified = false
    mocks.agentsData.value = {
      agents: [
        {
          agentId: 7,
          name: 'Agent Seven',
          description: '',
          status: 'SUSPENDED',
          createdAt: '2026-04-22T10:00:00',
        },
      ],
    }

    const wrapper = mountProfileEditor()
    const registerButton = findButtonByText(wrapper, 'user.profile.agentRegister')

    expect(wrapper.text()).toContain('user.profile.agentEmailVerificationRequired')
    expect(registerButton.attributes('disabled')).toBeDefined()
  })

  it('keeps agent claiming disabled while the agent list is loading', () => {
    mocks.isAgentsLoading.value = true

    const wrapper = mountProfileEditor()
    const registerButton = findButtonByText(wrapper, 'user.profile.agentRegister')

    expect(registerButton.attributes('disabled')).toBeDefined()
  })

  it('labels the agent code input', () => {
    const wrapper = mountProfileEditor()

    expect(wrapper.get('label[for="agent-token"]').text()).toBe('user.profile.agentPlaceholder')
    expect(wrapper.get('#agent-token').attributes()).toMatchObject({
      name: 'agentToken',
      autocomplete: 'off',
    })
  })

  it('labels profile photo upload controls', () => {
    const wrapper = mountProfileEditor()

    expect(wrapper.get('button[aria-label="user.profile.choosePhoto"]').attributes('type')).toBe('button')
    expect(wrapper.get('#profile-photo-input').attributes()).toMatchObject({
      name: 'profileImage',
      accept: IMAGE_UPLOAD_ACCEPT,
      'aria-label': 'user.profile.choosePhoto',
    })
  })

  it('uses localized messages when suspending an active agent', async () => {
    mocks.agentsData.value = {
      agents: [
        {
          agentId: 7,
          name: 'Agent Seven',
          description: '',
          status: 'ACTIVE',
          createdAt: '2026-04-22T10:00:00',
        },
      ],
    }
    mocks.suspendMyAgent.mockResolvedValue({ success: true })

    const wrapper = mountProfileEditor()
    await findButtonByText(wrapper, 'user.profile.agentSuspend').trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith(
      'user.profile.agentSuspendConfirmMessage',
      'user.profile.agentSuspendConfirmTitle',
      'user.profile.agentSuspend',
      'common.cancel',
    )
    expect(mocks.suspendMyAgent).toHaveBeenCalledWith(7)
    expect(mocks.addToast).toHaveBeenCalledWith('user.profile.agentSuspendSuccess', 'success')
  })

  it('stops profile update when profile image upload envelope fails', async () => {
    mocks.uploadFile.mockResolvedValue({
      data: {
        success: false,
        data: null,
      },
    })
    const wrapper = mountProfileEditor()
    ;(wrapper.vm as unknown as { selectedFile: File }).selectedFile = new File(['avatar'], 'avatar.png', {
      type: 'image/png',
    })

    await findButtonByText(wrapper, 'common.save').trigger('click')
    await flushPromises()

    expect(mocks.uploadFile).toHaveBeenCalled()
    expect(mocks.updateProfile).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('common.messages.uploadFailed', 'error')
  })

  it('emits refreshed after a successful agent claim', async () => {
    const wrapper = mountProfileEditor()
    const input = wrapper.find('input[placeholder="user.profile.agentPlaceholder"]')

    await input.setValue('agent-token')
    await findButtonByText(wrapper, 'user.profile.agentRegister').trigger('click')
    await flushPromises()

    expect(mocks.claimAgent).toHaveBeenCalledWith('agent-token')
    expect(mocks.addToast).toHaveBeenCalledWith('user.profile.agentClaimSuccess', 'success')
    expect(wrapper.emitted('refreshed')).toHaveLength(1)
  })
})
