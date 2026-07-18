import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useNotificationStream } from '@/features/notifications/stream/useNotificationStream'

const mocks = vi.hoisted(() => ({
  closeSse: vi.fn(),
  connectToSse: vi.fn(),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: vi.fn(() => ({})),
}))

vi.mock('@/features/notifications/queries/useNotification', () => ({
  useNotification: () => ({
    useUnreadCount: () => ({ data: ref(0) }),
  }),
}))

vi.mock('@/features/notifications/stream/notificationStreamController', () => ({
  createNotificationStreamController: () => ({
    closeSse: mocks.closeSse,
    connectToSse: mocks.connectToSse,
  }),
}))

describe('useNotificationStream', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reconnects when an authenticated session advances to a new generation', async () => {
    const authenticated = ref(true)
    const sessionGeneration = ref(1)
    const wrapper = mount(defineComponent({
      setup() {
        useNotificationStream(
          () => authenticated.value,
          () => sessionGeneration.value,
        )
        return () => h('div')
      },
    }))

    expect(mocks.connectToSse).toHaveBeenCalledTimes(1)
    expect(mocks.closeSse).not.toHaveBeenCalled()

    sessionGeneration.value = 2
    await nextTick()

    expect(mocks.closeSse).toHaveBeenCalledTimes(1)
    expect(mocks.connectToSse).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    expect(mocks.closeSse).toHaveBeenCalledTimes(2)
  })
})
