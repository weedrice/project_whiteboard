<script setup lang="ts">
import { computed } from 'vue'
import { useNotification } from '@/features/notifications/queries/useNotification'
import { useNotificationNavigation } from '@/features/notifications/navigation/useNotificationNavigation'
import { Check, Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useNotificationListState } from '@/features/notifications/list/useNotificationListState'
import { usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { formatDate } from '@/utils/date'
import NotificationListItem from '@/components/notification/NotificationListItem.vue'
import type { Notification } from '@/types'

const { t } = useI18n()
const { useMarkAllAsRead, useUnreadCount } = useNotification()
const { navigateFromNotification } = useNotificationNavigation({ showCommentFailureToast: true })

const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({ initialSize: 15 })

const { isLoading, isError, errorMessage, refetch, notifications, totalPages } = useNotificationListState(params, t)
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
  <PaginatedListCard
    title-tag="h1"
    :title="$t('notification.title')"
    :icon="Bell"
    :items-count="notifications.length"
    :loading="isLoading"
    :error="isError ? errorMessage : null"
    :empty-title="$t('notification.empty')"
    :empty-description="$t('notification.emptyDescription')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    header-class="px-3 py-3 sm:py-5 sm:px-6 gap-2"
    actions-visibility="always"
    loading-preset="notification-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #header-actions>
      <BaseButton
        @click="handleMarkAllAsRead"
        size="sm"
        variant="secondary"
        :disabled="!hasUnreadNotifications || isMarkingAllAsRead"
        class="min-h-[36px] sm:min-h-0 text-xs sm:text-sm"
      >
        <Check class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 text-[var(--nv-success-text)]" />
        <span class="sm:hidden">{{ $t('notification.markAllReadShort') || $t('notification.markAllRead') }}</span>
        <span class="hidden sm:inline">{{ $t('notification.markAllRead') }}</span>
      </BaseButton>
    </template>

    <ul class="divide-y divide-[var(--nv-line)]">
      <li v-for="notification in notifications" :key="notification.notificationId">
        <NotificationListItem
          :notification="notification"
          mode="page"
          :time-text="formatDate(notification.createdAt)"
          @activate="handleNotificationClick"
        />
      </li>
    </ul>
  </PaginatedListCard>
</template>
