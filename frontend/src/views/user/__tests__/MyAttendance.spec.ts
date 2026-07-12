import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import MyAttendance from '../MyAttendance.vue'

const attendance = ref({
  month: '2026-07',
  today: '2026-07-12',
  checkedInToday: true,
  currentStreakCount: 7,
  days: [{ attendanceDate: '2026-07-12', streakCount: 7 }],
})
const isError = ref(false)
const refetch = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ locale: ref('en'), t: (key: string) => key }),
}))

vi.mock('@/composables/useAttendance', () => ({
  useAttendance: () => ({
    useMyAttendance: () => ({ data: attendance, isLoading: ref(false), isError, refetch }),
  }),
}))

describe('MyAttendance', () => {
  beforeEach(() => {
    isError.value = false
    refetch.mockClear()
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 12))
  })

  it('renders the monthly grid, today marker, and streak day', () => {
    const wrapper = mount(MyAttendance, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BaseButton: { template: '<button type="button"><slot /></button>' },
          BaseSpinner: true,
        },
      },
    })

    expect(wrapper.text()).toContain('July 2026')
    expect(wrapper.text()).toContain('user.attendance.today')
    expect(wrapper.text()).toContain('user.attendance.checkedDay')
    expect(wrapper.get('[role="grid"]').attributes('aria-label')).toBe('July 2026')
    expect(wrapper.findAll('[role="columnheader"]')).toHaveLength(7)
    expect(wrapper.findAll('[role="row"]')).toHaveLength(6)
    expect(wrapper.get('[aria-current="date"]').attributes('aria-label')).toContain('Sunday, July 12, 2026')
  })

  it('shows a retryable error instead of an empty calendar', async () => {
    isError.value = true
    const wrapper = mount(MyAttendance, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          BaseButton: { template: '<button type="button"><slot /></button>' },
          BaseSpinner: true,
        },
      },
    })

    expect(wrapper.find('[role="grid"]').exists()).toBe(false)
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(refetch).toHaveBeenCalledOnce()
  })
})
