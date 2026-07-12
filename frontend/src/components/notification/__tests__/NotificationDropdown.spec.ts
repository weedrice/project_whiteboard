import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationDropdown from '../NotificationDropdown.vue'
import type { Notification } from '@/types'

const notificationsData = ref<{ content: Notification[] }>({ content: [] })
const isLoading = ref(false)
const isError = ref(false)
const error = ref<Error | null>(null)
const isMarkingAllAsRead = ref(false)
const refetchNotifications = vi.fn()
const markAllAsRead = vi.fn()
const navigateFromNotification = vi.fn()

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({
    useNotifications: () => ({ data: notificationsData, isLoading, isError, error, refetch: refetchNotifications }),
    useMarkAllAsRead: () => ({ mutate: markAllAsRead, isPending: isMarkingAllAsRead }),
  }),
}))

vi.mock('@/composables/useNotificationNavigation', () => ({
  useNotificationNavigation: () => ({ navigateFromNotification }),
}))

const BaseButtonStub = defineComponent({
  name: 'BaseButton',
  props: {
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          disabled: props.disabled,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const makeNotification = (
  isRead: boolean,
  notificationType: Notification['notificationType'] = 'COMMENT',
): Notification => ({
  notificationId: isRead ? 1 : 2,
  notificationType,
  sourceType: 'POST',
  sourceId: 10,
  message: isRead ? 'read notification' : 'unread notification',
  isRead,
  createdAt: '2026-05-23T10:00:00',
  actor: {
    userId: 3,
    displayName: 'Tester',
    profileImageUrl: '',
  },
  actorDisplayName: 'Tester',
  actorInitial: 'T',
})

const mountDropdown = () =>
  mount(NotificationDropdown, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        BaseButton: BaseButtonStub,
        Check: true,
        RouterLink: defineComponent({
          setup(_props, { slots }) {
            return () => h('a', slots.default?.())
          },
        }),
      },
    },
  })

describe('NotificationDropdown', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    isLoading.value = false
    isError.value = false
    error.value = null
    isMarkingAllAsRead.value = false
    notificationsData.value = { content: [] }
  })

  it('disables mark-all when every notification is already read', async () => {
    notificationsData.value = { content: [makeNotification(true)] }
    const wrapper = mountDropdown()

    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')

    expect(markAllAsRead).not.toHaveBeenCalled()
  })

  it('marks all as read only when unread notifications exist', async () => {
    notificationsData.value = { content: [makeNotification(false)] }
    const wrapper = mountDropdown()

    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeUndefined()
    await button.trigger('click')

    expect(markAllAsRead).toHaveBeenCalledTimes(1)
  })

  it('uses a button for notification items and navigates without hash links', async () => {
    const notification = makeNotification(false)
    notificationsData.value = { content: [notification] }
    const wrapper = mountDropdown()

    const item = wrapper.findAll('button').find((button) => button.text().includes(notification.message))

    expect(item).toBeTruthy()
    expect(item?.attributes('type')).toBe('button')
    expect(item?.attributes('href')).toBeUndefined()

    await item!.trigger('click')

    expect(navigateFromNotification).toHaveBeenCalledWith(notification)
  })

  it('exposes dialog semantics with a localized accessible name', () => {
    const wrapper = mountDropdown()

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('common.notifications')
  })

  it('uses the shared unread surface token for unread rows', () => {
    const notification = makeNotification(false)
    notificationsData.value = { content: [notification] }
    const wrapper = mountDropdown()

    const item = wrapper.findAll('button').find((button) => button.text().includes(notification.message))

    expect(item?.classes()).toContain('nv-hover-surface')
    expect(item?.classes()).toContain('nv-unread-surface')
  })

  it('renders normalized actor display fields instead of raw actor DTO fields', () => {
    notificationsData.value = {
      content: [{
        ...makeNotification(true),
        actor: {
          userId: 0,
          authorType: 'SYSTEM',
          displayName: '',
        },
        actorDisplayName: 'System',
        actorInitial: 'S',
      }],
    }

    const wrapper = mountDropdown()

    expect(wrapper.text()).toContain('System')
    expect(wrapper.text()).not.toContain('displayName')
  })

  it('renders notification type labels in item metadata', () => {
    notificationsData.value = { content: [makeNotification(false, 'MENTION')] }
    const wrapper = mountDropdown()

    expect(wrapper.text()).toContain('notification.types.mention')
  })

  it('uses the shared notification load error message and retries', async () => {
    isError.value = true
    error.value = new Error('raw backend detail')
    const wrapper = mountDropdown()

    expect(wrapper.text()).toContain('common.messages.loadFailed')
    expect(wrapper.text()).not.toContain('raw backend detail')

    const retryButton = wrapper.findAll('button').find((button) => button.text().includes('common.retry'))
    await retryButton!.trigger('click')

    expect(refetchNotifications).toHaveBeenCalledTimes(1)
  })
})
