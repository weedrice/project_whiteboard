<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/Pagination.vue'
import PageSizeSelector from '@/components/common/PageSizeSelector.vue'

const scraps = ref([])
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
      const newScraps = data.data.content.map(item => item.post || item)
      scraps.value = newScraps
      totalPages.value = data.data.totalPages
    }
  } catch (error) {
    console.error('스크랩 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage) => {
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
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div class="px-4 py-5 sm:px-6 flex justify-between items-center border-b border-gray-200 dark:border-gray-700">
          <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white">{{ $t('user.tabs.scraps') }}</h3>
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
      </div>
      <div v-if="scraps.length > 0">
        <PostList :posts="scraps" :show-board-name="true" :hide-no-column="true" />
        
        <div class="mt-4 flex justify-center pb-6">
          <Pagination 
            :current-page="page" 
            :total-pages="totalPages"
            @page-change="handlePageChange" 
          />
        </div>
      </div>
      
      <div v-else-if="!loading" class="text-center py-10 text-gray-500 dark:text-gray-400">
        {{ $t('user.scrapList.empty') }}
      </div>
      
      <div v-if="loading && scraps.length === 0" class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </div>
  </div>
</template>
