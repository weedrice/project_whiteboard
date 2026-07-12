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

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ locale: ref('en'), t: (key: string) => key }),
}))

vi.mock('@/composables/useAttendance', () => ({
  useAttendance: () => ({
    useMyAttendance: () => ({ data: attendance, isLoading: ref(false) }),
  }),
}))

describe('MyAttendance', () => {
  beforeEach(() => {
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
    expect(wrapper.findAll('.grid-cols-7').length).toBeGreaterThanOrEqual(2)
  })
})
