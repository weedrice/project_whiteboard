import { mount } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyNotifications from '../MyNotifications.vue'
import type { Notification, PageResponse } from '@/types'

const notificationsData = ref<PageResponse<Notification> | null>(null)
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

const makeNotification = (isRead: boolean): Notification => ({
  notificationId: isRead ? 1 : 2,
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
})

const makePage = (content: Notification[]): PageResponse<Notification> => ({
  content,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  size: 15,
  number: 0,
  first: true,
  last: true,
  empty: content.length === 0,
})

const mountMyNotifications = () =>
  mount(MyNotifications, {
    global: {
      mocks: {
        $t: (key: string) => key,
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

  it('shows error state and retries notification loading', async () => {
    isError.value = true
    notificationsData.value = makePage([])
    const wrapper = mountMyNotifications()

    expect(wrapper.get('[data-testid="error-state"]').attributes('data-message')).toBe('common.messages.loadFailed')
    await wrapper.get('[data-testid="error-state"] button').trigger('click')

    expect(refetchNotifications).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent({ name: 'EmptyState' }).exists()).toBe(false)
  })
})
