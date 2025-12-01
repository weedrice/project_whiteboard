<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/Pagination.vue'

const posts = ref([])
const loading = ref(false)
const page = ref(0)
const size = ref(20)
const totalPages = ref(0)
const totalElements = ref(0)

const fetchRecentViewed = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getRecentlyViewedPosts({
      page: page.value,
      size: size.value
    })
    
    if (data.success) {
      posts.value = data.data.content
      totalPages.value = data.data.totalPages
      totalElements.value = data.data.totalElements
    }
  } catch (error) {
    console.error('최근 읽은 글을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchRecentViewed()
}

onMounted(() => {
  fetchRecentViewed()
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">최근 읽은 글</h1>

    <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6">
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
      
      <div v-else-if="!loading" class="text-center py-10 text-gray-500">
        최근 읽은 글이 없습니다.
      </div>
      
      <div v-if="loading && posts.length === 0" class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </div>
  </div>
</template>
