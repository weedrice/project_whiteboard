<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, Settings, ChevronDown, ChevronUp, User } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const route = useRoute()
const authStore = useAuthStore()

const board = ref(null)
const posts = ref([])
const notices = ref([])
const categories = ref([]) // 추가: 카테고리 목록
const totalCount = ref(0)
const page = ref(0)
const size = ref(20)
const isLoading = ref(true)
const error = ref('')
const searchQuery = ref('')
const searchType = ref('TITLE_CONTENT')
const isSearching = ref(false)
const showAllNotices = ref(false)
const filterType = ref('all') // 'all', 'concept', or 'category'
const activeFilterCategory = ref(null) // Stores the categoryId when filtering by category
const sort = ref('createdAt,desc')

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
      boardApi.getBoard(route.params.boardUrl, { redirectOnError: true }),
      fetchPosts()
    ]
    
    // Fetch notices only if not searching
    if (!isSearching.value) {
        promises.push(boardApi.getNotices(route.params.boardUrl))
    }

    const [boardRes, postsRes, noticesRes] = await Promise.all(promises)

    if (boardRes.data.success) {
      board.value = boardRes.data.data
      // '일반' 카테고리를 제외하고 필터링에 사용할 카테고리 목록을 설정
      categories.value = board.value.categories.filter(cat => cat.name !== '일반')
    }
              if (postsRes.data.success) {
                let fetchedPosts = postsRes.data.data.content
                totalCount.value = postsRes.data.data.totalElements
                page.value = postsRes.data.data.page
                size.value = postsRes.data.data.size
          
                if (noticesRes && noticesRes.data.success && noticesRes.data.data.length > 0) {                const fetchedNotices = noticesRes.data.data.map(n => ({ ...n, isNotice: true }))
                notices.value = fetchedNotices
                fetchedPosts = [...fetchedNotices, ...fetchedPosts]
            } else if (!isSearching.value && notices.value.length > 0) {
                 // If notices were already fetched (e.g. returning from post detail), keep them
                 // But fetchBoardData is called on mount/watch.
                 // If noticesRes is null (because we didn't fetch it?), we might lose them if we don't persist.
                 // But fetchBoardData always fetches notices if !isSearching.
            }
            posts.value = fetchedPosts
          }
        } catch (err) {
          logger.error('Failed to load board data:', err)
          error.value = t('board.loadFailed')
        } finally {
          isLoading.value = false
        }
      }
    
      async function fetchPosts() {
          if (isSearching.value) {
              return searchApi.searchPosts({ 
                  q: searchQuery.value, 
                  searchType: searchType.value,
                  boardUrl: route.params.boardUrl 
              })
          } else {              const params = {
                  minLikes: null,
                  categoryId: null // Changed from 'category'
              }
              if (filterType.value === 'concept') {
                  params.minLikes = 5
              } else if (filterType.value === 'category' && activeFilterCategory.value !== null) {
                  params.categoryId = activeFilterCategory.value // Pass categoryId
              }
              params.sort = sort.value
              return boardApi.getPosts(route.params.boardUrl, params)
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
    
      async function toggleFilter(type, categoryId = null) {
          if (type === 'all') {
              if (filterType.value === 'all' && activeFilterCategory.value === null) return
              filterType.value = 'all'
              activeFilterCategory.value = null
          } else if (type === 'concept') {
              if (filterType.value === 'concept') return
              filterType.value = 'concept'
              activeFilterCategory.value = null
          } else if (type === 'category' && categoryId !== null) {
              if (filterType.value === 'category' && activeFilterCategory.value === categoryId) return
              filterType.value = 'category'
              activeFilterCategory.value = categoryId
          } else {
              return // Invalid filter type
          }
      
          isLoading.value = true
          try {
                        const res = await fetchPosts()
                        if (res.data.success) {
                            let fetchedPosts = res.data.data.content
                            totalCount.value = res.data.data.totalElements
                            page.value = res.data.data.page
                            size.value = res.data.data.size
              
                            if (notices.value.length > 0 && !isSearching.value) {
                                fetchedPosts = [...notices.value, ...fetchedPosts]
                            }
                            posts.value = fetchedPosts
                        }          } catch (err) {
              logger.error(err)
          } finally {
              isLoading.value = false
          }
      }
async function handleSortChange(newSort) {
    sort.value = newSort
    isLoading.value = true
    try {
        const res = await fetchPosts()
        if (res.data.success) {
            let fetchedPosts = res.data.data.content
            totalCount.value = res.data.data.totalElements
            page.value = res.data.data.page
            size.value = res.data.data.size

            if (notices.value.length > 0 && !isSearching.value) {
                fetchedPosts = [...notices.value, ...fetchedPosts]
            }
            posts.value = fetchedPosts
        }
    } catch (err) {
        logger.error(err)
    } finally {
        isLoading.value = false
    }
}

async function handleSubscribe() {
    if (!board.value) return
    try {
        if (board.value.isSubscribed) {
            await boardApi.unsubscribeBoard(board.value.boardUrl)
            board.value.isSubscribed = false
            board.value.subscriberCount--
        } else {
            await boardApi.subscribeBoard(board.value.boardUrl)
            board.value.isSubscribed = true
            board.value.subscriberCount++
        }
    } catch (err) {
        logger.error('Failed to toggle subscription:', err)
        alert(t('board.detail.subscribeFailed'))
    }
}

onMounted(() => {
  activeFilterCategory.value = null; // Reset category filter on mount
  fetchBoardData();
});
watch(() => route.params.boardUrl, () => {
  searchQuery.value = ''
  isSearching.value = false
  filterType.value = 'all'
  activeFilterCategory.value = null; // Reset category filter on board change
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
          <router-link :to="`/board/${board.boardUrl}`" class="flex-shrink-0 mr-6 cursor-pointer">
              <img v-if="board.iconUrl" :src="board.iconUrl" class="h-20 w-20 rounded-full" alt="" />
              <div v-else class="h-20 w-20 rounded-full bg-indigo-100 flex items-center justify-center">
                <span class="text-indigo-600 font-bold text-3xl">{{ board.boardName[0] }}</span>
              </div>
          </router-link>
          <div class="flex-1 h-20 flex flex-col justify-between">
            <div class="flex justify-between items-start">
                <router-link :to="`/board/${board.boardUrl}`" class="hover:underline cursor-pointer">
                    <h1 class="text-2xl font-bold text-gray-900">{{ board.boardName }}</h1>
                </router-link>
                <div class="flex space-x-2">
                    <button
                        v-if="authStore.isAuthenticated"
                        @click="handleSubscribe"
                        class="inline-flex items-center px-3 py-1.5 border shadow-sm text-xs font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                        :class="board.isSubscribed ? 'border-gray-300 text-gray-700 bg-white hover:bg-gray-50' : 'border-transparent text-white bg-indigo-600 hover:bg-indigo-700'"
                    >
                        {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                    </button>
                    <router-link 
                        v-if="board.isAdmin" 
                        :to="`/board/${board.boardUrl}/edit`"
                        class="inline-flex items-center px-3 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                    >
                        <Settings class="-ml-1 mr-1 h-4 w-4" />
                        {{ $t('common.manage') }}
                    </router-link>
                </div>
            </div>
            <div class="flex items-center text-sm text-gray-500 space-x-4">
                <span class="flex items-center">
                    <User class="h-4 w-4 mr-1" />
                    {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
                </span>
                <span class="flex items-center">
                    <span class="font-medium mr-1">{{ $t('common.admin') }}</span> 
                    <UserMenu 
                        v-if="board.adminUserId"
                        :user-id="board.adminUserId"
                        :display-name="board.adminDisplayName || $t('common.defaultAdminName')"
                    />
                    <span v-else>{{ board.adminDisplayName || $t('board.detail.defaultAdminName') }}</span>
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
                {{ $t('board.detail.filter.all') }}
            </button>
            <button 
                @click="toggleFilter('concept')"
                class="px-3 py-1 text-sm font-medium rounded-md"
                :class="filterType === 'concept' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
            >
                {{ $t('board.detail.filter.concept') }}
            </button>
            <button
                v-for="category in categories"
                :key="category.categoryId"
                @click="toggleFilter('category', category.categoryId)"
                class="px-3 py-1 text-sm font-medium rounded-md"
                :class="filterType === 'category' && activeFilterCategory === category.categoryId ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:text-gray-700'"
            >
                {{ category.name }}
            </button>
        </div>
        <PostList 
            :posts="posts" 
            :boardUrl="board.boardUrl"
            :totalCount="totalCount"
            :page="page"
            :size="size"
            :current-sort="sort"
            @update:sort="handleSortChange"
        />
      </div>

      <!-- Search Bar & Write Button -->
      <div class="mt-4 px-4 py-4 sm:px-6 bg-gray-50 rounded-lg relative flex justify-center items-center">
        <div class="flex max-w-lg w-full">
            <select v-model="searchType" class="block pl-3 pr-8 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md bg-white mr-2 cursor-pointer shadow-sm">
                <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
            </select>
            <div class="relative flex-grow">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Search class="h-5 w-5 text-gray-400" />
                </div>
                <input
                    type="text"
                    v-model="searchQuery"
                    @keyup.enter="handleSearch"
                    class="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-l-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                    :placeholder="$t('board.detail.searchPlaceholder')"
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
                {{ $t('common.search') }}
            </button>
        </div>
        
        <div class="absolute right-6">
            <router-link 
                v-if="authStore.isAuthenticated"
                :to="`/board/${board.boardUrl}/write`"
                class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap"
            >
                <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
                {{ $t('common.write') }}
            </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
