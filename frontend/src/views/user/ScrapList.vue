<script setup lang="ts">
import { computed } from 'vue'
import { useUser } from '@/composables/useUser'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
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
    loading-preset="post-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
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
