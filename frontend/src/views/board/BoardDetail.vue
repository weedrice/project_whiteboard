<script setup lang="ts">
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import PostList from '@/components/board/PostList.vue'
import { Search, X, PlusCircle, User } from 'lucide-vue-next'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/ui/Pagination.vue' // Added Pagination
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { useHead } from '@unhead/vue'
import { useRecentBoards } from '@/composables/useRecentBoards'
import { isInputFocused } from '@/utils/keyboard'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import type { PostSummary } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const { useBoardDetail, useBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()
const { addRecentBoard } = useRecentBoards()

// Queries
const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)
const { data: board, isLoading: isBoardLoading, error: boardError } = useBoardDetail(boardUrl, {
    meta: { errorMessage: false },
    requestConfig: { skipGlobalErrorHandler: true }
})
const boardTitle = computed(() => {
    const boardName = board.value?.boardName?.trim()
    if (boardName) return boardName
    return decodeURIComponent(boardUrl.value || '').trim() || 'Board'
})

// SEO
useHead({
    title: computed(() => (currentPostId.value ? undefined : boardTitle.value)),
    meta: [
        { name: 'description', content: computed(() => board.value?.description || 'Board posts and discussions') },
        { property: 'og:title', content: computed(() => `${board.value?.boardName || 'Board'} - ${t('common.appName')}`) },
        { property: 'og:description', content: computed(() => board.value?.description || 'Board posts and discussions') }
    ]
})

watch([() => route.name, boardTitle], ([routeName, title]) => {
    if (currentPostId.value || routeName !== 'board-detail' || typeof document === 'undefined') {
        console.log('[TitleDebug][BoardDetail] skip', {
            routeName,
            currentPostId: currentPostId.value,
            title,
            documentTitle: typeof document !== 'undefined' ? document.title : 'no-document'
        })
        return
    }
    console.log('[TitleDebug][BoardDetail] apply', {
        routeName,
        currentPostId: currentPostId.value,
        titleBefore: document.title,
        nextTitle: `${title} - ${t('common.appName')}`
    })
    document.title = `${title} - ${t('common.appName')}`
    console.log('[TitleDebug][BoardDetail] applied', { documentTitle: document.title })
}, { immediate: true })

// State

// State
const page = ref(0)
const size = ref(20)
const searchQuery = ref('')
const searchType = ref('TITLE_CONTENT')
const isSearching = ref(false)
const filterType = ref('all') // 'all', 'concept', or 'category'
const activeFilterCategory = ref<{ categoryId: number; name: string } | null>(null)
const sort = ref('createdAt,desc')
const searchInputElementId = 'board-search-input'

const parsePageFromQuery = (value: unknown): number => {
    const parsed = Number.parseInt(String(value ?? '1'), 10)
    if (Number.isNaN(parsed) || parsed < 1) return 0
    return parsed - 1
}

const buildPaginationRoute = (targetPage: number) => {
    const nextQuery = { ...route.query }
    if (targetPage <= 0) {
        delete nextQuery.page
    } else {
        nextQuery.page = String(targetPage + 1)
    }
    return {
        path: route.path,
        query: nextQuery
    }
}

const buildBoardListRoute = () => ({
    path: `/board/${boardUrl.value}`,
    query: route.query
})

type SortField = 'author' | 'category' | 'viewCount' | 'likeCount' | 'commentCount' | 'createdAt' | 'title'

const resolveSortField = (field: string): SortField => {
    switch (field) {
        case 'author':
        case 'category':
        case 'viewCount':
        case 'likeCount':
        case 'commentCount':
        case 'createdAt':
        case 'title':
            return field
        default:
            return 'createdAt'
    }
}

const getSortValue = (post: PostSummary, field: SortField): string | number => {
    switch (field) {
        case 'author':
            return post.author?.displayName || ''
        case 'category':
            return post.category?.name || ''
        case 'viewCount':
            return post.viewCount || 0
        case 'likeCount':
            return post.likeCount || 0
        case 'commentCount':
            return post.commentCount || 0
        case 'title':
            return post.title || ''
        case 'createdAt':
        default:
            return post.createdAt || ''
    }
}

// Computed Params for Query
const queryParams = computed(() => {
    const params: {
        page: number
        size: number
        sort: string
        q?: string
        searchType?: string
        minLikes?: number
        categoryId?: number
    } = {
        page: page.value,
        size: size.value,
        sort: sort.value,
    }

    if (isSearching.value) {
        params.q = searchQuery.value
        params.searchType = searchType.value
    } else {
        if (filterType.value === 'concept') {
            params.minLikes = 5
        }
        if (filterType.value === 'category' && activeFilterCategory.value) {
            params.categoryId = activeFilterCategory.value.categoryId
        }
    }
    return params
})

