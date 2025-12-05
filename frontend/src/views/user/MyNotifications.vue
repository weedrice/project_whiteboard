<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { Check, Bell } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/Pagination.vue'
import PageSizeSelector from '@/components/common/PageSizeSelector.vue'

const { t } = useI18n()

const router = useRouter()
const notificationStore = useNotificationStore()

const page = ref(0)
const size = ref(15)

onMounted(() => {
  notificationStore.fetchNotifications(page.value, size.value)
})

function handlePageChange(newPage) {
  page.value = newPage
  notificationStore.fetchNotifications(page.value, size.value)
}

function handleSizeChange() {
  page.value = 0
  notificationStore.fetchNotifications(page.value, size.value)
}

async function handleNotificationClick(notification) {
  if (!notification.isRead) {
    await notificationStore.markAsRead(notification.notificationId)
  }
  
  if (notification.sourceType === 'POST' || notification.sourceType === 'COMMENT') {
    if (notification.sourceType === 'POST') {
        try {
            const { data } = await postApi.getPost(notification.sourceId)
            if (data.success && data.data.board) {
                router.push(`/board/${data.data.board.boardUrl}/post/${notification.sourceId}`)
            }
        } catch (err) {
            console.error('Failed to navigate to post:', err)
        }
    } else if (notification.sourceType === 'COMMENT') {
        try {
            const { data } = await commentApi.getComment(notification.sourceId)
            if (data.success) {
                const { boardUrl, postId } = data.data
                router.push(`/board/${boardUrl}/post/${postId}#comment-${notification.sourceId}`)
            }
        } catch (err) {
            console.error('Failed to navigate to comment:', err)
        }
    }
  }
}

function formatDate(dateString) {
  return new Date(dateString).toLocaleString()
}
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200">
        <h3 class="text-lg leading-6 font-medium text-gray-900 flex items-center">
          <Bell class="h-5 w-5 mr-2 text-gray-500" />
          {{ $t('notification.title') }}
        </h3>
        <div class="flex items-center space-x-4">
            <PageSizeSelector v-model="size" @change="handleSizeChange" />
            <button 
            @click="() => { console.log('Marking all as read'); notificationStore.markAllAsRead(); }"
            class="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
            <Check class="h-4 w-4 mr-1 text-green-500" />
            {{ $t('notification.markAllRead') }}
            </button>
        </div>
      </div>
      
      <div v-if="notificationStore.isLoading && notificationStore.notifications.length === 0" class="text-center py-10">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600 mx-auto"></div>
      </div>

      <div v-else-if="notificationStore.notifications.length === 0" class="text-center py-10 text-gray-500">
        {{ $t('notification.empty') }}
      </div>

      <ul v-else class="divide-y divide-gray-200">
        <li 
            v-for="notification in notificationStore.notifications" 
            :key="notification.notificationId"
            class="hover:bg-gray-50 transition duration-150 ease-in-out"
            :class="{ 'bg-blue-50': !notification.isRead }"
        >
          <a href="#" @click.prevent="handleNotificationClick(notification)" class="block px-4 py-4 sm:px-6">
            <div class="flex items-center justify-between">
              <div class="flex items-center">
                <div class="flex-shrink-0">
                    <div class="h-10 w-10 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold">
                        {{ notification.actor.displayName[0] }}
                    </div>
                </div>
                <div class="ml-4">
                    <div class="text-sm font-medium text-indigo-600 truncate">
                        {{ notification.actor.displayName }}
                    </div>
                    <div class="flex items-center text-sm text-gray-500">
                        {{ notification.message }}
                    </div>
                </div>
              </div>
              <div class="ml-2 flex-shrink-0 flex flex-col items-end">
                <p class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800 mb-1">
                  {{ notification.sourceType }}
                </p>
                <p class="text-xs text-gray-500">
                  {{ formatDate(notification.createdAt) }}
                </p>
              </div>
            </div>
          </a>
        </li>
      </ul>
      
      <div v-if="notificationStore.notifications.length > 0" class="bg-gray-50 px-4 py-4 sm:px-6 flex justify-center">
        <Pagination 
          :current-page="page" 
          :total-pages="notificationStore.totalPages"
          @page-change="handlePageChange" 
        />
      </div>
    </div>
  </div>
</template>