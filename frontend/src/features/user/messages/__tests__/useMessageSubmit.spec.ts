import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useMessageSubmit } from '../useMessageSubmit'
import { BLOCKED_BY_USER_CODE, messageApi } from '@/api/message'
import { createDeferred } from '@/test/async'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: mocks.addToast,
  }),
}))

vi.mock('@/api/message', () => ({
  BLOCKED_BY_USER_CODE: 'U009',
  messageApi: {
    sendMessage: vi.fn(),
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
  },
}))

describe('useMessageSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('trims valid message content before sending', async () => {
    vi.mocked(messageApi.sendMessage).mockResolvedValue({
      data: {
        success: true,
        data: 1,
      },
    } as Awaited<ReturnType<typeof messageApi.sendMessage>>)
    const onSuccess = vi.fn()
    const submit = useMessageSubmit({
      getReceiverId: () => 7,
      onSuccess,
      logMessage: 'Failed to send message:',
    })
    submit.content.value = '  hello  '

    await expect(submit.send()).resolves.toBe(true)

    expect(messageApi.sendMessage).toHaveBeenCalledWith(7, 'hello', {
      skipGlobalErrorHandler: true,
      signal: expect.any(AbortSignal),
    })
    expect(mocks.addToast).toHaveBeenCalledWith('user.message.sendSuccess', 'success')
    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(submit.content.value).toBe('')
  })

  it('uses the invalid toast for blank messages', async () => {
    const submit = useMessageSubmit({
      getReceiverId: () => 7,
      logMessage: 'Failed to send message:',
    })
    submit.content.value = '   '

    await expect(submit.send()).resolves.toBe(false)

    expect(messageApi.sendMessage).not.toHaveBeenCalled()
    expect(mocks.addToast).toHaveBeenCalledWith('user.message.inputContent', 'warning')
  })

  it('shows the blocked-by-user toast for blocked receiver errors', async () => {
    vi.mocked(messageApi.sendMessage).mockRejectedValue({
      response: {
        data: {
          code: BLOCKED_BY_USER_CODE,
          message: 'Blocked by user',
        },
      },
    })
    const submit = useMessageSubmit({
      getReceiverId: () => 7,
      logMessage: 'Failed to send message:',
    })
    submit.content.value = 'hello'

    await expect(submit.send()).resolves.toBe(false)

    expect(mocks.addToast).toHaveBeenCalledWith('user.message.blockedByUser', 'error')
    expect(submit.content.value).toBe('hello')
  })

  it('aborts a pending send and suppresses late success at a session boundary', async () => {
    const pending = createDeferred<Awaited<ReturnType<typeof messageApi.sendMessage>>>()
    vi.mocked(messageApi.sendMessage).mockReturnValue(pending.promise)
    const onSuccess = vi.fn()
    const submit = useMessageSubmit({
      getReceiverId: () => 7,
      onSuccess,
      logMessage: 'Failed to send message:',
    })
    submit.content.value = 'hello'

    const sending = submit.send()
    const signal = vi.mocked(messageApi.sendMessage).mock.calls[0][2]?.signal
    notifyAuthSessionBoundary(2)

    expect(signal?.aborted).toBe(true)
    expect(submit.content.value).toBe('')
    pending.resolve({ data: { success: true, data: 1 } } as Awaited<ReturnType<typeof messageApi.sendMessage>>)
    await expect(sending).resolves.toBe(false)
    expect(onSuccess).not.toHaveBeenCalled()
    expect(mocks.addToast).not.toHaveBeenCalledWith('user.message.sendSuccess', 'success')
  })
})
