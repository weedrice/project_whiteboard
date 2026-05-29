<script setup lang="ts">
import { computed } from 'vue'
import { useNotification } from '@/composables/useNotification'
import { useNotificationNavigation } from '@/composables/useNotificationNavigation'
import { Check, Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { useNotificationListState } from '@/composables/useNotificationListState'
import { usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { formatDate } from '@/utils/date'
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
    :title="$t('notification.title')"
    :icon="Bell"
    :items-count="notifications.length"
    :loading="isLoading"
    :error="isError ? errorMessage : null"
    :empty-title="$t('notification.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    header-class="px-3 py-3 sm:py-5 sm:px-6 gap-2"
    actions-visibility="always"
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
        <Check class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 text-green-500" />
        <span class="sm:hidden">{{ $t('notification.markAllReadShort') || $t('notification.markAllRead') }}</span>
        <span class="hidden sm:inline">{{ $t('notification.markAllRead') }}</span>
      </BaseButton>
    </template>

    <template #loading>
      <div class="divide-y divide-[var(--nv-border)]">
        <div v-for="i in 5" :key="i" class="px-3 py-3 sm:px-6 sm:py-4 flex justify-between items-center">
          <div class="flex flex-col flex-1 min-w-0">
            <BaseSkeleton width="80%" height="14px" className="mb-1" />
            <BaseSkeleton width="60%" height="12px" />
          </div>
          <BaseSkeleton width="48px" height="16px" rounded="rounded-full" />
        </div>
      </div>
    </template>

    <ul class="divide-y divide-[var(--nv-border)]">
      <li v-for="notification in notifications" :key="notification.notificationId"
        class="nv-hover-surface transition duration-150 ease-in-out"
        :class="{ 'nv-unread-surface': !notification.isRead }">
        <a href="#" @click.prevent="handleNotificationClick(notification)"
          class="block px-3 py-3 sm:px-6 sm:py-4 min-h-[48px] active:bg-[var(--nv-surface-active)]">
          <div class="flex flex-row items-center justify-between gap-3">
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2 mb-0.5">
                <span class="text-[11px] nv-text-subtle flex-shrink-0">
                  {{ formatDate(notification.createdAt) }}
                </span>
                <span
                  class="px-2 py-0.5 text-[11px] font-semibold rounded-full bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400 flex-shrink-0">
                  {{ notification.sourceType === 'POST' ? '게시글' : notification.sourceType === 'COMMENT' ? '댓글' :
                    notification.sourceType }}
                </span>
              </div>
              <div class="text-xs sm:text-sm nv-text-subtle line-clamp-2">
                {{ notification.message }}
              </div>
            </div>
          </div>
        </a>
      </li>
    </ul>
  </PaginatedListCard>
</template>
