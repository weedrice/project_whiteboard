import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyNotifications from '../MyNotifications.vue'
import { pageResponseFixture } from '@/test/apiResponseFixtures'
import type { Notification, PageResponse } from '@/types'

const notificationsData = ref<PageResponse<Notification> | null>(null)
const isLoading = ref(false)
const isError = ref(false)
const error = ref<Error | null>(null)
const isMarkingAllAsRead = ref(false)
const refetchNotifications = vi.fn()
const markAllAsRead = vi.fn()
const navigateFromNotification = vi.fn()
let latestParams: Ref<{ page: number; size: number }> | undefined

const testI18n = vi.hoisted(() => ({
  translate: (key: string) => ({
    'notification.types.like': '좋아요',
    'notification.types.comment': '댓글',
    'notification.types.mention': '멘션',
    'notification.types.system': '시스템',
  }[key] ?? key),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: testI18n.translate }),
}))

vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({
    useNotifications: (params: Ref<{ page: number; size: number }>) => {
      latestParams = params
      return { data: notificationsData, isLoading, isError, error, refetch: refetchNotifications }
    },
    useMarkAllAsRead: () => ({ mutate: markAllAsRead, isPending: isMarkingAllAsRead }),
  }),
}))

vi.mock('@/composables/useNotificationNavigation', () => ({
  useNotificationNavigation: () => ({ navigateFromNotification }),
}))

vi.mock('@/utils/date', () => ({
  formatDate: (value: string) => value,
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

const ErrorStateStub = defineComponent({
  name: 'ErrorState',
  props: {
    message: String,
    showRetry: Boolean,
  },
  emits: ['retry'],
  setup(props, { emit }) {
    return () => h('div', { 'data-testid': 'error-state', 'data-message': props.message }, [
      props.showRetry ? h('button', { onClick: () => emit('retry') }, 'retry') : null,
    ])
  },
})

const makeNotification = (
  isRead: boolean,
  sourceType: Notification['sourceType'] = 'POST',
  notificationType: Notification['notificationType'] = 'COMMENT',
): Notification => ({
  notificationId: isRead ? 1 : 2,
  notificationType,
  sourceType,
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

const makePage = (content: Notification[]): PageResponse<Notification> =>
  pageResponseFixture(content, { size: 15 })

const mountMyNotifications = () =>
  mount(MyNotifications, {
    global: {
      mocks: {
        $t: testI18n.translate,
      },
      stubs: {
        BaseButton: BaseButtonStub,
        BaseSkeleton: true,
        Bell: true,
        Check: true,
        EmptyState: true,
        ErrorState: ErrorStateStub,
        PageSizeSelector: true,
        Pagination: true,
      },
    },
  })

describe('MyNotifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    isLoading.value = false
    isError.value = false
    error.value = null
    isMarkingAllAsRead.value = false
    notificationsData.value = makePage([])
    latestParams = undefined
  })

  it('disables mark-all when the page has no unread notifications', async () => {
    notificationsData.value = makePage([makeNotification(true)])
    const wrapper = mountMyNotifications()

    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')

    expect(markAllAsRead).not.toHaveBeenCalled()
  })

  it('marks all as read when the page has unread notifications', async () => {
    notificationsData.value = makePage([makeNotification(false)])
    const wrapper = mountMyNotifications()

    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeUndefined()
    await button.trigger('click')

    expect(markAllAsRead).toHaveBeenCalledTimes(1)
  })

  it('uses tokenized surfaces for unread notification rows', () => {
    notificationsData.value = makePage([makeNotification(false)])
    const wrapper = mountMyNotifications()

    const unreadRow = wrapper.get('li')

    expect(unreadRow.classes()).toContain('nv-hover-surface')
    expect(unreadRow.classes()).toContain('nv-unread-surface')
    expect(unreadRow.get('a').classes()).toContain('active:bg-[var(--nv-surface-active)]')
  })

  it('renders notification type labels', () => {
    notificationsData.value = makePage([
      makeNotification(true, 'POST', 'LIKE'),
      makeNotification(false, 'COMMENT', 'MENTION'),
      makeNotification(true, 'SYSTEM', 'SYSTEM'),
    ])
    const wrapper = mountMyNotifications()

    expect(wrapper.text()).toContain('좋아요')
    expect(wrapper.text()).toContain('멘션')
    expect(wrapper.text()).toContain('시스템')
  })

  it('shows error state and retries notification loading', async () => {
    isError.value = true
    notificationsData.value = makePage([])
    const wrapper = mountMyNotifications()

    expect(wrapper.get('[data-testid="error-state"]').attributes('data-message')).toBe('common.messages.loadFailed')
    await wrapper.get('[data-testid="error-state"] button').trigger('click')

    expect(refetchNotifications).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)
  })

  it('updates notification query params through shared pagination state', async () => {
    const wrapper = mountMyNotifications()
    const listCard = wrapper.getComponent({ name: 'PaginatedListCard' })

    expect(latestParams?.value).toEqual({ page: 0, size: 15 })

    listCard.vm.$emit('page-change', 2)
    await nextTick()
    expect(latestParams?.value).toEqual({ page: 2, size: 15 })

    listCard.vm.$emit('size-change', 30)
    await nextTick()
    expect(latestParams?.value).toEqual({ page: 0, size: 30 })
  })
})
