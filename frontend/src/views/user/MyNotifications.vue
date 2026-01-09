<script setup>
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
import logger from '@/utils/logger'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

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

function handlePageChange(newPage) {
  page.value = newPage
}

function handleSizeChange() {
  page.value = 0
}

async function handleNotificationClick(notification) {
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
      } catch (err) {
        logger.error('Failed to navigate to post:', err)
      }
    } else if (notification.sourceType === 'COMMENT') {
      try {
        const { data } = await commentApi.getComment(notification.sourceId)
        if (data.success) {
          const { boardUrl, postId } = data.data
          router.push(`/board/${boardUrl}/post/${postId}#comment-${notification.sourceId}`)
        }
      } catch (err) {
        logger.error('Failed to navigate to comment:', err)
      }
    }
  }
}

import { formatDate } from '@/utils/date'
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <Bell class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400" />
          {{ $t('notification.title') }}
        </h3>
        <div class="flex items-center space-x-4">
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
          <BaseButton @click="markAllAsRead" size="sm" variant="secondary">
            <Check class="h-4 w-4 mr-1 text-green-500" />
            {{ $t('notification.markAllRead') }}
          </BaseButton>
        </div>
      </div>

      <div v-if="isLoading && notifications.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div class="flex items-center flex-1">
            <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" className="mr-4" />
            <div class="flex-1">
              <BaseSkeleton width="120px" height="16px" className="mb-1" />
              <BaseSkeleton width="200px" height="14px" />
            </div>
          </div>
          <BaseSkeleton width="60px" height="16px" className="ml-4" />
        </div>
      </div>

      <EmptyState 
        v-else-if="notifications.length === 0"
        :title="$t('notification.empty')"
        :icon="Bell"
      />

      <ul v-else class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="notification in notifications" :key="notification.notificationId"
          class="hover:bg-gray-50 dark:hover:bg-gray-700 transition duration-150 ease-in-out"
          :class="{ 'bg-blue-50 dark:bg-blue-900/20': !notification.isRead }">
          <a href="#" @click.prevent="handleNotificationClick(notification)" class="block px-4 py-4 sm:px-6">
            <div class="flex items-center justify-between">
              <div class="flex items-center">
                <div class="flex-shrink-0">
                  <div
                    class="h-10 w-10 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold">
                    {{ notification.actor.displayName[0] }}
                  </div>
                </div>
                <div class="ml-4">
                  <div class="text-sm font-medium text-indigo-600 dark:text-indigo-400 truncate">
                    {{ notification.actor.displayName }}
                  </div>
                  <div class="flex items-center text-sm text-gray-500 dark:text-gray-400">
                    {{ notification.message }}
                  </div>
                </div>
              </div>
              <div class="ml-2 flex-shrink-0 flex flex-col items-end">
                <p
                  class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 dark:bg-green-900/50 text-green-800 dark:text-green-400 mb-1">
                  {{ notification.sourceType }}
                </p>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  {{ formatDate(notification.createdAt) }}
                </p>
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
