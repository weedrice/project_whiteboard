<script setup>
import { ref, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, Settings, User } from 'lucide-vue-next'
import UserMenu from '@/components/common/UserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/Pagination.vue' // Added Pagination

const { t } = useI18n()
const route = useRoute()
const authStore = useAuthStore()

const { useBoardDetail, useBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()

// State
const page = ref(0)
const size = ref(20)
const searchQuery = ref('')
const searchType = ref('TITLE_CONTENT')
const isSearching = ref(false)
const filterType = ref('all') // 'all', 'concept', or 'category'
const activeFilterCategory = ref(null)
const sort = ref('createdAt,desc')

// Computed Params for Query
const queryParams = computed(() => {
    const params = {
        page: page.value,
        size: size.value,
        sort: 'createdAt,desc',
    }

    if (isSearching.value) {
        params.q = searchQuery.value
        params.searchType = searchType.value
    } else {
        params.minLikes = filterType.value === 'concept' ? 5 : null
        params.categoryId = filterType.value === 'category' ? activeFilterCategory.value : null
    }
    return params
})

// Initialize search from route query
watch(() => route.query, (newQuery) => {
    if (newQuery.q && newQuery.type) {
        searchQuery.value = newQuery.q
        searchType.value = newQuery.type
        isSearching.value = true
    }
}, { immediate: true })

// Queries
const boardUrl = computed(() => route.params.boardUrl)
const { data: board, isLoading: isBoardLoading, error: boardError } = useBoardDetail(boardUrl)
const { data: postsData, isLoading: isPostsLoading } = useBoardPosts(boardUrl, queryParams, isSearching)
const { data: noticesData } = useBoardNotices(boardUrl)

// Mutations
const { mutate: subscribeMutate } = useSubscribeBoard()

// Computed Data
const categories = computed(() => {
    return board.value?.categories.filter(cat => cat.name !== '일반') || []
})

const posts = computed(() => {
    let p = [...(postsData.value?.content || [])]

    // Client-side sorting
    const [field, direction] = sort.value.split(',')
    const isAsc = direction === 'asc'

    p.sort((a, b) => {
        let valA = a[field]
        let valB = b[field]

        if (field === 'author') {
            valA = a.author?.displayName || ''
            valB = b.author?.displayName || ''
        } else if (field === 'category') {
            valA = a.category?.name || ''
            valB = b.category?.name || ''
        }

        if (valA < valB) return isAsc ? -1 : 1
        if (valA > valB) return isAsc ? 1 : -1
        return 0
    })

    // Assign visual row numbers
    const total = totalCount.value
    const pageSize = size.value
    const currentPage = page.value

    // rowNum recalculation removed to keep original rowNum

    // Merge notices if not searching and on first page
    if (!isSearching.value && noticesData.value && page.value === 0) {
        const n = noticesData.value.map(notice => ({ ...notice, isNotice: true }))
        return [...n, ...p]
    }
    return p
})

const totalCount = computed(() => postsData.value?.totalElements || 0)
const totalPages = computed(() => postsData.value?.totalPages || 0)
const isLoading = computed(() => isBoardLoading.value || isPostsLoading.value)
const error = computed(() => boardError.value ? t('board.loadFailed') : '')

const canWrite = computed(() => {
    if (!authStore.isAuthenticated || !board.value) return false

    // Find general category
    const generalCategory = board.value.categories.find(c => c.name === '일반')
    if (!generalCategory) return true // Fallback if no general category

    const minRole = generalCategory.minWriteRole || 'USER'
    const userRole = authStore.user?.role || 'USER'
    const isBoardAdmin = board.value.isAdmin

    if (minRole === 'SUPER_ADMIN') return userRole === 'SUPER_ADMIN'
    if (minRole === 'BOARD_ADMIN') return userRole === 'SUPER_ADMIN' || isBoardAdmin
    return true // USER role
})

// Methods
function handleSearch() {
    if (!searchQuery.value.trim()) return
    isSearching.value = true
    page.value = 0
}

function clearSearch() {
    searchQuery.value = ''
    isSearching.value = false
    page.value = 0
}

function toggleFilter(type, categoryId = null) {
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
        return
    }
    page.value = 0
}

function handleSortChange(newSort) {
    sort.value = newSort
    page.value = 0
}

function handleSubscribe() {
    if (!board.value) return
    subscribeMutate({
        boardUrl: board.value.boardUrl,
        isSubscribed: board.value.isSubscribed
    })
}

function handlePageChange(newPage) {
    page.value = newPage
}

