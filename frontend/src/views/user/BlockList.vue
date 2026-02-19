<template>
  <div class="max-w-4xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-4 sm:py-5 sm:px-6 border-b border-gray-200 dark:border-gray-700 flex items-center">
        <UserX class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white">{{ $t('user.blockList.title') }}</h3>
      </div>

      <div v-if="loading" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex items-center justify-between">
          <div class="flex items-center">
            <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" className="mr-4" />
            <div>
              <BaseSkeleton width="100px" height="16px" className="mb-1" />
              <BaseSkeleton width="150px" height="14px" />
            </div>
          </div>
          <BaseSkeleton width="80px" height="32px" />
        </div>
      </div>

      <EmptyState v-else-if="blockedUsers.length === 0" :title="$t('user.blockList.empty')" :icon="UserX" />

      <ul v-else role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
        <li v-for="user in blockedUsers" :key="user.userId"
          class="px-4 py-4 sm:px-6 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-200">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div class="flex items-center min-w-0 flex-1">
              <div
                class="flex-shrink-0 h-10 w-10 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
                <span class="text-gray-500 dark:text-gray-400 font-medium">{{ user.displayName.charAt(0).toUpperCase()
                }}</span>
              </div>
              <div class="ml-4 min-w-0">
                <p class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ user.displayName }}</p>
                <p class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ user.email }}</p>
              </div>
            </div>
            <div class="flex-shrink-0 min-h-[44px] sm:min-h-0 flex items-center">
              <BlockButton :userId="user.userId" :initialBlocked="true" @block-change="refreshList" />
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi, type BlockedUserSummary } from '@/api/user'
import BlockButton from '@/components/user/BlockButton.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { UserX } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import logger from '@/utils/logger'

const { t } = useI18n()

const blockedUsers = ref<BlockedUserSummary[]>([])
const loading = ref(false)

const fetchBlockedUsers = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getBlockList()
    if (data.success) {
      const payload = data.data
      blockedUsers.value = Array.isArray(payload)
        ? payload
        : payload.content
    }
  } catch (error: unknown) {
    logger.error('Failed to fetch blocked users:', error)
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  fetchBlockedUsers()
}

onMounted(() => {
  fetchBlockedUsers()
})
</script>
