import { flushPromises, mount } from '@vue/test-utils'
import type { Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import HomeAttendancePanel from '../HomeAttendancePanel.vue'

const mocks = vi.hoisted(() => ({
  authStore: {
    isAuthenticated: true,
    sessionGeneration: 0,
    user: { userId: 7 },
  },
  attendance: null as unknown as Ref<{ checkedInToday: boolean; currentStreakCount: number }>,
  checkIn: vi.fn(),
  addToast: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => params ? `${key}:${JSON.stringify(params)}` : key,
  }),
}))
vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue')
  mocks.authStore = reactive(mocks.authStore)
  return { useAuthStore: () => mocks.authStore }
})
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/features/user/attendance/useAttendance', async () => {
  const { ref } = await import('vue')
  mocks.attendance = ref({ checkedInToday: false, currentStreakCount: 6 })
  return {
    useAttendance: () => ({
      useMyAttendance: () => ({
        data: mocks.attendance,
        isLoading: ref(false),
        isError: ref(false),
        refetch: vi.fn(),
      }),
      useCheckIn: () => ({ mutateAsync: mocks.checkIn, isPending: ref(false) }),
    }),
  }
})

const mountPanel = () => mount(HomeAttendancePanel, {
  global: {
    mocks: { $t: (key: string, params?: Record<string, unknown>) => params ? `${key}:${JSON.stringify(params)}` : key },
  },
})

describe('HomeAttendancePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.isAuthenticated = true
    mocks.authStore.sessionGeneration = 0
    mocks.authStore.user = { userId: 7 }
    mocks.attendance.value = { checkedInToday: false, currentStreakCount: 6 }
  })

  it('clears a milestone when the authenticated session changes', async () => {
    mocks.checkIn.mockResolvedValue({
      alreadyCheckedIn: false,
      streakCount: 7,
      earnedPoints: 10,
    })
    const wrapper = mountPanel()

    await wrapper.get('button.nv-attendance-button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('home.attendance.milestone')

    mocks.authStore.sessionGeneration = 1
    mocks.authStore.user = { userId: 8 }
    await flushPromises()

    expect(wrapper.text()).not.toContain('home.attendance.milestone')
  })

  it('ignores a delayed previous-session result and consumes click rejections', async () => {
    let resolveCheckIn!: (value: unknown) => void
    mocks.checkIn.mockImplementationOnce(() => new Promise((resolve) => {
      resolveCheckIn = resolve
    }))
    const errorHandler = vi.fn()
    const wrapper = mount(HomeAttendancePanel, {
      global: {
        config: { errorHandler },
        mocks: { $t: (key: string) => key },
      },
    })

    await wrapper.get('button.nv-attendance-button').trigger('click')
    mocks.authStore.sessionGeneration = 1
    mocks.authStore.user = { userId: 8 }
    resolveCheckIn({ alreadyCheckedIn: false, streakCount: 7, earnedPoints: 10 })
    await flushPromises()

    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('home.attendance.milestone')

    mocks.checkIn.mockRejectedValueOnce(new Error('offline'))
    await wrapper.get('button.nv-attendance-button').trigger('click')
    await flushPromises()
    expect(errorHandler).not.toHaveBeenCalled()
  })

  it('waits for the authenticated user to hydrate before allowing check-in', async () => {
    mocks.authStore.user = null as unknown as { userId: number }
    const wrapper = mountPanel()
    const button = wrapper.get('button.nv-attendance-button')

    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(mocks.checkIn).not.toHaveBeenCalled()

    mocks.authStore.user = { userId: 7 }
    await flushPromises()
    expect(button.attributes('disabled')).toBeUndefined()
  })
})
