<script setup lang="ts">
import { computed } from 'vue'
import { useNotification } from '@/composables/useNotification'
import { useNotificationNavigation } from '@/composables/useNotificationNavigation'
import { Check, Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import { useNotificationListState } from '@/composables/useNotificationListState'
import { usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { formatDate } from '@/utils/date'
import { getNotificationPresentation } from '@/features/notifications/notificationPresentation'
import type { Notification } from '@/types'

const { t } = useI18n()
const { useMarkAllAsRead } = useNotification()
const { navigateFromNotification } = useNotificationNavigation({ showCommentFailureToast: true })

const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({ initialSize: 15 })

const { isLoading, isError, errorMessage, refetch, notifications, totalPages } = useNotificationListState(params, t)
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
      <li v-for="notification in notifications" :key="notification.notificationId"
        class="nv-hover-surface transition duration-150 ease-in-out"
        :class="{ 'nv-unread-surface': !notification.isRead }">
        <button type="button" @click="handleNotificationClick(notification)"
          class="block w-full px-3 py-3 text-left sm:px-6 sm:py-4 min-h-[48px] active:bg-[var(--nv-surface-active)]">
          <div class="flex flex-row items-center justify-between gap-3">
            <span
              class="notification-icon flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full"
              :class="getNotificationPresentation(notification).iconClass"
              aria-hidden="true"
            >
              <component :is="getNotificationPresentation(notification).icon" class="h-4 w-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2 mb-0.5">
                <span class="text-xs nv-text-subtle flex-shrink-0">
                  {{ formatDate(notification.createdAt) }}
                </span>
                <span
                  class="px-2 py-0.5 text-xs font-semibold rounded-full flex-shrink-0"
                  :class="getNotificationPresentation(notification).badgeClass"
                >
                  {{ t(getNotificationPresentation(notification).labelKey) }}
                </span>
              </div>
              <div class="text-xs sm:text-sm nv-text-subtle line-clamp-2">
                {{ notification.message }}
              </div>
              <div v-if="notification.grouped" class="mt-1 text-xs font-medium nv-accent-text">
                {{ t('notification.groupedCount', { count: notification.groupCount }) }}
              </div>
            </div>
          </div>
        </button>
      </li>
    </ul>
  </PaginatedListCard>
</template>

<style scoped>
.notification-icon-default,
.notification-badge-default {
  background: var(--nv-surface-muted);
  color: var(--nv-muted);
}

.notification-icon-like,
.notification-badge-like {
  background: var(--nv-danger-bg);
  color: var(--nv-danger-text);
}

.notification-icon-comment,
.notification-badge-comment,
.notification-icon-reply,
.notification-badge-reply {
  background: var(--nv-info-bg);
  color: var(--nv-info-text);
}

.notification-icon-mention,
.notification-badge-mention {
  background: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-surface));
  color: var(--nv-accent);
}

.notification-icon-message,
.notification-badge-message {
  background: var(--nv-success-bg);
  color: var(--nv-success-text);
}

.notification-icon-system,
.notification-badge-system {
  background: var(--nv-success-bg);
  color: var(--nv-success-text);
}

.notification-icon-sanction,
.notification-badge-sanction {
  background: var(--nv-warning-bg);
  color: var(--nv-warning-text);
}
</style>
