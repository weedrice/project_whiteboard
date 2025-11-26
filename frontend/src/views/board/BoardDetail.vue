<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, Settings } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const board = ref(null)
const posts = ref([])
const isLoading = ref(true)
const error = ref('')
const searchQuery = ref('')
const isSearching = ref(false)

async function fetchBoardData() {
  isLoading.value = true
  error.value = ''
  try {
    const [boardRes, postsRes] = await Promise.all([
      boardApi.getBoard(route.params.boardId),
      isSearching.value 
        ? searchApi.searchPosts({ q: searchQuery.value, boardId: route.params.boardId })
        : boardApi.getPosts(route.params.boardId)
    ])

    if (boardRes.data.success) {
      board.value = boardRes.data.data
    }
    if (postsRes.data.success) {
      posts.value = postsRes.data.data.content
    }
  } catch (err) {
    console.error('Failed to load board data:', err)
    error.value = 'Failed to load board information.'
  } finally {
    isLoading.value = false
  }
}

async function handleSearch() {
  if (!searchQuery.value.trim()) return
  isSearching.value = true
  await fetchBoardData()
}

async function clearSearch() {
  searchQuery.value = ''
  isSearching.value = false
  await fetchBoardData()
}

onMounted(fetchBoardData)
watch(() => route.params.boardId, () => {
  searchQuery.value = ''
  isSearching.value = false
  fetchBoardData()
})
</script>

<template>
  <div>
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else-if="board">
      <!-- Board Header -->
      <div class="bg-white shadow rounded-lg mb-6 p-6">
        <div class="flex items-center justify-between">
          <div class="flex items-center">
            <img v-if="board.iconUrl" :src="board.iconUrl" class="h-16 w-16 rounded-full mr-4" alt="" />
            <div v-else class="h-16 w-16 rounded-full bg-indigo-100 flex items-center justify-center mr-4">
              <span class="text-indigo-600 font-bold text-2xl">{{ board.boardName[0] }}</span>
            </div>
            <div>
              <h1 class="text-2xl font-bold text-gray-900">{{ board.boardName }}</h1>
              <p class="text-gray-500">{{ board.description }}</p>
            </div>
          </div>
          <div v-if="authStore.isAuthenticated" class="flex space-x-2">
            <router-link 
              v-if="authStore.user?.role === 'ADMIN'"
              :to="`/board/${board.boardId}/edit`"
              class="inline-flex items-center px-4 py-2 border border-gray-300 shadow-sm text-sm font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              <Settings class="-ml-1 mr-2 h-5 w-5" />
              Settings
            </router-link>
            <router-link 
              :to="`/board/${board.boardId}/write`"
              class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            >
              <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
              New Post
            </router-link>
          </div>
        </div>
      </div>

      <!-- Post List -->
      <div class="border-t border-gray-200">
        <PostList :posts="posts" :boardId="board.boardId" />
      </div>

      <!-- Search Bar -->
      <div class="px-4 py-4 sm:px-6 border-t border-gray-200 bg-gray-50">
        <form @submit.prevent="handleSearch" class="flex items-center justify-center max-w-lg mx-auto">
          <div class="relative flex-grow">
            <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search class="h-5 w-5 text-gray-400" />
            </div>
            <input
              type="text"
              v-model="searchQuery"
              class="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
              placeholder="Search in this board..."
            />
            <div v-if="isSearching" class="absolute inset-y-0 right-0 pr-3 flex items-center">
              <button type="button" @click="clearSearch" class="text-gray-400 hover:text-gray-500">
                <X class="h-5 w-5" />
              </button>
            </div>
          </div>
          <button
            type="submit"
            class="ml-3 inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
          >
            Search
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
