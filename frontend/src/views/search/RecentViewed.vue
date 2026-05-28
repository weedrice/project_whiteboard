<script setup lang="ts">
import { ref, computed } from 'vue'
import { useUser } from '@/composables/useUser'
import { Clock } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const { useRecentlyViewedPosts } = useUser()

const page = ref(0)
const size = ref(15)

const params = computed(() => ({
  page: page.value,
  size: size.value
}))

const { data: recentData, isLoading: loading } = useRecentlyViewedPosts(params)

const posts = computed(() => recentData.value?.content || [])
const totalPages = computed(() => recentData.value?.totalPages || 0)

const handlePageChange = (newPage: number) => {
  page.value = newPage
}

const handleSizeChange = (newSize = size.value) => {
  size.value = newSize
  page.value = 0
}
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.recent')"
    :icon="Clock"
    :items-count="posts.length"
    :loading="loading"
    :error="null"
    :empty-title="$t('user.recentViewed.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </template>

    <PostList
      :posts="posts"
      :show-board-name="true"
      :hide-no-column="true"
      :resolve-post-route="resolvePostDetailRoute"
      :resolve-board-route="resolveBoardRoute"
      :show-inquiry-status="isInquiryPostItem"
    />
  </PaginatedListCard>
</template>
