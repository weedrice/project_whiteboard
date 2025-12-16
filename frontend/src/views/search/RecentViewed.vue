<script setup>
import { ref, computed } from 'vue'
import { useUser } from '@/composables/useUser'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'

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
const totalElements = computed(() => recentData.value?.totalElements || 0)

const handlePageChange = (newPage) => {
  page.value = newPage
}

const handleSizeChange = () => {
  page.value = 0
}
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg mb-6 transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200 dark:border-gray-700">
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.tabs.recent') }}</h3>
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
      </div>
      <div v-if="posts.length > 0">
        <PostList 
          :posts="posts" 
          :show-board-name="true" 
          :hide-no-column="true"
        />
        
        <div class="mt-4 flex justify-center pb-6">
          <Pagination 
            :current-page="page" 
            :total-pages="totalPages"
            @page-change="handlePageChange" 
          />
        </div>
      </div>
      
      <div v-else-if="!loading" class="text-center py-10 text-gray-500 dark:text-gray-400">
        {{ $t('user.recentViewed.empty') }}
      </div>
      
      <div v-if="loading && posts.length === 0" class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </div>
  </div>
</template>