// Watchers
watch(() => route.params.boardUrl, () => {
    searchQuery.value = ''
    isSearching.value = false
    filterType.value = 'all'
    activeFilterCategory.value = null
    page.value = 0
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
            <div class="bg-white dark:bg-gray-800 shadow rounded-lg mb-6 p-6 transition-colors duration-200">
                <div class="flex items-start">
                    <router-link :to="`/board/${board.boardUrl}`" class="flex-shrink-0 mr-6 cursor-pointer">
                        <img v-if="board.iconUrl" :src="board.iconUrl" class="h-20 w-20 rounded-full" alt="" />
                        <div v-else
                            class="h-20 w-20 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center">
                            <span class="text-indigo-600 dark:text-indigo-400 font-bold text-3xl">{{ board.boardName[0]
                            }}</span>
                        </div>
                    </router-link>
                    <div class="flex-1 h-20 flex flex-col justify-between">
                        <div class="flex justify-between items-start">
                            <router-link :to="`/board/${board.boardUrl}`" class="hover:underline cursor-pointer">
                                <h1 class="text-2xl font-bold text-gray-900 dark:text-white">{{ board.boardName }}</h1>
                            </router-link>
                            <div class="flex space-x-2">
                                <button v-if="authStore.isAuthenticated" @click="handleSubscribe"
                                    class="inline-flex items-center px-3 py-1.5 border shadow-sm text-xs font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200"
                                    :class="board.isSubscribed ? 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600' : 'border-transparent text-white bg-indigo-600 hover:bg-indigo-700'">
                                    {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                                </button>
                                <router-link v-if="board.isAdmin" :to="`/board/${board.boardUrl}/edit`"
                                    class="inline-flex items-center px-3 py-1.5 border border-gray-300 dark:border-gray-600 shadow-sm text-xs font-medium rounded-md text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
                                    <Settings class="-ml-1 mr-1 h-4 w-4" />
                                    {{ $t('common.manage') }}
                                </router-link>
                            </div>
                        </div>
                        <div class="flex items-center text-sm text-gray-500 dark:text-gray-400 space-x-4">
                            <span class="flex items-center">
                                <User class="h-4 w-4 mr-1" />
                                {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
                            </span>
                            <span class="flex items-center">
                                <span class="font-medium mr-1">{{ $t('common.admin') }}</span>
                                <UserMenu v-if="board.adminUserId" :user-id="board.adminUserId"
                                    :display-name="board.adminDisplayName || $t('common.defaultAdminName')" />
                                <span v-else>{{ board.adminDisplayName || $t('board.detail.defaultAdminName') }}</span>
                            </span>
                        </div>
                        <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-1">{{ board.description }}</p>
                    </div>
                </div>
            </div>

            <!-- Post Detail Router View -->
            <div class="mb-6">
                <router-view></router-view>
            </div>



            <!-- Filters & Post List -->
            <div class="bg-white dark:bg-gray-800 shadow rounded-lg transition-colors duration-200">
                <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 flex space-x-4">
                    <button @click="toggleFilter('all')"
                        class="px-3 py-1 text-sm font-medium rounded-md transition-colors duration-200"
                        :class="filterType === 'all' ? 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'">
                        {{ $t('board.detail.filter.all') }}
                    </button>
                    <button @click="toggleFilter('concept')"
                        class="px-3 py-1 text-sm font-medium rounded-md transition-colors duration-200"
                        :class="filterType === 'concept' ? 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'">
                        {{ $t('board.detail.filter.concept') }}
                    </button>
                    <button v-for="category in categories" :key="category.categoryId"
                        @click="toggleFilter('category', category.categoryId)"
                        class="px-3 py-1 text-sm font-medium rounded-md transition-colors duration-200"
                        :class="filterType === 'category' && activeFilterCategory === category.categoryId ? 'bg-indigo-100 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'">
                        {{ category.name }}
                    </button>
                </div>
                <PostList :posts="posts" :boardUrl="board.boardUrl" :totalCount="totalCount" :page="page" :size="size"
                    :current-sort="sort" @update:sort="handleSortChange" />
                <!-- Pagination -->
                <div class="px-4 py-3 border-t border-gray-200 dark:border-gray-700 flex justify-center"
                    v-if="totalPages > 1">
                    <Pagination :currentPage="page" :totalPages="totalPages" @page-change="handlePageChange" />
                </div>
            </div>

            <!-- Search Bar & Write Button -->
            <div
                class="mt-4 px-4 py-4 sm:px-6 bg-gray-50 dark:bg-gray-800 rounded-lg relative flex justify-center items-center transition-colors duration-200">
                <div class="flex max-w-lg w-full">
                    <select v-model="searchType"
                        class="block pl-3 pr-8 py-2 text-base border-gray-300 dark:border-gray-600 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-white mr-2 cursor-pointer shadow-sm transition-colors duration-200">
                        <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                        <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                        <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                        <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
                        <option value="TAG">{{ $t('board.detail.searchType.tag') }}</option>
                    </select>
                    <div class="relative flex-grow">
                        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <Search class="h-5 w-5 text-gray-400" />
                        </div>
                        <input type="text" v-model="searchQuery" @keyup.enter="handleSearch"
                            class="block w-full pl-10 pr-3 py-2 border border-gray-300 dark:border-gray-600 rounded-l-md leading-5 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm transition-colors duration-200"
                            :placeholder="$t('board.detail.searchPlaceholder')" />
                        <div v-if="isSearching" class="absolute inset-y-0 right-2 pr-3 flex items-center">
                            <button type="button" @click="clearSearch"
                                class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300">
                                <X class="h-5 w-5" />
                            </button>
                        </div>
                    </div>
                    <button @click="handleSearch"
                        class="inline-flex items-center px-4 py-2 border border-l-0 border-gray-300 dark:border-gray-600 shadow-sm text-sm font-medium rounded-r-md text-gray-700 dark:text-gray-200 bg-gray-50 dark:bg-gray-700 hover:bg-gray-100 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap transition-colors duration-200">
                        {{ $t('common.search') }}
                    </button>
                </div>

                <div class="absolute right-6">
                    <router-link v-if="canWrite" :to="`/board/${board.boardUrl}/write`"
                        class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap">
                        <PlusCircle class="-ml-1 mr-2 h-5 w-5" />
                        {{ $t('common.write') }}
                    </router-link>
                </div>
            </div>
        </div>
    </div>
</template>
