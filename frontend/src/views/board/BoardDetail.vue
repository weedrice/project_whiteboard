<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, Settings, ChevronDown, ChevronUp, User } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const board = ref(null)
const posts = ref([])
const notices = ref([])
const isLoading = ref(true)
const error = ref('')
const searchQuery = ref('')
const isSearching = ref(false)
const showAllNotices = ref(false)
const filterType = ref('all') // 'all' or 'concept'

const displayedNotices = computed(() => {
  if (showAllNotices.value) {
    return notices.value
  }
  return notices.value.slice(0, 3)
})

async function fetchBoardData() {
  isLoading.value = true
  error.value = ''
  try {
    const promises = [
      boardApi.getBoard(route.params.boardId),
      fetchPosts()
    ]
    
    // Fetch notices only if not searching
    if (!isSearching.value) {
        promises.push(boardApi.getNotices(route.params.boardId))
    }

    const [boardRes, postsRes, noticesRes] = await Promise.all(promises)

    if (boardRes.data.success) {
      board.value = boardRes.data.data
    }
    if (postsRes.data.success) {
      let fetchedPosts = postsRes.data.data.content
      if (noticesRes && noticesRes.data.success && noticesRes.data.data.length > 0) {
          // Mark notices to be sure (though they should have isNotice=true)
          const fetchedNotices = noticesRes.data.data.map(n => ({ ...n, isNotice: true }))
          // Prepend notices to posts
          fetchedPosts = [...fetchedNotices, ...fetchedPosts]
      }
      posts.value = fetchedPosts
    }
  } catch (err) {
    console.error('Failed to load board data:', err)
    error.value = 'Failed to load board information.'
  } finally {
    isLoading.value = false
  }
}

async function fetchPosts() {
    if (isSearching.value) {
        return searchApi.searchPosts({ q: searchQuery.value, boardId: route.params.boardId })
    } else {
        const minLikes = filterType.value === 'concept' ? 5 : null
        return boardApi.getPosts(route.params.boardId, { params: { minLikes } })
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

async function toggleFilter(type) {
    if (filterType.value === type) return
    filterType.value = type
    isLoading.value = true
    try {
        const res = await fetchPosts()
        if (res.data.success) {
            posts.value = res.data.data.content
        }
    } catch (err) {
        console.error(err)
    } finally {
        isLoading.value = false
    }
}

async function handleSubscribe() {
    if (!board.value) return
    try {
        if (board.value.isSubscribed) {
            await boardApi.unsubscribeBoard(board.value.boardId)
            board.value.isSubscribed = false
            board.value.subscriberCount--
        } else {
            await boardApi.subscribeBoard(board.value.boardId)
            board.value.isSubscribed = true
            board.value.subscriberCount++
        }
    } catch (err) {
        console.error('Failed to toggle subscription:', err)
        alert('구독 처리에 실패했습니다.')
    }
}

onMounted(fetchBoardData)
watch(() => route.params.boardId, () => {
  searchQuery.value = ''
  isSearching.value = false
  filterType.value = 'all'
  fetchBoardData()
})
</script>

<template>
  <div>
    <div v-if="isLoading && !board" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else-if="error" class="text-center py-10 text-red-500">
      {{ error }}
    </div>

    <div v-else-if="board">
      <!-- Board Header -->
      <div class="bg-white shadow rounded-lg mb-6 p-6">
        <div class="flex items-start">
          <router-link :to="`/board/${board.boardId}`" class="flex-shrink-0 mr-6 cursor-pointer">
              <img v-if="board.iconUrl" :src="board.iconUrl" class="h-20 w-20 rounded-full" alt="" />
              <div v-else class="h-20 w-20 rounded-full bg-indigo-100 flex items-center justify-center">
                <span class="text-indigo-600 font-bold text-3xl">{{ board.boardName[0] }}</span>
              </div>
          </router-link>
          <div class="flex-1 h-20 flex flex-col justify-between">
            <div class="flex justify-between items-start">
                <router-link :to="`/board/${board.boardId}`" class="hover:underline cursor-pointer">
                    <h1 class="text-2xl font-bold text-gray-900">{{ board.boardName }}</h1>
                </router-link>
                <div class="flex space-x-2">
                    <button
                        v-if="authStore.isAuthenticated"
                        @click="handleSubscribe"
                        class="inline-flex items-center px-3 py-1.5 border shadow-sm text-xs font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                        :class="board.isSubscribed ? 'border-gray-300 text-gray-700 bg-white hover:bg-gray-50' : 'border-transparent text-white bg-indigo-600 hover:bg-indigo-700'"
                    >
                        {{ board.isSubscribed ? '구독 취소' : '구독' }}
                    </button>
                    <router-link 
                        v-if="board.isAdmin" 
                        :to="`/board/${board.boardId}/edit`"
                        class="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                    >
                        <Settings class="-ml-1 mr-1 h-4 w-4" />
                        관리
                    </router-link>
                </div>
            </div>
            <div class="flex items-center text-sm text-gray-500 space-x-4">
                <span class="flex items-center">
                    <User class="h-4 w-4 mr-1" />
                    구독자 {{ board.subscriberCount || 0 }}명
                </span>
                <span class="flex items-center">
                    <span class="font-medium mr-1">관리자:</span> {{ board.adminDisplayName || '관리자' }}
                </span>
            </div>
            <p class="text-sm text-gray-500 line-clamp-1">{{ board.description }}</p>
          </div>
        </div>
      </div>

      <!-- Post Detail Router View -->
      <div class="mb-6">
        <router-view></router-view>
      </div>



      <!-- Filters & Post List -->
      <div class="bg-white shadow rounded-lg">
        <div class="px-4 py-3 border-b border-gray-200 flex space-x-4">
            <button 
                @click="toggleFilter('all')"
                class="px-3 py-1 text-sm font-medium rounded-md"
                :class="filterType === 'all' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
            >
                전체글
            </button>
            <button 
                @click="toggleFilter('concept')"
                class="px-3 py-1 text-sm font-medium rounded-md"
                :class="filterType === 'concept' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
            >
                개념글
            </button>
        </div>
        <PostList :posts="posts" :boardId="board.boardId" />
      </div>

      <!-- Search Bar & Write Button -->
      <div class="mt-4 px-4 py-4 sm:px-6 bg-gray-50 rounded-lg relative flex justify-center items-center">
        <div class="flex max-w-lg w-full">
            <div class="relative flex-grow">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Search class="h-5 w-5 text-gray-400" />
                </div>
                <input
                    type="text"
                    v-model="searchQuery"
                    @keyup.enter="handleSearch"
                    class="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-l-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                    placeholder="게시판 내 검색"
                />
                <div v-if="isSearching" class="absolute inset-y-0 right-2 pr-3 flex items-center">
                    <button type="button" @click="clearSearch" class="text-gray-400 hover:text-gray-500">
                    <X class="h-5 w-5" />
                    </button>
                </div>
            </div>
            <button
                @click="handleSearch"
                class="inline-flex items-center px-4 py-2 border border-l-0 border-gray-300 shadow-sm text-sm font-medium rounded-r-md text-gray-700 bg-gray-50 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap"
            >
                검색
            </button>
        </div>
        
        <div class="absolute right-6">
            <router-link 
                v-if="authStore.isAuthenticated"
                :to="`/board/${board.boardId}/write`"
                class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap"
            >
                <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
                글쓰기
            </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
