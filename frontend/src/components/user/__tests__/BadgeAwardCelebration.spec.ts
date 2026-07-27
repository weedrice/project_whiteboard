import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BadgeAwardCelebration from '../BadgeAwardCelebration.vue'
import { emitBadgeAwardEvent } from '@/features/notifications/events/badgeAwardEvents'
import type { Notification } from '@/types'
import { createDeferred } from '@/test/async'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const mocks = vi.hoisted(() => ({
  getMyBadges: vi.fn(),
  routerPush: vi.fn(),
  authStore: { user: { userId: 7 }, sessionGeneration: 7 },
}))

vi.mock('@/api/badge', () => ({
  badgeApi: { getMyBadges: mocks.getMyBadges },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}))

vi.mock('@/utils/logger', () => ({
  default: { warn: vi.fn() },
}))

const badgeNotification: Notification = {
  notificationId: 91,
  notificationType: 'BADGE',
  message: 'New badge acquired',
  sourceType: 'SYSTEM',
  sourceId: 15,
  isRead: false,
  createdAt: '2026-07-12T00:00:00',
  actor: { userId: 0, authorType: 'SYSTEM', displayName: '' },
  actorDisplayName: '',
  actorInitial: '',
}

describe('BadgeAwardCelebration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.user.userId = 7
    mocks.authStore.sessionGeneration = 7
  })

  const mountCelebration = () => mount(BadgeAwardCelebration, {
    global: {
      mocks: { $t: (key: string) => key },
      stubs: {
        BaseModal: {
          props: ['isOpen'],
          template: '<section v-if="isOpen"><slot /><slot name="footer" /></section>',
        },
        BaseButton: { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
      },
    },
  })

  it('shows the latest acquired badge and links to representative badge settings', async () => {
    mocks.getMyBadges.mockResolvedValue({
      data: {
        success: true,
        data: [{
          badgeCode: 'ATTENDANCE_7',
          name: '7 Day Streak',
          description: 'Seven consecutive check-ins',
          acquired: true,
          acquiredAt: '2026-07-12T00:00:00',
          representative: false,
          tier: 'SILVER',
        }],
      },
    })

    const wrapper = mountCelebration()

    emitBadgeAwardEvent(badgeNotification)
    await flushPromises()

    expect(wrapper.text()).toContain('7일 연속 출석')
    expect(wrapper.text()).toContain('SILVER')
    expect(wrapper.get('h3').text()).toBe('7일 연속 출석')
    expect(wrapper.text()).toContain('7일 연속으로 출석 체크하면 획득합니다.')

    const representativeButton = wrapper.findAll('button')
      .find((button) => button.text() === 'user.badgeAward.setRepresentative')!
    await representativeButton.trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/mypage/badges')
    wrapper.unmount()
  })

  it('aborts and ignores a previous account badge response at the session boundary', async () => {
    const pending = createDeferred<Awaited<ReturnType<typeof mocks.getMyBadges>>>()
    mocks.getMyBadges.mockReturnValueOnce(pending.promise)
    const wrapper = mountCelebration()

    emitBadgeAwardEvent(badgeNotification)
    const signal = mocks.getMyBadges.mock.calls[0][0]?.signal as AbortSignal
    mocks.authStore.sessionGeneration = 8
    mocks.authStore.user.userId = 8
    notifyAuthSessionBoundary(8)
    expect(signal.aborted).toBe(true)
    pending.resolve({ data: { success: true, data: [{
      badgeCode: 'ATTENDANCE_7', name: 'Account A', acquired: true,
      acquiredAt: '2026-07-12T00:00:00', representative: false, tier: 'SILVER',
    }] } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Account A')
    wrapper.unmount()
  })

  it('clears shown badge codes so the next account can show the same badge code', async () => {
    mocks.getMyBadges.mockResolvedValue({ data: { success: true, data: [{
      badgeCode: 'SHARED', name: 'Account badge', acquired: true,
      acquiredAt: '2026-07-12T00:00:00', representative: false, tier: 'SILVER',
    }] } })
    const wrapper = mountCelebration()
    emitBadgeAwardEvent(badgeNotification)
    await flushPromises()
    expect(wrapper.text()).toContain('Account badge')

    mocks.authStore.sessionGeneration = 8
    notifyAuthSessionBoundary(8)
    emitBadgeAwardEvent(badgeNotification)
    await flushPromises()
    expect(mocks.getMyBadges).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Account badge')
    wrapper.unmount()
  })
})
