import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAttendance } from '@/features/user/attendance/useAttendance'

const mocks = vi.hoisted(() => ({
  useQuery: vi.fn((options) => options),
  useMutation: vi.fn((options) => options),
  invalidateQueries: vi.fn(),
  getMyAttendance: vi.fn(),
  checkIn: vi.fn(),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: mocks.useQuery,
  useMutation: mocks.useMutation,
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}))
vi.mock('@/api/attendance', () => ({
  attendanceApi: {
    getMyAttendance: mocks.getMyAttendance,
    checkIn: mocks.checkIn,
  },
}))

describe('useAttendance', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads a selected month with the query abort signal', async () => {
    mocks.getMyAttendance.mockResolvedValue({ data: { data: { checkedIn: true } } })
    const { useMyAttendance } = useAttendance()
    const query = useMyAttendance(ref(true), ref('2026-07')) as unknown as {
      queryKey: { value: unknown },
      queryFn: (context: { signal: AbortSignal }) => Promise<unknown>,
    }
    const controller = new AbortController()

    expect(query.queryKey.value).toEqual(['attendance', 'me', '2026-07'])
    await expect(query.queryFn({ signal: controller.signal })).resolves.toEqual({ checkedIn: true })
    expect(mocks.getMyAttendance).toHaveBeenCalledWith('2026-07', { signal: controller.signal })
  })

  it('checks in and invalidates attendance and point caches', async () => {
    mocks.checkIn.mockResolvedValue({ data: { data: { consecutiveDays: 3 } } })
    const { useCheckIn } = useAttendance()
    const mutation = useCheckIn() as unknown as {
      mutationFn: () => Promise<unknown>,
      onSuccess: () => void,
    }

    await expect(mutation.mutationFn()).resolves.toEqual({ consecutiveDays: 3 })
    mutation.onSuccess()

    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['attendance', 'me'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'points'] })
  })
})
