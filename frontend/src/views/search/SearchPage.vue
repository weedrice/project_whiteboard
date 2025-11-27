<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex flex-col md:flex-row gap-8">
      <!-- Main Content -->
      <div class="flex-1">
        <div class="mb-6">
          <h2 class="text-2xl font-bold text-gray-900">Search Results</h2>
          <p v-if="searchTag" class="mt-2 text-gray-600">
            Showing results for tag: <span class="font-semibold text-indigo-600">#{{ searchTag }}</span>
          </p>
          <p v-if="searchKeyword" class="mt-2 text-gray-600">
            Showing results for keyword: <span class="font-semibold text-indigo-600">"{{ searchKeyword }}"</span>
          </p>
        </div>

        <!-- Search Input (Mobile/Tablet) -->
        <div class="md:hidden mb-6">
          <GlobalSearchInput />
        </div>

        <div v-if="loading" class="text-center py-10">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
        </div>

        <div v-else-if="posts.length === 0" class="text-center py-10 bg-white shadow rounded-lg">
          <p class="text-gray-500">No posts found.</p>
        </div>

        <div v-else class="space-y-4">
          <PostList :posts="posts" />
        </div>
      </div>

      <!-- Sidebar -->
      <div class="w-full md:w-80 space-y-6">
        <GlobalSearchInput class="hidden md:block" />
        <PopularKeywordList />
        <PopularPosts />
        <TagCloud />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { searchApi } from '@/api/search'
import PostList from '@/components/board/PostList.vue'
import GlobalSearchInput from '@/components/search/GlobalSearchInput.vue'
import PopularKeywordList from '@/components/search/PopularKeywordList.vue'
import PopularPosts from '@/components/board/PopularPosts.vue'
import TagCloud from '@/components/tag/TagCloud.vue'

const route = useRoute()
const posts = ref([])
const loading = ref(false)
const searchTag = ref('')
const searchKeyword = ref('')

const fetchPosts = async () => {
  const tag = route.query.tag
  const keyword = route.query.keyword
  
  if (!tag && !keyword) return

  searchTag.value = tag || ''
  searchKeyword.value = keyword || ''
  
  loading.value = true
  try {
    const params = {
      q: keyword || tag || '', // 'q' is required by backend
      type: tag ? 'TAG' : 'ALL', // Example mapping, backend expects 'type'
      page: 0,
      size: 20
    }
    
    const { data } = await searchApi.search({ params })
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

watch(() => route.query, () => {
  fetchPosts()
})

onMounted(() => {
  fetchPosts()
})
</script>
