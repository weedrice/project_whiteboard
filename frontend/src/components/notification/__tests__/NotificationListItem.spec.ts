import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BaseBadge from '@/components/common/ui/BaseBadge.vue'
import NotificationListItem from '../NotificationListItem.vue'
import type { Notification } from '@/types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: { count?: number }) => params?.count === undefined ? key : `${key}:${params.count}`,
  }),
}))

const notification: Notification = {
  notificationId: 12,
  notificationType: 'BADGE',
  message: 'Badge earned',
  sourceType: 'SYSTEM',
  sourceId: 3,
  isRead: false,
  createdAt: '2026-09-15T10:00:00Z',
  grouped: true,
  groupCount: 4,
  actor: { userId: 0, authorType: 'SYSTEM', displayName: 'System' },
  actorDisplayName: 'System',
  actorInitial: 'S',
}

describe('NotificationListItem', () => {
  it.each(['dropdown', 'page'] as const)('exposes read state and updates it after activation in %s mode', async (mode) => {
    const wrapper = mount(NotificationListItem, {
      props: { notification, mode, timeText: '3m' },
    })
    const status = wrapper.get('.sr-only')
    expect(status.text()).toBe('notification.unread.')
    expect(status.attributes('aria-hidden')).toBeUndefined()
    expect(wrapper.findAll('span[aria-hidden="true"]').some((node) => node.classes().includes('rounded-full') && node.classes().includes('h-2'))).toBe(true)

    await wrapper.setProps({ notification: { ...notification, isRead: true } })
    expect(status.text()).toBe('notification.read.')
    expect(wrapper.classes()).not.toContain('nv-unread-surface')
    expect(wrapper.findAll('span[aria-hidden="true"]').some((node) => node.classes().includes('h-2'))).toBe(false)
  })

  it('renders dropdown actor, relative time, unread surface, and accent tone', async () => {
    const wrapper = mount(NotificationListItem, {
      props: { notification, mode: 'dropdown', timeText: '3m' },
    })

    expect(wrapper.text()).toContain('System')
    expect(wrapper.text()).toContain('3m')
    expect(wrapper.classes()).toContain('nv-unread-surface')
    expect(wrapper.getComponent(BaseBadge).props('variant')).toBe('accent')

    await wrapper.trigger('click')
    expect(wrapper.emitted('activate')).toEqual([[notification]])
  })

  it('renders page date and grouped count without duplicating the actor row', () => {
    const wrapper = mount(NotificationListItem, {
      props: { notification: { ...notification, isRead: true }, mode: 'page', timeText: '2026-09-15' },
    })

    expect(wrapper.text()).toContain('2026-09-15')
    expect(wrapper.text()).toContain('notification.groupedCount:4')
    expect(wrapper.classes()).not.toContain('nv-unread-surface')
    expect(wrapper.text()).not.toContain('System')
  })
})
