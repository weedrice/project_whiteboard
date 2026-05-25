import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import BlockButton from '../BlockButton.vue'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  blockUser: vi.fn(),
  unblockUser: vi.fn(),
  addToast: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: mocks.confirm }),
}))

vi.mock('@/composables/useUser', () => ({
  useUser: () => ({
    useBlockUser: () => ({ mutateAsync: mocks.blockUser, isPending: ref(false) }),
    useUnblockUser: () => ({ mutateAsync: mocks.unblockUser, isPending: ref(false) }),
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

vi.mock('@/utils/logger', () => ({
  default: { error: mocks.loggerError },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const mountButton = (initialBlocked = false) => mount(BlockButton, {
  props: {
    userId: 42,
    initialBlocked,
  },
  global: {
    mocks: {
      $t: (key: string) => key,
    },
    stubs: {
      BaseButton: {
        props: ['disabled'],
        emits: ['click'],
        template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
      },
    },
  },
})

describe('BlockButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.blockUser.mockResolvedValue(undefined)
    mocks.unblockUser.mockResolvedValue(undefined)
  })

  it('blocks a user through the user mutation and emits the changed state', async () => {
    const wrapper = mountButton(false)

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(mocks.blockUser).toHaveBeenCalledWith(42)
    expect(mocks.unblockUser).not.toHaveBeenCalled()
    expect(wrapper.emitted('block-change')).toEqual([[true]])
  })

  it('unblocks a user through the user mutation and emits the changed state', async () => {
    const wrapper = mountButton(true)

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(mocks.unblockUser).toHaveBeenCalledWith(42)
    expect(mocks.blockUser).not.toHaveBeenCalled()
    expect(wrapper.emitted('block-change')).toEqual([[false]])
  })

  it('shows the existing error toast when the mutation fails', async () => {
    mocks.blockUser.mockRejectedValueOnce(new Error('request failed'))
    const wrapper = mountButton(false)

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(mocks.addToast).toHaveBeenCalledWith('user.block.processFailed', 'error')
    expect(wrapper.emitted('block-change')).toBeUndefined()
  })
})
