<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useNotification } from '@/composables/useNotification'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { Check, Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { formatDate } from '@/utils/date'
import logger from '@/utils/logger'
import type { Notification } from '@/types'

const { t } = useI18n()
const router = useRouter()
const { useNotifications, useMarkAsRead, useMarkAllAsRead } = useNotification()

const page = ref(0)
const size = ref(15)

const params = computed(() => ({
  page: page.value,
  size: size.value
}))

const { data: notificationsData, isLoading } = useNotifications(params)
const { mutate: markAsRead } = useMarkAsRead()
const { mutate: markAllAsRead } = useMarkAllAsRead()

const notifications = computed(() => notificationsData.value?.content || [])
const totalPages = computed(() => notificationsData.value?.totalPages || 0)

function handlePageChange(newPage: number) {
  page.value = newPage
}

function handleSizeChange() {
  page.value = 0
}

async function handleNotificationClick(notification: Notification) {
  if (!notification.isRead) {
    markAsRead(notification.notificationId)
  }

  if (notification.sourceType === 'POST' || notification.sourceType === 'COMMENT') {
    if (notification.sourceType === 'POST') {
      try {
        const { data } = await postApi.getPost(notification.sourceId)
        if (data.success && data.data.board) {
          router.push(`/board/${data.data.board.boardUrl}/post/${notification.sourceId}`)
        }
      } catch (err: unknown) {
        logger.error('Failed to navigate to post:', err)
      }
    } else if (notification.sourceType === 'COMMENT') {
      try {
        const { data } = await commentApi.getComment(notification.sourceId)
        if (data.success) {
          const comment = data.data
          const boardUrl = comment.post?.boardUrl ?? comment.boardUrl
          const postId = comment.post?.postId ?? comment.postId
          if (boardUrl && postId) {
            router.push(`/board/${boardUrl}/post/${postId}#comment-${notification.sourceId}`)
          }
        }
      } catch (err: unknown) {
        logger.error('Failed to navigate to comment:', err)
      }
    }
  }
}
</script>

<template>
  <div class="max-w-4xl mx-auto py-3 sm:py-6 md:py-8 px-3 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        class="px-3 py-3 sm:py-5 sm:px-6 flex flex-row justify-between items-center gap-2 border-b border-gray-200 dark:border-gray-700">
        <h3
          class="text-base sm:text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center flex-1 min-w-0">
          <Bell class="h-4 w-4 sm:h-5 sm:w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ $t('notification.title') }}
        </h3>
        <div class="flex items-center gap-2 flex-shrink-0">
          <div class="hidden sm:block">
            <PageSizeSelector v-model="size" @change="handleSizeChange" />
          </div>
          <BaseButton @click="() => markAllAsRead()" size="sm" variant="secondary"
            class="min-h-[36px] sm:min-h-0 text-xs sm:text-sm">
            <Check class="h-3.5 w-3.5 sm:h-4 sm:w-4 mr-1 text-green-500" />
            <span class="sm:hidden">{{ $t('notification.markAllReadShort') || $t('notification.markAllRead') }}</span>
            <span class="hidden sm:inline">{{ $t('notification.markAllRead') }}</span>
          </BaseButton>
        </div>
      </div>

      <div v-if="isLoading && notifications.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-3 py-3 sm:px-6 sm:py-4 flex justify-between items-center">
          <div class="flex flex-col flex-1 min-w-0">
            <BaseSkeleton width="80%" height="14px" className="mb-1" />
            <BaseSkeleton width="60%" height="12px" />
          </div>
          <BaseSkeleton width="48px" height="16px" rounded="rounded-full" />
        </div>
      </div>

      <EmptyState v-else-if="notifications.length === 0" :title="$t('notification.empty')" :icon="Bell" />

      <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="notification in notifications" :key="notification.notificationId"
          class="hover:bg-gray-50 dark:hover:bg-gray-700 transition duration-150 ease-in-out"
          :class="{ 'bg-blue-50 dark:bg-blue-900/20': !notification.isRead }">
          <a href="#" @click.prevent="handleNotificationClick(notification)"
            class="block px-3 py-3 sm:px-6 sm:py-4 min-h-[48px] active:bg-gray-100 dark:active:bg-gray-600">
            <div class="flex flex-row items-center justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2 mb-0.5">
                  <span class="text-[11px] text-gray-500 dark:text-gray-400 flex-shrink-0">
                    {{ formatDate(notification.createdAt) }}
                  </span>
                  <span
                    class="px-2 py-0.5 text-[11px] font-semibold rounded-full bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400 flex-shrink-0">
                    {{ notification.sourceType === 'POST' ? '게시글' : notification.sourceType === 'COMMENT' ? '댓글' :
                      notification.sourceType }}
                  </span>
                </div>
                <div class="text-xs sm:text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                  {{ notification.message }}
                </div>
              </div>
            </div>
          </a>
        </li>
      </ul>

      <div v-if="notifications.length > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>
    </div>
  </div>
</template>