// Initialize search from route query
watch(() => route.query, (newQuery) => {
    if (newQuery.q && newQuery.type) {
        searchQuery.value = String(newQuery.q)
        searchType.value = String(newQuery.type)
        isSearching.value = true
    }
}, { immediate: true })

watch(() => route.query.page, (newPage) => {
    page.value = parsePageFromQuery(newPage)
}, { immediate: true })

watch(page, (newPage) => {
    if (newPage === parsePageFromQuery(route.query.page)) {
        return
    }
    router.replace(buildPaginationRoute(newPage))
})

// Queries
// boardUrl, board, isBoardLoading, boardError are already defined above
const boardContentEnabled = computed(() => !!board.value && !boardError.value)
const { data: postsData, isLoading: isPostsLoading } = useBoardPosts(boardUrl, queryParams, isSearching, boardContentEnabled)
const { data: noticesData } = useBoardNotices(boardUrl, boardContentEnabled)

// Mutations
const { mutate: subscribeMutate } = useSubscribeBoard()

// Computed Data
const categories = computed(() => {
    return board.value?.categories?.filter(cat => cat.name !== '일반') || []
})

const posts = computed(() => {
    const data = postsData.value?.content
    if (!data || data.length === 0) return []

    // 정렬이 기본값('createdAt,desc')인 경우 서버에서 이미 정렬된 데이터 사용
    // 서버 API는 항상 'createdAt,desc'로 정렬된 데이터를 반환
    if (sort.value === 'createdAt,desc') {
        // Merge notices if not searching and on first page
        if (!isSearching.value && noticesData.value && page.value === 0) {
            const n = noticesData.value.map(notice => ({ ...notice, isNotice: true }))
            return [...n, ...data]
        }
        return data
    }

    // 정렬이 필요한 경우에만 클라이언트 사이드 정렬 수행
    const sortedData = [...data]
    const [rawField, direction] = sort.value.split(',')
    const field = resolveSortField(rawField)
    const isAsc = direction === 'asc'

    sortedData.sort((a, b) => {
        const valA = getSortValue(a, field)
        const valB = getSortValue(b, field)

        if (valA < valB) return isAsc ? -1 : 1
        if (valA > valB) return isAsc ? 1 : -1
        return 0
    })

    // Merge notices if not searching and on first page
    if (!isSearching.value && noticesData.value && page.value === 0) {
        const n = noticesData.value.map(notice => ({ ...notice, isNotice: true }))
        return [...n, ...sortedData]
    }
    return sortedData
})

const totalCount = computed(() => postsData.value?.totalElements || 0)
const totalPages = computed(() => postsData.value?.totalPages || 0)
const isLoading = computed(() => isBoardLoading.value || isPostsLoading.value)
const error = computed(() => {
    if (!boardError.value) return ''
    if (isRestrictedResourceError(boardError.value)) {
        return '접근이 제한된 게시판입니다.'
    }
    return t('board.loadFailed')
})

