<template>
  <PaginatedListCard
    title-tag="h1"
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
    loading-preset="avatar-action-list"
    @retry="fetchBlockedUsers"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <ul role="list" class="divide-y divide-[var(--nv-line)]">
      <li v-for="user in blockedUsers" :key="user.userId"
        class="px-4 py-4 sm:px-6 nv-hover-surface transition-colors duration-200">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div class="flex items-center min-w-0 flex-1">
            <UserAvatar :name="user.displayName" size-class="h-10 w-10" fallback-class="font-medium" />
            <div class="ml-4 min-w-0">
              <p class="text-sm font-medium nv-title truncate">{{ user.displayName }}</p>
              <p class="text-sm nv-text-subtle truncate">{{ user.secondaryText }}</p>
            </div>
          </div>
          <div class="flex-shrink-0 min-h-[44px] sm:min-h-0 flex items-center">
            <BlockButton :userId="user.userId" :initialBlocked="true" @block-change="handleBlockChange" />
          </div>
        </div>
      </li>
    </ul>

    <template #footer-meta>
      <div class="mb-3 text-center text-sm nv-text-muted">
        {{ t('common.paginationSummary.total', { count: totalElements, unit: t('common.paginationSummary.itemUnit') }) }}
      </div>
    </template>
  </PaginatedListCard>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BlockButton from '@/components/user/BlockButton.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { UserX } from 'lucide-vue-next'
import logger from '@/utils/logger'
import { useUser } from '@/features/user/useUser'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import type { BlockedUserListItem } from '@/api/user'

const { t } = useI18n()
const { useBlockList } = useUser()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: blockedUsers,
  totalElements,
  totalPages,
  isLoading: loading,
  error,
  errorMessage,
  refetch,
} = usePaginatedListState<BlockedUserListItem>(useBlockList, { initialSize: 20, t })

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
