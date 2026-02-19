<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import { Bookmark } from 'lucide-vue-next'
import logger from '@/utils/logger'
import type { PostSummary } from '@/types'

const scraps = ref<PostSummary[]>([])
const loading = ref(false)
const page = ref(0)
const totalPages = ref(0)
const size = ref(15)

const fetchScraps = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getMyScraps({
      page: page.value,
      size: size.value
    })
    if (data.success) {
      const newScraps = data.data.content.map((item: PostSummary & { post?: PostSummary }) => item.post || item)
      scraps.value = newScraps
      totalPages.value = data.data.totalPages
    }
  } catch (error: unknown) {
    logger.error('Failed to load scraps:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage: number) => {
  page.value = newPage
  fetchScraps()
}

const handleSizeChange = () => {
  page.value = 0
  fetchScraps()
}

onMounted(() => {
  fetchScraps()
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <Bookmark class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ $t('user.tabs.scraps') }}
        </h3>
        <div class="hidden sm:block">
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
        </div>
      </div>
      <div v-if="loading && scraps.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
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
      <EmptyState v-else-if="scraps.length === 0" :title="$t('user.scrapList.empty')" :icon="Bookmark" />
      <div v-else>
        <PostList :posts="scraps" :show-board-name="true" :hide-no-column="true" />
        <div class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
          <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
        </div>
      </div>
    </div>
  </div>
</template>
