<script setup lang="ts">
import { computed, ref } from 'vue'
import { useNotification } from '@/features/notifications/queries/useNotification'
import { useNotificationNavigation } from '@/features/notifications/navigation/useNotificationNavigation'
import { Check } from 'lucide-vue-next'
import type { NotificationParams } from '@/api/notification'
import type { Notification } from '@/types'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import NotificationListItem from '@/components/notification/NotificationListItem.vue'
import { useNotificationListState } from '@/features/notifications/list/useNotificationListState'
import { formatTimeAgo } from '@/utils/date'

const { t } = useI18n()
const { useMarkAllAsRead, useUnreadCount } = useNotification()
const { navigateFromNotification } = useNotificationNavigation()

// Default params for dropdown
const params = ref<NotificationParams>({ page: 0, size: 20 })

// Trigger fetch via useQuery
const { isLoading, isError, errorMessage, refetch, notifications } = useNotificationListState(params, t)
const { data: unreadCount } = useUnreadCount()
const { mutate: markAllAsRead, isPending: isMarkingAllAsRead } = useMarkAllAsRead()

const hasUnreadNotifications = computed(() => (unreadCount.value ?? 0) > 0)

async function handleNotificationClick(notification: Notification) {
  await navigateFromNotification(notification)
}

function handleMarkAllAsRead() {
  if (!hasUnreadNotifications.value || isMarkingAllAsRead.value) {
    return
  }

  markAllAsRead()
}
</script>

<template>
  <div
    role="dialog"
    data-dialog-mode="non-modal"
    aria-labelledby="notification-dropdown-title"
    class="nv-dialog-surface nv-dialog-surface--popover origin-top-right absolute right-0 mt-2 w-full py-1 focus:outline-none z-50 transition-colors duration-200">
    <div class="px-4 py-2 border-b nv-border flex justify-between items-center">
      <h2 id="notification-dropdown-title" class="text-sm font-medium nv-title">{{ $t('common.notifications') }}</h2>
      <BaseButton
        @click="handleMarkAllAsRead"
        variant="ghost"
        size="sm"
        :disabled="!hasUnreadNotifications || isMarkingAllAsRead"
        class="min-h-11 text-xs flex items-center p-0"
      >
        <Check class="h-3 w-3 mr-1" />
        {{ $t('notification.markAllRead') }}
      </BaseButton>
    </div>

    <div class="notification-scroll overflow-y-auto">
      <div v-if="isLoading && notifications.length === 0" class="px-4 py-4 text-center">
        <div class="notification-spinner mx-auto h-5 w-5 flex items-center justify-center">
          <BaseSpinner size="sm" class="scale-125" />
        </div>
      </div>

      <div v-else-if="isError" class="space-y-3 px-4 py-4 text-center text-sm nv-form-error">
        <p>{{ errorMessage }}</p>
        <BaseButton type="button" variant="secondary" size="sm" @click="() => refetch()">
          {{ $t('common.error.retry') }}
        </BaseButton>
      </div>

      <div v-else-if="notifications.length === 0"
        class="px-4 py-4 text-center text-sm nv-text-subtle">
        {{ $t('notification.empty') }}
      </div>

      <NotificationListItem
        v-for="notification in notifications"
        :key="notification.notificationId"
        :notification="notification"
        mode="dropdown"
        :time-text="formatTimeAgo(notification.createdAt, t)"
        @activate="handleNotificationClick"
      />
    </div>

    <div class="px-4 py-2 border-t nv-border text-center">
      <router-link to="/mypage/notifications"
        class="notification-link inline-flex min-h-11 w-full items-center justify-center rounded-md text-xs font-medium">
        {{ $t('common.viewAll') }}
      </router-link>
    </div>
  </div>
</template>

<style scoped>
/* 스크롤 영역 다크모드: 스크롤바 색상 */
.notification-scroll {
  max-height: clamp(10rem, calc(100dvh - 12rem), 24rem);
  scrollbar-color: var(--nv-border-strong) transparent;
}
.dark .notification-scroll {
  scrollbar-color: var(--nv-border-strong) var(--nv-surface-muted);
}
/* WebKit (Chrome, Safari, Edge) */
.notification-scroll::-webkit-scrollbar {
  width: 6px;
}
.notification-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.notification-scroll::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: var(--nv-border-strong);
}
.dark .notification-scroll::-webkit-scrollbar-track {
  background: var(--nv-surface-muted);
}
.dark .notification-scroll::-webkit-scrollbar-thumb {
  background: var(--nv-border-strong);
}
.dark .notification-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--nv-muted);
}

.notification-link {
  color: var(--nv-accent);
}

.notification-link:hover {
  color: color-mix(in srgb, var(--nv-accent) 82%, var(--nv-ink) 18%);
}

</style>
