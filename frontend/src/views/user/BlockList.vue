<template>
  <PaginatedListCard
    :title="$t('user.blockList.title')"
    :icon="UserX"
    :items-count="blockedUsers.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.blockList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    :page-size-options="[20, 50, 100]"
    @retry="fetchBlockedUsers"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="divide-y divide-gray-200 dark:divide-gray-700">
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
    </template>

    <ul role="list" class="divide-y divide-gray-200 dark:divide-gray-700">
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
            <BlockButton :userId="user.userId" :initialBlocked="true" @block-change="handleBlockChange" />
          </div>
        </div>
      </li>
    </ul>

    <template #footer-meta>
      <div class="mb-3 text-center text-sm text-gray-600 dark:text-gray-300">총 {{ totalElements }}건</div>
    </template>
  </PaginatedListCard>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BlockedUserSummary } from '@/api/user'
import BlockButton from '@/components/user/BlockButton.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { UserX } from 'lucide-vue-next'
import logger from '@/utils/logger'
import { useUser } from '@/composables/useUser'
import { getListLoadErrorMessage } from '@/utils/listLoadError'

const { t } = useI18n()
const { useBlockList } = useUser()
const page = ref(0)
const size = ref(20)
const blockListParams = computed(() => ({ page: page.value, size: size.value }))
const { data: blockListData, isLoading: loading, error, refetch } = useBlockList(blockListParams)

const blockedUsers = computed<BlockedUserSummary[]>(() => {
  const payload = blockListData.value
  if (!payload) return []
  return Array.isArray(payload) ? payload : payload.content
})

const totalElements = computed(() => {
  const payload = blockListData.value
  if (!payload) return 0
  return Array.isArray(payload) ? payload.length : payload.totalElements
})

const totalPages = computed(() => {
  const payload = blockListData.value
  if (!payload) return 0
  return Array.isArray(payload) ? (payload.length > 0 ? 1 : 0) : payload.totalPages
})

const handlePageChange = (nextPage: number) => {
  page.value = nextPage
}

const handleSizeChange = (nextSize = size.value) => {
  size.value = nextSize
  page.value = 0
}

const errorMessage = computed(() => error.value ? getListLoadErrorMessage(t) : '')

const fetchBlockedUsers = () => {
  refetch()
}

const handleBlockChange = () => {
  if (page.value > 0 && blockedUsers.value.length === 1) {
    page.value -= 1
  }
}

watch(error, (value) => {
  if (value) {
    logger.error('Failed to fetch blocked users:', value)
  }
})
</script>
