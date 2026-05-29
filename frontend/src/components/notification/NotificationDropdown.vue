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
const { isLoading, isError, error, refetch, notifications } = useNotificationListState(params)
const { mutate: markAllAsRead, isPending: isMarkingAllAsRead } = useMarkAllAsRead()

const hasUnreadNotifications = computed(() => notifications.value.some((notification) => !notification.isRead))
const errorMessage = computed(() => error.value instanceof Error ? error.value.message : t('common.messages.loadFailed'))

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
    class="origin-top-right absolute right-0 mt-2 w-80 rounded-md shadow-lg py-1 bg-white dark:bg-gray-800 ring-1 ring-black ring-opacity-5 focus:outline-none z-50 transition-colors duration-200">
    <div class="px-4 py-2 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center">
      <h3 class="text-sm font-medium text-gray-900 dark:text-white">{{ $t('common.notifications') }}</h3>
      <BaseButton
        @click="handleMarkAllAsRead"
        variant="ghost"
        size="sm"
        :disabled="!hasUnreadNotifications || isMarkingAllAsRead"
        class="text-xs text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300 flex items-center p-0"
      >
        <Check class="h-3 w-3 mr-1" />
        {{ $t('notification.markAllRead') }}
      </BaseButton>
    </div>

    <div class="notification-scroll max-h-96 overflow-y-auto">
      <div v-if="isLoading && notifications.length === 0" class="px-4 py-4 text-center">
        <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600 mx-auto"></div>
      </div>

      <div v-else-if="isError" class="space-y-3 px-4 py-4 text-center text-sm text-red-600 dark:text-red-400">
        <p>{{ errorMessage }}</p>
        <BaseButton type="button" variant="secondary" size="sm" @click="() => refetch()">
          {{ $t('common.retry') }}
        </BaseButton>
      </div>

      <div v-else-if="notifications.length === 0"
        class="px-4 py-4 text-center text-sm text-gray-500 dark:text-gray-400">
        {{ $t('notification.empty') }}
      </div>

      <button v-for="notification in notifications" :key="notification.notificationId" type="button"
        @click="handleNotificationClick(notification)"
        class="block w-full px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-gray-700 transition duration-150 ease-in-out border-b border-gray-50 dark:border-gray-700 last:border-0"
        :class="{ 'bg-blue-50 dark:bg-blue-900/20': !notification.isRead }">
        <div class="flex items-start">
          <div class="flex-shrink-0">
            <!-- Icon based on type could go here -->
            <div
              class="h-8 w-8 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold">
              {{ notification.actorInitial }}
            </div>
          </div>
          <div class="ml-3 w-0 flex-1">
            <p class="text-sm font-medium text-gray-900 dark:text-white">
              {{ notification.actorDisplayName }}
            </p>
            <p class="text-sm text-gray-500 dark:text-gray-400 truncate">
              {{ notification.message }}
            </p>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
              {{ formatTimeAgo(notification.createdAt, t) }}
            </p>
          </div>
          <div v-if="!notification.isRead" class="ml-2 flex-shrink-0">
            <span class="inline-block h-2 w-2 rounded-full bg-indigo-600 dark:bg-indigo-400"></span>
          </div>
        </div>
      </button>
    </div>

    <div class="px-4 py-2 border-t border-gray-100 dark:border-gray-700 text-center">
      <router-link to="/mypage/notifications"
        class="text-xs font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300">
        {{ $t('common.viewAll') }}
      </router-link>
    </div>
  </div>
</template>

<style scoped>
/* 스크롤 영역 다크모드: 스크롤바 색상 */
.notification-scroll {
  scrollbar-color: rgb(209 213 219) transparent;
}
.dark .notification-scroll {
  scrollbar-color: rgb(75 85 99) rgb(31 41 55);
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
  background: rgb(209 213 219);
}
.dark .notification-scroll::-webkit-scrollbar-track {
  background: rgb(31 41 55);
}
.dark .notification-scroll::-webkit-scrollbar-thumb {
  background: rgb(75 85 99);
}
.dark .notification-scroll::-webkit-scrollbar-thumb:hover {
  background: rgb(107 114 128);
}
</style>
