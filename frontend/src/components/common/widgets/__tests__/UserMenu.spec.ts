import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserMenu from '../UserMenu.vue'
import { reportApi } from '@/api/report'
import { createDeferred } from '@/test/async'

const authState = {
  user: { userId: 1 } as { userId: number } | null,
  sessionGeneration: 0,
}
const invalidateQueriesMock = vi.fn()
const routerPushMock = vi.hoisted(() => vi.fn())
const addToastMock = vi.hoisted(() => vi.fn())

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key === 'user.deletedUser' ? '탈퇴한 사용자' : key
    })
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal<typeof import('vue-router')>(),
  useRouter: () => ({
    push: routerPushMock
  })
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/vue-query')>()
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: invalidateQueriesMock
    })
  }
})

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: addToastMock
  })
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn()
  })
}))

vi.mock('@/api/user', () => ({
  userApi: {
    blockUser: vi.fn()
  }
}))

vi.mock('@/api/report', () => ({
  reportApi: {
    reportUser: vi.fn()
  }
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn()
  }
}))

describe('UserMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { userId: 1 }
    authState.sessionGeneration = 0
  })

  it('truncates the visible label using maxLabelLength', () => {
    const wrapper = mount(UserMenu, {
      props: {
        userId: 2,
        displayName: '12345678901',
        maxLabelLength: 10
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    const button = wrapper.get('button')

    expect(button.text()).toBe('1234567890...')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('shows the deleted-user fallback label and keeps profile access for guests', () => {
    authState.user = null

    const wrapper = mount(UserMenu, {
      props: {
        userId: 2,
        displayName: '',
        maxLabelLength: 10
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    const button = wrapper.get('button')

    expect(button.text()).toBe('탈퇴한 사용자')
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('keeps profile access for the current user', () => {
    authState.user = { userId: 7 }

    const wrapper = mount(UserMenu, {
      props: {
        userId: 7,
        displayName: 'Author'
      },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: true,
          Teleport: true
        }
      }
    })

    expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
  })

  it('uses roving focus for menu arrows, Home, End, Escape, and Tab', async () => {
    const wrapper = mount(UserMenu, {
      attachTo: document.body,
      props: { userId: 2, displayName: 'Author' },
      global: {
        stubs: { MessageModal: true, ReportModal: true, Teleport: true }
      }
    })
    const trigger = wrapper.get('.nv-user-menu-button')
    await trigger.trigger('click')
    await nextTick()
    const menu = wrapper.get('[role="menu"]')
    const items = wrapper.findAll('[role="menuitem"]')

    expect(items[0].attributes('tabindex')).toBe('0')
    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.findAll('[role="menuitem"]')[1].attributes('tabindex')).toBe('0')
    await menu.trigger('keydown', { key: 'End' })
    expect(wrapper.findAll('[role="menuitem"]').at(-1)?.attributes('tabindex')).toBe('0')
    await menu.trigger('keydown', { key: 'Home' })
    expect(wrapper.findAll('[role="menuitem"]')[0].attributes('tabindex')).toBe('0')
    await menu.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    await vi.waitFor(() => expect(document.activeElement).toBe(trigger.element))

    await trigger.trigger('click')
    await wrapper.get('[role="menu"]').trigger('keydown', { key: 'Tab' })
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('aborts and ignores a pending user report when the menu target changes', async () => {
    const reportResult = createDeferred<Awaited<ReturnType<typeof reportApi.reportUser>>>()
    vi.mocked(reportApi.reportUser).mockReturnValueOnce(reportResult.promise)
    const ReportModalStub = defineComponent({
      props: {
        isOpen: Boolean,
        submit: Function,
      },
      setup(props) {
        return () => props.isOpen
          ? h('button', {
              'data-test': 'submit-report',
              onClick: () => (props.submit as (reason: string) => Promise<boolean>)('신고 사유'),
            }, 'submit report')
          : null
      },
    })
    const wrapper = mount(UserMenu, {
      props: { userId: 2, displayName: 'Original' },
      global: {
        stubs: {
          MessageModal: true,
          ReportModal: ReportModalStub,
          Teleport: true,
        },
      },
    })

    await wrapper.get('.nv-user-menu-button').trigger('click')
    await nextTick()
    await wrapper.findAll('[role="menuitem"]')[1].trigger('click')
    await wrapper.get('[data-test="submit-report"]').trigger('click')
    const signal = vi.mocked(reportApi.reportUser).mock.calls[0][3]?.signal

    await wrapper.setProps({ userId: 3, displayName: 'Replacement' })

    expect(signal?.aborted).toBe(true)
    reportResult.resolve({ data: { success: true } } as Awaited<ReturnType<typeof reportApi.reportUser>>)
    await flushPromises()
    expect(addToastMock).not.toHaveBeenCalledWith('report.reportSuccess', 'success')
  })
})