const canWrite = computed(() => {
    if (!authStore.isAuthenticated || !board.value) return false

    // Find general category
    const generalCategory = board.value.categories?.find(c => c.name === '일반')
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

function toggleFilter(type: string, categoryId: number | null = null) {
    if (type === 'all') {
        if (filterType.value === 'all' && activeFilterCategory.value === null) return
        filterType.value = 'all'
        activeFilterCategory.value = null
    } else if (type === 'concept') {
        if (filterType.value === 'concept') return
        filterType.value = 'concept'
        activeFilterCategory.value = null
    } else if (type === 'category' && categoryId !== null) {
        if (filterType.value === 'category' && activeFilterCategory.value?.categoryId === categoryId) return
        filterType.value = 'category'
        // Find category from categories list
        const category = categories.value.find(c => c.categoryId === categoryId)
        activeFilterCategory.value = category || null
    } else {
        return
    }
    page.value = 0
}

function handleSortChange(newSort: string) {
    sort.value = newSort
    page.value = 0
}

function handleSubscribe() {
    if (!board.value) return
    if (board.value.isSubscribed && !window.confirm(t('user.subscriptions.unsubscribeConfirm'))) return
    subscribeMutate({
        boardUrl: board.value.boardUrl,
        isSubscribed: board.value.isSubscribed ?? false
    })
}

function handlePageChange(newPage: number) {
    const maxPage = Math.max(totalPages.value - 1, 0)
    page.value = Math.min(Math.max(newPage, 0), maxPage)
}

// Watchers
watch(() => route.params.boardUrl, () => {
    searchQuery.value = ''
    isSearching.value = false
    filterType.value = 'all'
    activeFilterCategory.value = null
    page.value = 0
})

// 게시판 데이터 로드 시 최근 방문 기록 추가
watch(board, (newBoard) => {
    if (newBoard) {
        addRecentBoard({
            boardUrl: newBoard.boardUrl,
            boardName: newBoard.boardName,
            iconUrl: newBoard.iconUrl,
        })
    }
}, { immediate: true })



// 키보드 단축키 핸들러
const handleKeyDown = (event: KeyboardEvent) => {
    const { key, shiftKey, ctrlKey, altKey, metaKey } = event

    if (ctrlKey || altKey || metaKey) return
    if (isInputFocused()) return

    // Shift 조합
    if (shiftKey) {
        if (key === '[' || key === '{') {
            event.preventDefault()
            page.value = 0
            return
        }
        if (key === ']' || key === '}') {
            event.preventDefault()
            if (totalPages.value > 0) {
                page.value = totalPages.value - 1
            }
            return
        }
        return
    }

    switch (key) {
        case ']':
            if (page.value < totalPages.value - 1) {
                event.preventDefault()
                page.value++
            }
            break

        case '[':
            if (page.value > 0) {
                event.preventDefault()
                page.value--
            }
            break

        case 'n':
        case 'N':
            if (canWrite.value && board.value) {
                event.preventDefault()
                router.push(`/board/${board.value.boardUrl}/write`)
            }
            break

        case 'f':
        case 'F':
            if (authStore.isAuthenticated) {
                event.preventDefault()
                handleSubscribe()
            }
            break

        case '/':
            event.preventDefault()
            // 검색창에 포커스
            const searchInput = document.getElementById(searchInputElementId) as HTMLInputElement | null
            if (searchInput) {
                searchInput.focus()
            }
            break
    }
}

onMounted(() => {
    document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
    document.removeEventListener('keydown', handleKeyDown)
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
            <div
                class="bg-white dark:bg-gray-800 shadow rounded-lg mb-2 sm:mb-6 p-4 sm:p-6 transition-colors duration-200">
                <div class="flex items-start">
                    <router-link :to="buildBoardListRoute()" class="flex-shrink-0 mr-4 sm:mr-6 cursor-pointer">
                        <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl, 80)"
                            class="h-14 w-14 sm:h-20 sm:w-20 rounded-full" alt="" @error="handleImageError($event)" />
                        <div v-else
                            class="h-14 w-14 sm:h-20 sm:w-20 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center">
                            <span class="text-indigo-600 dark:text-indigo-400 font-bold text-xl sm:text-3xl">{{
                                board.boardName[0]
                            }}</span>
                        </div>
                    </router-link>
                    <div class="flex-1 min-h-[5rem] flex flex-col justify-between">
                        <div class="flex flex-row justify-between items-center gap-2 min-w-0">
                            <router-link :to="buildBoardListRoute()"
                                class="hover:underline cursor-pointer min-w-0 flex-1">
                                <h1 class="text-lg sm:text-2xl font-bold text-gray-900 dark:text-white truncate">{{
                                    board.boardName }}</h1>
                            </router-link>
                            <div class="flex gap-1.5 sm:gap-2 flex-shrink-0">
                                <BaseButton v-if="authStore.isAuthenticated" @click="handleSubscribe" size="sm"
                                    :variant="board.isSubscribed ? 'secondary' : 'primary'"
                                    class="cursor-pointer transition-colors duration-200">
                                    {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                                </BaseButton>
                                <router-link v-if="board.isAdmin" :to="`/board/${board.boardUrl}/edit`"
                                    class="inline-flex items-center justify-center px-2 py-1.5 sm:px-3 sm:py-2 border border-gray-300 dark:border-gray-600 shadow-sm text-xs sm:text-sm font-medium rounded-md leading-4 text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer transition-colors duration-200">
                                    {{ $t('common.manage') }}
                                </router-link>
                            </div>
                        </div>
                        <div
                            class="flex flex-wrap items-center text-xs sm:text-sm text-gray-500 dark:text-gray-400 gap-3 sm:gap-4 mt-2 sm:mt-0">
                            <span class="flex items-center">
                                <User class="hidden sm:block h-4 w-4 mr-1 flex-shrink-0" />
                                {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
                            </span>
                            <span class="flex items-center">
                                <span class="font-medium mr-1">{{ $t('common.admin') }}</span>
                                <template v-if="board.adminUserId">
                                    <span class="text-[11px] sm:text-sm">
                                        <UserMenu :user-id="board.adminUserId"
                                            :display-name="board.adminDisplayName || $t('common.defaultAdminName')"
                                            size="inherit" />
                                    </span>
                                </template>
                                <span v-else class="text-[11px] sm:text-sm">{{ board.adminDisplayName ||
                                    $t('board.detail.defaultAdminName') }}</span>
                            </span>
                        </div>
                        <p class="text-xs sm:text-sm text-gray-500 dark:text-gray-400 line-clamp-1 mt-1">{{
                            board.description }}
                        </p>
                    </div>
                </div>
            </div>

            <!-- Post Detail Router View -->
            <div class="mb-3 sm:mb-6">
                <router-view></router-view>
            </div>



            <!-- Filters & Post List -->
            <div id="board-post-list"
                class="bg-white dark:bg-gray-800 shadow rounded-lg transition-colors duration-200">
                <div
                    class="px-3 sm:px-4 py-2 sm:py-3 border-b border-gray-200 dark:border-gray-700 flex gap-1.5 sm:gap-2 overflow-x-auto scrollbar-hide">
                    <BaseButton @click="toggleFilter('all')" size="sm"
                        :variant="filterType === 'all' ? 'primary' : 'ghost'"
                        :class="[filterType === 'all' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200', 'whitespace-nowrap']">
                        {{ $t('board.detail.filter.all') }}
                    </BaseButton>
                    <BaseButton @click="toggleFilter('concept')" size="sm"
                        :variant="filterType === 'concept' ? 'primary' : 'ghost'"
                        :class="[filterType === 'concept' ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200', 'whitespace-nowrap']">
                        {{ $t('board.detail.filter.concept') }}
                    </BaseButton>
                    <BaseButton v-for="category in categories" :key="category.categoryId"
                        @click="toggleFilter('category', category.categoryId)" size="sm"
                        :variant="filterType === 'category' && activeFilterCategory?.categoryId === category.categoryId ? 'primary' : 'ghost'"
                        :class="[filterType === 'category' && activeFilterCategory?.categoryId === category.categoryId ? '!bg-indigo-100 !text-indigo-700 dark:!bg-indigo-900/50 dark:!text-indigo-300' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200', 'whitespace-nowrap']">
                        {{ category.name }}
                    </BaseButton>
                </div>
                <PostList :posts="posts" :boardUrl="board.boardUrl" :totalCount="totalCount" :page="page" :size="size"
                    :current-sort="sort" :currentPostId="currentPostId" :linkQuery="route.query"
                    @update:sort="handleSortChange" />
                <!-- Pagination -->
                <div class="px-3 sm:px-4 py-2 sm:py-3 border-t border-gray-200 dark:border-gray-700 flex justify-center"
                    v-if="totalPages >= 1">
                    <Pagination
                        :currentPage="page"
                        :totalPages="totalPages"
                        :linkBuilder="buildPaginationRoute"
                        @page-change="handlePageChange"
                    />
                </div>
            </div>

            <!-- Search Bar (가운데) & Write Button (오른쪽) -->
            <div
                class="mt-2 sm:mt-4 px-3 sm:px-6 py-3 sm:py-4 bg-gray-50 dark:bg-gray-800 rounded-lg flex flex-col sm:flex-row items-center gap-3 sm:gap-4 transition-colors duration-200">
                <div class="flex-1 min-w-0 hidden sm:block" aria-hidden="true"></div>
                <div class="w-full sm:w-auto flex justify-center shrink-0 list-search-mobile">
                    <div class="list-search-row">
                        <div class="list-search-group">
                            <select v-model="searchType" class="list-search-select-inline">
                                <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                                <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                                <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                                <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
                                <option value="TAG">{{ $t('board.detail.searchType.tag') }}</option>
                            </select>
                            <div class="list-search-input-inner">
                                <BaseInput v-model="searchQuery" @keyup.enter="handleSearch"
                                    :id="searchInputElementId"
                                    :placeholder="$t('board.detail.searchPlaceholder')" inputClass="list-search-input"
                                    hideLabel>
                                    <template #prefix>
                                        <Search class="hidden sm:block h-5 w-5 text-gray-400" />
                                    </template>
                                    <template #suffix>
                                        <button v-if="isSearching" type="button" @click="clearSearch"
                                            :aria-label="t('layout.recentBoards.clear')"
                                            class="hidden sm:flex text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 cursor-pointer items-center">
                                            <X class="h-5 w-5" />
                                        </button>
                                    </template>
                                </BaseInput>
                            </div>
                            <BaseButton @click="handleSearch" variant="secondary" type="button" class="list-search-btn">
                                {{ $t('search.doSearch') }}
                            </BaseButton>
                        </div>
                    </div>
                </div>

                <div class="flex-1 min-w-0 w-full sm:w-auto flex justify-end">
                    <router-link v-if="canWrite" :to="`/board/${board.boardUrl}/write`"
                        class="inline-flex items-center justify-center px-2 py-1.5 sm:px-4 sm:py-2 border border-transparent text-xs sm:text-sm font-medium rounded-md shadow-sm leading-4 text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 whitespace-nowrap">
                        <PlusCircle class="hidden sm:inline-block -ml-1 mr-2 h-5 w-5" />
                        {{ $t('common.write') }}
                    </router-link>
                </div>
            </div>
        </div>
    </div>
</template>
