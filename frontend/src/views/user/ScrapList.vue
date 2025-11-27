<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'

const scraps = ref([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)

const fetchScraps = async () => {
  loading.value = true
  try {
    const { data } = await userApi.getMyScraps({
      page: page.value,
      size: 20
    })
    if (data.success) {
      const newScraps = data.data.content.map(item => item.post || item)
      
      if (page.value === 0) {
        scraps.value = newScraps
      } else {
        scraps.value.push(...newScraps)
      }
      hasMore.value = !data.data.last
    }
  } catch (error) {
    console.error('스크랩 목록을 불러오는데 실패했습니다:', error)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value++
  fetchScraps()
}

onMounted(() => {
  fetchScraps()
})
</script>

<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <h1 class="text-2xl font-bold text-gray-900 mb-6">스크랩 목록</h1>

    <div class="bg-white shadow overflow-hidden sm:rounded-lg">
      <div v-if="scraps.length > 0">
        <PostList :posts="scraps" />
        
        <div v-if="hasMore" class="bg-gray-50 px-4 py-4 sm:px-6 text-center border-t border-gray-200">
          <button 
            @click="loadMore" 
            :disabled="loading"
            class="text-sm font-medium text-indigo-600 hover:text-indigo-500 disabled:opacity-50"
          >
            {{ loading ? '로딩 중...' : '더 보기' }}
          </button>
        </div>
      </div>
      
      <div v-else-if="!loading" class="text-center py-10 text-gray-500">
        스크랩한 게시글이 없습니다.
      </div>
      
      <div v-if="loading && scraps.length === 0" class="text-center py-10">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
      </div>
    </div>
  </div>
</template>
