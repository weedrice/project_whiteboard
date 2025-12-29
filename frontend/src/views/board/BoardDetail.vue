<script setup>
import { ref, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, Settings, User } from 'lucide-vue-next'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/ui/Pagination.vue' // Added Pagination
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

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
        <div v-if="isLoading && !board" class="space-y-6">
            <!-- Header Skeleton -->
            <div class="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
                <div class="flex items-start">
                    <BaseSkeleton width="5rem" height="5rem" rounded="rounded-full" className="mr-6 flex-shrink-0" />
                    <div class="flex-1">
                        <div class="flex justify-between items-start mb-2">
                            <BaseSkeleton width="200px" height="32px" />
                            <BaseSkeleton width="100px" height="32px" />
                        </div>
                        <div class="flex gap-4 mb-2">
                            <BaseSkeleton width="80px" height="20px" />
                            <BaseSkeleton width="100px" height="20px" />
                        </div>
                        <BaseSkeleton width="60%" height="20px" />
                    </div>
                </div>
            </div>
            <!-- List Skeleton -->
            <div class="bg-white dark:bg-gray-800 shadow rounded-lg p-4">
                <div class="flex space-x-4 mb-4 overflow-hidden">
                    <BaseSkeleton v-for="i in 4" :key="i" width="60px" height="32px" />
                </div>
                <div class="space-y-4">
                    <div v-for="i in 5" :key="i"
                        class="flex justify-between items-center py-2 border-b border-gray-100 dark:border-gray-700 last:border-0">
                        <div class="w-full">
                            <BaseSkeleton width="70%" height="24px" className="mb-2" />
                            <div class="flex gap-2">
                                <BaseSkeleton width="40px" height="16px" />
                                <BaseSkeleton width="60px" height="16px" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
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
                    <div class="flex-1 min-h-[5rem] flex flex-col justify-between">
                        <div class="flex flex-col sm:flex-row justify-between items-start gap-2">
                            <router-link :to="`/board/${board.boardUrl}`" class="hover:underline cursor-pointer">
                                <h1 class="text-2xl font-bold text-gray-900 dark:text-white break-all">{{
                                    board.boardName }}</h1>
                            </router-link>
                            <div class="flex space-x-2 flex-shrink-0">
                                <BaseButton v-if="authStore.isAuthenticated" @click="handleSubscribe" size="sm"
                                    :variant="board.isSubscribed ? 'secondary' : 'primary'"
                                    class="cursor-pointer transition-colors duration-200">
                                    {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                                </BaseButton>
                                <router-link v-if="board.isAdmin" :to="`/board/${board.boardUrl}/edit`"
                                    class="inline-flex items-center px-3 py-1.5 border border-gray-300 dark:border-gray-600 shadow-sm text-xs font-medium rounded-md text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
                                    <Settings class="-ml-1 mr-1 h-4 w-4" />
                                    {{ $t('common.manage') }}
                                </router-link>
                            </div>
                        </div>
                        <div
                            class="flex flex-wrap items-center text-sm text-gray-500 dark:text-gray-400 gap-4 mt-2 sm:mt-0">
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
                        <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-1 mt-1">{{ board.description }}
                        </p>
                    </div>
                </div>
            </div>

            <!-- Post Detail Router View -->
            <div class="mb-6">
                <router-view></router-view>
            </div>



            <!-- Filters & Post List -->
            <div class="bg-white dark:bg-gray-800 shadow rounded-lg transition-colors duration-200">
                <div
                    class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 flex space-x-4 overflow-x-auto scrollbar-hide">
                    <BaseButton @click="toggleFilter('all')" size="sm"
                        :variant="filterType === 'all' ? 'primary' : 'ghost'"
                        :class="filterType === 'all' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                        class="whitespace-nowrap">
                        {{ $t('board.detail.filter.all') }}
                    </BaseButton>
                    <BaseButton @click="toggleFilter('concept')" size="sm"
                        :variant="filterType === 'concept' ? 'primary' : 'ghost'"
                        :class="filterType === 'concept' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                        class="whitespace-nowrap">
                        {{ $t('board.detail.filter.concept') }}
                    </BaseButton>
                    <BaseButton v-for="category in categories" :key="category.categoryId"
                        @click="toggleFilter('category', category.categoryId)" size="sm"
                        :variant="filterType === 'category' && activeFilterCategory === category.categoryId ? 'primary' : 'ghost'"
                        :class="filterType === 'category' && activeFilterCategory === category.categoryId ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'"
                        class="whitespace-nowrap">
                        {{ category.name }}
                    </BaseButton>
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
                class="mt-4 px-4 py-4 sm:px-6 bg-gray-50 dark:bg-gray-800 rounded-lg flex flex-col sm:flex-row justify-between items-center gap-4 transition-colors duration-200">
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
                        <BaseInput v-model="searchQuery" @keyup.enter="handleSearch"
                            :placeholder="$t('board.detail.searchPlaceholder')"
                            inputClass="rounded-l-md rounded-r-none border-r-0" hideLabel>
                            <template #prefix>
                                <Search class="h-5 w-5 text-gray-400" />
                            </template>
                            <template #suffix>
                                <button v-if="isSearching" type="button" @click="clearSearch"
                                    class="text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 cursor-pointer">
                                    <X class="h-5 w-5" />
                                </button>
                            </template>
                        </BaseInput>
                    </div>
                    <BaseButton @click="handleSearch" variant="secondary"
                        class="rounded-l-none border-l-0 bg-gray-50 dark:bg-gray-700 hover:bg-gray-100 dark:hover:bg-gray-600">
                        {{ $t('search.doSearch') }}
                    </BaseButton>
                </div>

                <div class="w-full sm:w-auto flex justify-end">
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
