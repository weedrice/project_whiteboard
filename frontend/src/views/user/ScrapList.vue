<script setup lang="ts">
import { computed } from 'vue'
import { useUser } from '@/composables/useUser'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { Bookmark } from 'lucide-vue-next'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const { useMyScraps } = useUser()
const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({ initialSize: 15 })
const { data: scrapsData, isLoading: loading, error, refetch } = useMyScraps(params)
const { items: scraps, totalPages } = usePageResponseState(scrapsData, page)
const errorMessage = computed(() => error.value ? getListLoadErrorMessage(t) : '')
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.scraps')"
    :icon="Bookmark"
    :items-count="scraps.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.scrapList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="divide-y divide-[var(--nv-border)]">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div class="w-full">
            <BaseSkeleton width="70%" height="24px" className="mb-2" />
            <div class="flex gap-2">
              <BaseSkeleton width="40px" height="16px" />
              <BaseSkeleton width="60px" height="16px" />
            </div>
          </div>
        </div>
      </div>
    </template>

    <PostList
      :posts="scraps"
      :show-board-name="true"
      :hide-no-column="true"
      :show-notice-badge="false"
      :show-comment-count="false"
      :show-preview-indicator="false"
      :show-secret-indicator="false"
      :resolve-post-route="resolvePostDetailRoute"
      :resolve-board-route="resolveBoardRoute"
      :show-inquiry-status="isInquiryPostItem"
    />
  </PaginatedListCard>
</template>
