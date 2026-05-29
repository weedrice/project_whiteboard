<script setup lang="ts">
import { computed, ref } from 'vue'
import { useNotification } from '@/composables/useNotification'
import { useNotificationNavigation } from '@/composables/useNotificationNavigation'
import { Check } from 'lucide-vue-next'
import type { NotificationParams } from '@/api/notification'
import type { Notification } from '@/types'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useNotificationListState } from '@/composables/useNotificationListState'
import { formatTimeAgo } from '@/utils/date'

const { t } = useI18n()
const { useMarkAllAsRead } = useNotification()
const { navigateFromNotification } = useNotificationNavigation()

// Default params for dropdown
const params = ref<NotificationParams>({ page: 0, size: 20 })

// Trigger fetch via useQuery
const { isLoading, isError, errorMessage, refetch, notifications } = useNotificationListState(params, t)
const { mutate: markAllAsRead, isPending: isMarkingAllAsRead } = useMarkAllAsRead()

const hasUnreadNotifications = computed(() => notifications.value.some((notification) => !notification.isRead))

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
    class="origin-top-right absolute right-0 mt-2 w-80 rounded-md shadow-lg py-1 nv-surface ring-1 ring-black/5 focus:outline-none z-50 transition-colors duration-200">
    <div class="px-4 py-2 border-b nv-border flex justify-between items-center">
      <h3 class="text-sm font-medium nv-title">{{ $t('common.notifications') }}</h3>
      <BaseButton
        @click="handleMarkAllAsRead"
        variant="ghost"
        size="sm"
        :disabled="!hasUnreadNotifications || isMarkingAllAsRead"
        class="text-xs flex items-center p-0"
      >
        <Check class="h-3 w-3 mr-1" />
        {{ $t('notification.markAllRead') }}
      </BaseButton>
    </div>

    <div class="notification-scroll max-h-96 overflow-y-auto">
      <div v-if="isLoading && notifications.length === 0" class="px-4 py-4 text-center">
        <div class="notification-spinner animate-spin rounded-full h-5 w-5 border-b-2 mx-auto"></div>
      </div>

      <div v-else-if="isError" class="space-y-3 px-4 py-4 text-center text-sm text-red-600 dark:text-red-400">
        <p>{{ errorMessage }}</p>
        <BaseButton type="button" variant="secondary" size="sm" @click="() => refetch()">
          {{ $t('common.retry') }}
        </BaseButton>
      </div>

      <div v-else-if="notifications.length === 0"
        class="px-4 py-4 text-center text-sm nv-text-subtle">
        {{ $t('notification.empty') }}
      </div>

      <button v-for="notification in notifications" :key="notification.notificationId" type="button"
        @click="handleNotificationClick(notification)"
        class="block w-full px-4 py-3 text-left nv-hover-surface transition duration-150 ease-in-out border-b nv-border last:border-0"
        :class="{ 'nv-unread-surface': !notification.isRead }">
        <div class="flex items-start">
          <div class="flex-shrink-0">
            <!-- Icon based on type could go here -->
            <div
              class="h-8 w-8 rounded-full nv-avatar-fallback flex items-center justify-center font-bold">
              {{ notification.actorInitial }}
            </div>
          </div>
          <div class="ml-3 w-0 flex-1">
            <p class="text-sm font-medium nv-title">
              {{ notification.actorDisplayName }}
            </p>
            <p class="text-sm nv-text-subtle truncate">
              {{ notification.message }}
            </p>
            <p class="mt-1 text-xs nv-text-subtle">
              {{ formatTimeAgo(notification.createdAt, t) }}
            </p>
          </div>
          <div v-if="!notification.isRead" class="ml-2 flex-shrink-0">
            <span class="notification-unread-dot inline-block h-2 w-2 rounded-full"></span>
          </div>
        </div>
      </button>
    </div>

    <div class="px-4 py-2 border-t nv-border text-center">
      <router-link to="/mypage/notifications"
        class="text-xs font-medium notification-link">
        {{ $t('common.viewAll') }}
      </router-link>
    </div>
  </div>
</template>

<style scoped>
/* 스크롤 영역 다크모드: 스크롤바 색상 */
.notification-scroll {
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
  background: var(--nv-text-subtle);
}

.notification-spinner {
  border-color: color-mix(in srgb, var(--nv-accent) 24%, transparent);
  border-bottom-color: var(--nv-accent);
}

.nv-unread-surface {
  background: var(--nv-info-bg);
}

.notification-unread-dot {
  background: var(--nv-accent);
}

.notification-link {
  color: var(--nv-accent);
}

.notification-link:hover {
  color: color-mix(in srgb, var(--nv-accent) 82%, var(--nv-text) 18%);
}
</style>
