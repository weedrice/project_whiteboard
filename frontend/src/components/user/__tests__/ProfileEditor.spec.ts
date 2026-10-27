import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import ProfileEditor from '../ProfileEditor.vue'
import { IMAGE_UPLOAD_ACCEPT } from '@/utils/imageUploadPolicy'
import { getExposedVm } from '@/test/vue-test-helpers'

type ProfileEditorExposed = {
  requestClose: () => Promise<unknown> | undefined
  selectedFile: File | null
  removeProfileImage: boolean
}

const mocks = vi.hoisted(() => ({
  agentsData: { value: { agents: [] as Array<Record<string, unknown>> }, __v_isRef: true },
  isAgentsLoading: { value: false, __v_isRef: true },
  isAgentsFetching: { value: false, __v_isRef: true },
  isAgentsError: { value: false, __v_isRef: true },
  refetchAgents: vi.fn(),
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
  authStore: null as unknown as {
    sessionGeneration: number
    user: Record<string, unknown>
    fetchUser: () => Promise<unknown>
    logout: () => Promise<unknown>
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

vi.mock('@/features/user/useUser', () => ({
  useUser: () => ({
    useDeleteAccount: () => ({
      mutateAsync: mocks.deleteAccount,
      isPending: { value: false },
    }),
    useMyAgents: () => ({
      data: mocks.agentsData,
      isLoading: mocks.isAgentsLoading,
      isFetching: mocks.isAgentsFetching,
      isError: mocks.isAgentsError,
      refetch: mocks.refetchAgents,
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

vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  mocks.authStore = reactive({
    sessionGeneration: 1,
    user: mocks.user,
    fetchUser: mocks.fetchUser,
    logout: mocks.logout,
  })
  return { useAuthStore: () => mocks.authStore }
})

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
  isConcurrentModificationError: vi.fn(() => false),
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
    mocks.isAgentsError.value = false
    mocks.isClaiming.value = false
    mocks.user.isEmailVerified = true
    mocks.user.userId = 1
    mocks.user.displayName = 'tester'
    mocks.user.profileImageUrl = ''
    mocks.authStore.sessionGeneration = 1
    mocks.authStore.user = mocks.user
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

  it('preserves changed names on cancelled dismissal and closes after approval', async () => {
    const wrapper = mountProfileEditor()
    await wrapper.get('#profile-display-name').setValue('New name')
    mocks.confirm.mockResolvedValueOnce(false)
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.get<HTMLInputElement>('#profile-display-name').element.value).toBe('New name')
    expect(mocks.confirm).toHaveBeenCalledWith(
      'user.profile.discardConfirm', undefined, undefined, undefined,
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('closes without confirmation when a name edit is reverted', async () => {
    const wrapper = mountProfileEditor()
    await wrapper.get('#profile-display-name').setValue('Temporary name')
    await wrapper.get('#profile-display-name').setValue('tester')
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('protects an unsaved photo even when the name has not changed', async () => {
    const wrapper = mountProfileEditor()
    getExposedVm<ProfileEditorExposed>(wrapper).selectedFile = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    mocks.confirm.mockResolvedValueOnce(false)
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(mocks.confirm).toHaveBeenCalled()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(getExposedVm<ProfileEditorExposed>(wrapper).selectedFile).not.toBeNull()
    wrapper.unmount()
  })

  it('protects removal of an existing profile photo', async () => {
    mocks.user.profileImageUrl = 'https://example.com/profile.png'
    const wrapper = mountProfileEditor()
    await findButtonByText(wrapper, 'user.profile.removePhoto').trigger('click')
    mocks.confirm.mockResolvedValueOnce(false)
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(mocks.confirm).toHaveBeenCalled()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(getExposedVm<ProfileEditorExposed>(wrapper).removeProfileImage).toBe(true)
    wrapper.unmount()
  })

  it('locks submitted inputs but preserves closing during a pending save', async () => {
    let finish!: (value: { success: boolean }) => void
    mocks.updateProfile.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
    const wrapper = mountProfileEditor()
    await wrapper.get('#profile-display-name').setValue('Saved name')
    await findButtonByText(wrapper, 'common.save').trigger('click')
    await flushPromises()
    expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined()
    expect(wrapper.get('fieldset').attributes('inert')).toBeDefined()
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(mocks.confirm).not.toHaveBeenCalled()
    wrapper.unmount()
    finish({ success: true })
    await flushPromises()
  })

  it('restores editability and draft protection after save failure', async () => {
    mocks.updateProfile.mockRejectedValueOnce(new Error('Save failed'))
    const wrapper = mountProfileEditor()
    await wrapper.get('#profile-display-name').setValue('Unsaved name')
    await findButtonByText(wrapper, 'common.save').trigger('click')
    await flushPromises()
    expect(wrapper.get('fieldset').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('fieldset').attributes('inert')).toBeUndefined()
    mocks.confirm.mockResolvedValueOnce(false)
    await getExposedVm<ProfileEditorExposed>(wrapper).requestClose()
    expect(mocks.confirm).toHaveBeenCalled()
    expect(wrapper.emitted('close')).toBeUndefined()
    wrapper.unmount()
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
    expect(wrapper.findAll('button[aria-label="user.profile.choosePhoto"]')).toHaveLength(1)
    expect(wrapper.get('#profile-photo-input').attributes()).toMatchObject({
      name: 'profileImage',
      accept: IMAGE_UPLOAD_ACCEPT,
      'aria-label': 'user.profile.choosePhoto',
    })
    expect(wrapper.get('#profile-photo-input').classes()).toContain('hidden')
    expect(wrapper.get('#profile-photo-input').classes()).not.toContain('sr-only')
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
    getExposedVm<ProfileEditorExposed>(wrapper).selectedFile = new File(['avatar'], 'avatar.png', {
      type: 'image/png',
    })

    await findButtonByText(wrapper, 'common.save').trigger('click')
    await flushPromises()

    expect(mocks.uploadFile).toHaveBeenCalled()
    expect(mocks.updateProfile).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('common.messages.uploadFailed', 'error')
  })

  it('trims display name before updating the profile', async () => {
    const wrapper = mountProfileEditor()

    await wrapper.find('input[placeholder="user.profile.displayNamePlaceholder"]').setValue('  Updated Name  ')
    await findButtonByText(wrapper, 'common.save').trigger('click')
    await flushPromises()

    expect(mocks.updateProfile).toHaveBeenCalledWith({
      displayName: 'Updated Name',
      profileImageId: null,
    })
  })

  it('discards the profile draft when the authenticated account changes', async () => {
    const wrapper = mountProfileEditor()
    const vm = getExposedVm<ProfileEditorExposed>(wrapper)
    await wrapper.find('input[placeholder="user.profile.displayNamePlaceholder"]').setValue('Draft Name')
    vm.selectedFile = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    vm.removeProfileImage = true

    mocks.authStore.sessionGeneration += 1
    mocks.authStore.user = {
      ...mocks.user,
      userId: 2,
      displayName: 'next-user',
      profileImageUrl: 'https://example.com/next.png',
    }
    await nextTick()

    expect(wrapper.find('input[placeholder="user.profile.displayNamePlaceholder"]').element).toHaveProperty('value', 'next-user')
    expect(vm.selectedFile).toBeNull()
    expect(vm.removeProfileImage).toBe(false)
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

  it('blocks agent actions and retries when the agent list fails', async () => {
    mocks.isAgentsError.value = true
    const wrapper = mountProfileEditor()

    expect(wrapper.find('[role="alert"]').text()).toContain('common.messages.loadFailed')
    expect(wrapper.find('input[placeholder="user.profile.agentPlaceholder"]').exists()).toBe(false)
    await findButtonByText(wrapper, 'common.error.retry').trigger('click')
    expect(mocks.refetchAgents).toHaveBeenCalled()
  })
})
