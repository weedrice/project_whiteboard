<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex flex-col md:flex-row gap-8">
      <!-- Main Content -->
      <div class="flex-1">
        <div class="mb-6">
          <h2 class="text-2xl font-bold text-gray-900">검색 결과</h2>
          <p v-if="searchQuery" class="mt-2 text-gray-600">
            검색어: <span class="font-semibold text-indigo-600">"{{ searchQuery }}"</span>
          </p>
        </div>

        <div v-if="loading" class="text-center py-10">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
        </div>

        <div v-else-if="posts.length === 0" class="text-center py-10 bg-white shadow rounded-lg">
          <p class="text-gray-500">검색 결과가 없습니다.</p>
        </div>

        <div v-else class="space-y-4">
          <PostList :posts="posts" :showBoardName="true" :hideNoColumn="true" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { searchApi } from '@/api/search'
import PostList from '@/components/board/PostList.vue'

const route = useRoute()
const posts = ref([])
const loading = ref(false)
const searchQuery = ref('')

const fetchPosts = async () => {
  const query = route.query.q
  
  if (!query) return

  searchQuery.value = query
  
  loading.value = true
  try {
    const params = {
      q: query,
      page: 0,
      size: 20
    }
    
    const { data } = await searchApi.searchPosts(params)
    if (data.success) {
      posts.value = data.data.content
    } else {
      posts.value = []
    }
  } catch (error) {
    console.error('Failed to search posts:', error)
    posts.value = []
  } finally {
    loading.value = false
  }
}

watch(() => route.query.q, () => {
  fetchPosts()
})

onMounted(() => {
  fetchPosts()
})
</script>
