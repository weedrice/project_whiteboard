<template>
  <div class="card p-4">
    <h3 class="text-lg font-medium text-gray-900 mb-4">Popular Posts</h3>
    
    <div v-if="loading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else-if="posts.length === 0" class="text-gray-500 text-sm">
      No popular posts yet.
    </div>
    
    <ul v-else class="space-y-3">
      <li v-for="post in posts" :key="post.postId" class="border-b border-gray-100 last:border-0 pb-2 last:pb-0">
        <router-link 
          :to="`/board/${post.boardUrl}/post/${post.postId}`"
          class="block hover:bg-gray-50 rounded p-1 -m-1 transition-colors"
        >
          <p class="text-sm font-medium text-gray-900 truncate">{{ post.title }}</p>
          <div class="mt-1 flex items-center text-xs text-gray-500">
            <span class="mr-2">{{ post.authorName }}</span>
            <span class="flex items-center">
              <svg class="h-3 w-3 mr-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5" />
              </svg>
              {{ post.likeCount }}
            </span>
          </div>
        </router-link>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'

const posts = ref([])
const loading = ref(false)

// Mock data
const mockPosts = [
  { postId: 1, boardUrl: 1, title: 'Getting Started with Vue 3', authorName: 'Alice', likeCount: 42 },
  { postId: 2, boardUrl: 1, title: 'Understanding Pinia Stores', authorName: 'Bob', likeCount: 38 },
  { postId: 3, boardUrl: 2, title: 'Tailwind CSS Tips & Tricks', authorName: 'Charlie', likeCount: 25 },
  { postId: 4, boardUrl: 1, title: 'Vite vs Webpack', authorName: 'Dave', likeCount: 20 },
  { postId: 5, boardUrl: 3, title: 'JavaScript ES6 Features', authorName: 'Eve', likeCount: 15 }
]

const fetchPopularPosts = async () => {
  loading.value = true
  try {
    // const { data } = await axios.get('/api/posts/popular')
    // if (data.success) {
    //   posts.value = data.data
    // }
    
    await new Promise(resolve => setTimeout(resolve, 500))
    posts.value = mockPosts
  } catch (error) {
    console.error('Failed to fetch popular posts:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchPopularPosts()
})
</script>
