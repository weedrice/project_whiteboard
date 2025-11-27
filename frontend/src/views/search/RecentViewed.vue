<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'
import PostList from '@/components/board/PostList.vue'

const posts = ref([])
const loading = ref(false)

const fetchRecentViewed = async () => {
  loading.value = true
  try {
    // const { data } = await axios.get('/search/recent')
    // Backend API '/search/recent' returns recent SEARCH keywords, not viewed posts.
    // Since the requirement is "Recently Read Posts", we need a different API or mock it.
    // Currently mocking as per plan.
    
    await new Promise(resolve => setTimeout(resolve, 500))
    posts.value = [] // Mock empty or some data
  } catch (error) {
    console.error('최근 본 글을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchRecentViewed()
})
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">최근 읽은 글</h1>

    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <div v-if="posts.length > 0">
        <PostList :posts="posts" />
      </div>
      
      <div v-else-if="!loading" class="text-center py-10 text-gray-500">
        최근 읽은 글이 없습니다.
      </div>
      
      <div v-if="loading" class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </div>
  </div>
</template>
