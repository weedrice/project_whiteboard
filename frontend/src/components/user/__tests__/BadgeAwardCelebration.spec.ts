import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BadgeAwardCelebration from '../BadgeAwardCelebration.vue'
import { emitBadgeAwardEvent } from '@/composables/badgeAwardEvents'
import type { Notification } from '@/types'

const mocks = vi.hoisted(() => ({
  getMyBadges: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('@/api/badge', () => ({
  badgeApi: { getMyBadges: mocks.getMyBadges },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { userId: 7 } }),
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

    const wrapper = mount(BadgeAwardCelebration, {
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

    emitBadgeAwardEvent(badgeNotification)
    await flushPromises()

    expect(wrapper.text()).toContain('7 Day Streak')
    expect(wrapper.text()).toContain('SILVER')
    expect(wrapper.get('h3').text()).toBe('7 Day Streak')

    const representativeButton = wrapper.findAll('button')
      .find((button) => button.text() === 'user.badgeAward.setRepresentative')!
    await representativeButton.trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/user/7')
  })
})
