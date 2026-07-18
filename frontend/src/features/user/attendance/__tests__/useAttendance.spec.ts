import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAttendance } from '@/features/user/attendance/useAttendance'

const authState = vi.hoisted(() => ({ sessionGeneration: 0, user: { userId: 7 } }))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))

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
  beforeEach(() => {
    vi.clearAllMocks()
    authState.sessionGeneration = 0
  })

  it('loads a selected month with the query abort signal', async () => {
    mocks.getMyAttendance.mockResolvedValue({ data: { data: { checkedIn: true } } })
    const { useMyAttendance } = useAttendance()
    const query = useMyAttendance(ref(true), ref('2026-07')) as unknown as {
      queryKey: { value: unknown },
      queryFn: (context: { signal: AbortSignal }) => Promise<unknown>,
    }
    const controller = new AbortController()

    expect(query.queryKey.value).toEqual(['session', 0, 'attendance', 'me', '2026-07'])
    await expect(query.queryFn({ signal: controller.signal })).resolves.toEqual({ checkedIn: true })
    expect(mocks.getMyAttendance).toHaveBeenCalledWith('2026-07', { signal: controller.signal })
  })

  it('checks in and invalidates attendance and point caches', async () => {
    mocks.checkIn.mockResolvedValue({ data: { data: { consecutiveDays: 3 } } })
    const { useCheckIn } = useAttendance()
    const mutation = useCheckIn() as unknown as {
      mutationFn: () => Promise<unknown>,
      onMutate: () => { sessionGeneration: number },
      onSuccess: (_data: unknown, _variables: unknown, context: unknown) => void,
    }

    await expect(mutation.mutationFn()).resolves.toEqual({ consecutiveDays: 3 })
    mutation.onSuccess(undefined, undefined, mutation.onMutate())

    expect(mocks.checkIn).toHaveBeenCalledWith({
      signal: expect.any(AbortSignal),
      skipGlobalErrorHandler: true,
    })

    expect(mocks.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, 'attendance', 'me'],
    })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, 'user', 'points'],
    })
  })

  it('does not invalidate the next account after a delayed check-in', () => {
    const { useCheckIn } = useAttendance()
    const mutation = useCheckIn() as unknown as {
      onMutate: () => { sessionGeneration: number },
      onSuccess: (_data: unknown, _variables: unknown, context: unknown) => void,
    }
    const context = mutation.onMutate()
    authState.sessionGeneration = 1

    mutation.onSuccess(undefined, undefined, context)

    expect(mocks.invalidateQueries).not.toHaveBeenCalled()
  })

  it('rejects a delayed result after the authentication intent changes', async () => {
    let resolveCheckIn!: (value: unknown) => void
    mocks.checkIn.mockImplementationOnce(() => new Promise((resolve) => {
      resolveCheckIn = resolve
    }))
    const { useCheckIn } = useAttendance()
    const mutation = useCheckIn() as unknown as {
      mutationFn: () => Promise<unknown>,
    }

    const request = mutation.mutationFn()
    authState.sessionGeneration = 1
    authState.user = { userId: 8 }
    resolveCheckIn({ data: { data: { earnedPoints: 10 } } })

    await expect(request).rejects.toMatchObject({ name: 'AbortError' })
  })
})
