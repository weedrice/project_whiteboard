<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { postApi } from '@/api/post'
import { Check } from 'lucide-vue-next'

const router = useRouter()
const notificationStore = useNotificationStore()

onMounted(() => {
  notificationStore.fetchNotifications()
})

async function handleNotificationClick(notification) {
  if (!notification.isRead) {
    await notificationStore.markAsRead(notification.notificationId)
  }
  
  // Navigate based on source type
  if (notification.sourceType === 'POST' || notification.sourceType === 'COMMENT') {
    // Assuming sourceId is postId for now
    if (notification.sourceType === 'POST') {
        try {
            const { data } = await postApi.getPost(notification.sourceId)
            if (data.success && data.data.board) {
                router.push(`/board/${data.data.board.boardUrl}/post/${notification.sourceId}`)
            }
        } catch (err) {
            console.error('Failed to navigate to post:', err)
        }
    }
    // If we can't determine the URL, just stay here (marked as read)
  }
}

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString()
}
</script>

<template>
  <div class="origin-top-right absolute right-0 mt-2 w-80 rounded-md shadow-lg py-1 bg-white ring-1 ring-black ring-opacity-5 focus:outline-none z-50">
    <div class="px-4 py-2 border-b border-gray-100 flex justify-between items-center">
      <h3 class="text-sm font-medium text-gray-900">Notifications</h3>
      <button 
        @click="notificationStore.markAllAsRead"
        class="text-xs text-indigo-600 hover:text-indigo-500 flex items-center"
      >
        <Check class="h-3 w-3 mr-1" />
        Mark all read
      </button>
    </div>

    <div class="max-h-96 overflow-y-auto">
      <div v-if="notificationStore.isLoading && notificationStore.notifications.length === 0" class="px-4 py-4 text-center">
        <div class="animate-spin rounded-full h-5 w-5 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
      
      <div v-else-if="notificationStore.notifications.length === 0" class="px-4 py-4 text-center text-sm text-gray-500">
        No notifications.
      </div>

      <a
        v-for="notification in notificationStore.notifications"
        :key="notification.notificationId"
        href="#"
        @click.prevent="handleNotificationClick(notification)"
        class="block px-4 py-3 hover:bg-gray-50 transition duration-150 ease-in-out"
        :class="{ 'bg-blue-50': !notification.isRead }"
      >
        <div class="flex items-start">
          <div class="flex-shrink-0">
            <!-- Icon based on type could go here -->
            <div class="h-8 w-8 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold">
              {{ notification.actor.displayName[0] }}
            </div>
          </div>
          <div class="ml-3 w-0 flex-1">
            <p class="text-sm font-medium text-gray-900">
              {{ notification.actor.displayName }}
            </p>
            <p class="text-sm text-gray-500 truncate">
              {{ notification.message }}
            </p>
            <p class="mt-1 text-xs text-gray-400">
              {{ formatDate(notification.createdAt) }}
            </p>
          </div>
          <div v-if="!notification.isRead" class="ml-2 flex-shrink-0">
            <span class="inline-block h-2 w-2 rounded-full bg-indigo-600"></span>
          </div>
        </div>
      </a>
    </div>
  </div>
</template>
