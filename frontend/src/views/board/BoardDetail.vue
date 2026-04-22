<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, ShieldCheck, User, X } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useBoard } from '@/composables/useBoard'
import { useRecentBoards } from '@/composables/useRecentBoards'
import { useAuthStore } from '@/stores/auth'
import type { Category, PostSummary } from '@/types'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { isInputFocused } from '@/utils/keyboard'

const GENERAL_CATEGORY_NAMES = new Set(['일반', 'general'])

const isGeneralCategory = (name?: string | null): boolean => {
  const normalized = name?.trim().toLowerCase()
  return normalized ? GENERAL_CATEGORY_NAMES.has(normalized) : false
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const { useBoardDetail, useBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()
const { addRecentBoard } = useRecentBoards()

const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)

const {
  data: board,
  isLoading: isBoardLoading,
  error: boardError
} = useBoardDetail(boardUrl, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})

const boardTitle = computed(() => {
  const boardName = board.value?.boardName?.trim()
  if (boardName) return boardName
  return decodeURIComponent(boardUrl.value || '').trim() || 'Board'
})

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
    return
  }
  document.title = `${title} - ${t('common.appName')}`
}, { immediate: true })

const page = ref(0)
const size = ref(20)
const searchQuery = ref('')
const searchType = ref('TITLE_CONTENT')
const isSearching = ref(false)
const selectedCategoryId = ref<number | null>(null)
const sort = ref('createdAt,desc')
const searchInputElementId = 'board-search-input'

type SortField = 'author' | 'category' | 'viewCount' | 'likeCount' | 'commentCount' | 'createdAt' | 'title' | 'postId'

const parsePageFromQuery = (value: unknown): number => {
  const parsed = Number.parseInt(String(value ?? '1'), 10)
  if (Number.isNaN(parsed) || parsed < 1) return 0
  return parsed - 1
}

const parseCategoryIdFromQuery = (value: unknown): number | null => {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (Number.isNaN(parsed) || parsed <= 0) return null
  return parsed
}

const resolveSortField = (field: string): SortField => {
  switch (field) {
    case 'author':
    case 'category':
    case 'viewCount':
    case 'likeCount':
    case 'commentCount':
    case 'createdAt':
    case 'title':
    case 'postId':
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
    case 'postId':
      return post.rowNum ?? post.postId ?? 0
    case 'createdAt':
    default:
      return post.createdAt || ''
  }
}

const buildListQuery = (
  targetPage: number,
  searchState?: { q: string; searchType: string } | null,
  categoryState?: number | null
) => {
  const nextQuery = { ...route.query }

  if (targetPage <= 0) {
    delete nextQuery.page
  } else {
    nextQuery.page = String(targetPage + 1)
  }

  const resolvedSearchState = searchState === undefined
    ? (isSearching.value && searchQuery.value.trim()
      ? { q: searchQuery.value.trim(), searchType: searchType.value }
      : null)
    : searchState
  const resolvedCategoryState = categoryState === undefined ? selectedCategoryId.value : categoryState

  if (resolvedSearchState?.q) {
    nextQuery.q = resolvedSearchState.q
    nextQuery.type = resolvedSearchState.searchType
  } else {
    delete nextQuery.q
    delete nextQuery.type
  }

  if (resolvedCategoryState !== null) {
    nextQuery.categoryId = String(resolvedCategoryState)
  } else {
    delete nextQuery.categoryId
  }

  return nextQuery
}

const syncListQuery = (
  targetPage: number,
  searchState?: { q: string; searchType: string } | null,
  categoryState?: number | null
) => {
  router.replace({
    path: route.path,
    query: buildListQuery(targetPage, searchState, categoryState)
  })
}

const buildPaginationRoute = (targetPage: number) => ({
  path: route.path,
  query: buildListQuery(targetPage)
})

const buildBoardListRoute = () => ({
  path: `/board/${boardUrl.value}`,
  query: route.query
})

const queryParams = computed(() => {
  const params: {
    page: number
    size: number
    sort: string
    q?: string
    type?: string
    categoryId?: number
  } = {
    page: page.value,
    size: size.value,
    sort: sort.value
  }

  if (isSearching.value && searchQuery.value.trim()) {
    params.q = searchQuery.value.trim()
    params.type = searchType.value
  } else if (selectedCategoryId.value !== null) {
    params.categoryId = selectedCategoryId.value
  }

  return params
})

watch(() => route.query, (newQuery) => {
  const nextPage = parsePageFromQuery(newQuery.page)
  if (page.value !== nextPage) {
    page.value = nextPage
  }

  const routeQuery = typeof newQuery.q === 'string' ? newQuery.q.trim() : ''
  const routeSearchType = typeof newQuery.type === 'string' ? newQuery.type : 'TITLE_CONTENT'
  const routeCategoryId = parseCategoryIdFromQuery(newQuery.categoryId)
  const shouldSearch = routeQuery.length > 0

  if (searchQuery.value !== routeQuery) {
    searchQuery.value = routeQuery
  }
  if (searchType.value !== routeSearchType) {
    searchType.value = routeSearchType
  }
  if (isSearching.value !== shouldSearch) {
    isSearching.value = shouldSearch
  }
  if (selectedCategoryId.value !== routeCategoryId) {
    selectedCategoryId.value = routeCategoryId
  }
}, { immediate: true })

watch(page, (newPage) => {
  if (newPage === parsePageFromQuery(route.query.page)) {
    return
  }
  syncListQuery(newPage)
})

const boardContentEnabled = computed(() => !!board.value && !boardError.value)
const { data: postsData, isLoading: isPostsLoading } = useBoardPosts(boardUrl, queryParams, isSearching, boardContentEnabled)
const { data: noticesData } = useBoardNotices(boardUrl, boardContentEnabled)
const { mutate: subscribeMutate } = useSubscribeBoard()

const categories = computed(() => {
  return board.value?.categories?.filter((category) => !isGeneralCategory(category.name)) ?? []
})

const selectedCategory = computed(() => {
  return categories.value.find((category) => category.categoryId === selectedCategoryId.value) ?? null
})

const posts = computed(() => {
  const noticePosts = !isSearching.value && page.value === 0
    ? (noticesData.value ?? []).map((notice) => ({ ...notice, isNotice: true }))
    : []

  const data = postsData.value?.content ?? []
  if (data.length === 0) {
    return noticePosts
  }

  if (sort.value === 'createdAt,desc') {
    return [...noticePosts, ...data]
  }

  const sortedData = [...data]
  const [rawField, direction] = sort.value.split(',')
  const field = resolveSortField(rawField)
  const isAsc = direction === 'asc'

  sortedData.sort((a, b) => {
    const valueA = getSortValue(a, field)
    const valueB = getSortValue(b, field)

    if (valueA < valueB) return isAsc ? -1 : 1
    if (valueA > valueB) return isAsc ? 1 : -1
    return 0
  })

  return [...noticePosts, ...sortedData]
})

const totalCount = computed(() => postsData.value?.totalElements || 0)
const totalPages = computed(() => postsData.value?.totalPages || 0)
const isLoading = computed(() => isBoardLoading.value || isPostsLoading.value)
const error = computed(() => {
  if (!boardError.value) return ''
  if (isRestrictedResourceError(boardError.value)) {
    return '접근 권한이 없는 게시판입니다.'
  }
  return t('board.loadFailed')
})

const canWrite = computed(() => {
  if (!authStore.isAuthenticated || !board.value) return false

  const generalCategory = board.value.categories?.find((category) => isGeneralCategory(category.name))
  if (!generalCategory) return true

  const minRole = generalCategory.minWriteRole || 'USER'
  const userRole = authStore.user?.role || 'USER'
  const isBoardAdmin = board.value.isAdmin

  if (minRole === 'SUPER_ADMIN') return userRole === 'SUPER_ADMIN'
  if (minRole === 'BOARD_ADMIN') return userRole === 'SUPER_ADMIN' || isBoardAdmin
  return true
})

const searchSummary = computed(() => {
  if (!isSearching.value || !searchQuery.value.trim()) {
    return ''
  }
  return `"${searchQuery.value.trim()}" 검색 결과`
})

function handleSearch() {
  const trimmedQuery = searchQuery.value.trim()
  if (!trimmedQuery) {
    clearSearch()
    return
  }

  isSearching.value = true
  selectedCategoryId.value = null
  page.value = 0
  syncListQuery(0, {
    q: trimmedQuery,
    searchType: searchType.value
  }, null)
}

function clearSearch() {
  searchQuery.value = ''
  isSearching.value = false
  page.value = 0
  syncListQuery(0, null)
}

function toggleCategory(category: Category | null) {
  const nextCategoryId = category?.categoryId ?? null
  if (selectedCategoryId.value === nextCategoryId) {
    return
  }

  selectedCategoryId.value = nextCategoryId
  searchQuery.value = ''
  isSearching.value = false
  page.value = 0
  syncListQuery(0, null, nextCategoryId)
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

watch(() => route.params.boardUrl, () => {
  searchQuery.value = ''
  isSearching.value = false
  selectedCategoryId.value = null
  page.value = 0
})

watch(board, (newBoard) => {
  if (!newBoard) return

  addRecentBoard({
    boardUrl: newBoard.boardUrl,
    boardName: newBoard.boardName,
    iconUrl: newBoard.iconUrl
  })
}, { immediate: true })

const handleKeyDown = (event: KeyboardEvent) => {
  const { key, shiftKey, ctrlKey, altKey, metaKey } = event

  if (ctrlKey || altKey || metaKey) return
  if (isInputFocused()) return

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
    case '/': {
      event.preventDefault()
      const searchInput = document.getElementById(searchInputElementId) as HTMLInputElement | null
      searchInput?.focus()
      break
    }
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
  <div class="nv-board-shell space-y-3 sm:space-y-6">
    <div v-if="isLoading && !board" class="space-y-4 sm:space-y-6">
      <section class="nv-board-panel p-4 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex items-start gap-4">
            <BaseSkeleton width="5rem" height="5rem" rounded="rounded-[24px]" className="flex-shrink-0" />
            <div class="space-y-3">
              <BaseSkeleton width="220px" height="34px" />
              <div class="flex gap-3">
                <BaseSkeleton width="90px" height="18px" />
                <BaseSkeleton width="120px" height="18px" />
              </div>
              <BaseSkeleton width="320px" height="18px" />
            </div>
          </div>
          <div class="space-y-2 lg:w-52">
            <BaseSkeleton width="100%" height="34px" rounded="rounded-full" />
            <BaseSkeleton width="100%" height="42px" rounded="rounded-full" />
          </div>
        </div>
      </section>

      <section class="nv-board-panel overflow-hidden">
        <div class="border-b border-[var(--nv-line)] px-4 py-4 sm:px-5">
          <div class="flex flex-wrap gap-2">
            <BaseSkeleton v-for="index in 4" :key="index" width="72px" height="36px" rounded="rounded-full" />
          </div>
        </div>
        <div class="space-y-3 px-4 py-5 sm:px-5">
          <BaseSkeleton v-for="index in 5" :key="index" width="100%" height="54px" rounded="rounded-2xl" />
        </div>
      </section>
    </div>

    <section v-else-if="error" class="nv-board-panel px-4 py-12 text-center text-sm text-red-500 sm:px-6">
      {{ error }}
    </section>

    <template v-else-if="board">
      <section class="nv-board-panel p-4 sm:p-6">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex min-w-0 flex-1 items-start gap-4 sm:gap-5">
            <router-link :to="buildBoardListRoute()" class="nv-board-icon-wrap flex-shrink-0">
              <img
                v-if="board.iconUrl"
                :src="getOptimizedBoardIconUrl(board.iconUrl, 96)"
                class="nv-board-icon"
                alt=""
                @error="handleImageError($event)"
              />
              <div v-else class="nv-board-icon-fallback">
                <span>{{ board.boardName?.[0] || '#' }}</span>
              </div>
            </router-link>

            <div class="min-w-0 flex-1 space-y-3">
              <router-link :to="buildBoardListRoute()" class="inline-flex max-w-full items-center gap-3">
                <h1 class="truncate text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-3xl">
                  {{ board.boardName }}
                </h1>
              </router-link>

              <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                <span class="inline-flex items-center gap-1.5">
                  <User class="h-4 w-4" />
                  구독자 {{ board.subscriberCount || 0 }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="font-medium text-[var(--nv-ink)]">관리자</span>
                  <UserMenu
                    v-if="board.adminUserId"
                    :user-id="board.adminUserId"
                    :display-name="board.adminDisplayName || t('common.defaultAdminName')"
                    size="inherit"
                  />
                  <span v-else>{{ board.adminDisplayName || '관리자' }}</span>
                </span>
              </div>

              <p class="max-w-3xl text-sm leading-6 text-[var(--nv-ink-soft)] sm:text-[15px]">
                {{ board.description || '게시판 설명이 없습니다.' }}
              </p>
            </div>
          </div>

          <div class="flex w-full flex-col gap-2 lg:w-52 lg:flex-shrink-0">
            <div class="flex gap-2 lg:justify-end">
              <BaseButton
                v-if="authStore.isAuthenticated"
                @click="handleSubscribe"
                size="sm"
                :variant="board.isSubscribed ? 'secondary' : 'primary'"
                class="flex-1 lg:flex-none"
              >
                {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
              </BaseButton>

              <router-link
                v-if="board.isAdmin"
                :to="`/board/${board.boardUrl}/edit`"
                class="nv-board-manage-btn"
              >
                <ShieldCheck class="h-4 w-4" />
                {{ $t('common.manage') }}
              </router-link>
            </div>

            <router-link
              v-if="canWrite"
              :to="`/board/${board.boardUrl}/write`"
              class="nv-board-write-btn"
            >
              {{ $t('common.write') }}
            </router-link>
          </div>
        </div>
      </section>

      <div class="mb-3 sm:mb-6">
        <router-view />
      </div>

      <section id="board-post-list" class="nv-board-panel overflow-hidden">
        <div class="border-b border-[var(--nv-line)] px-4 py-4 sm:px-5">
          <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div class="min-w-0 space-y-3">
              <div class="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="nv-board-filter-chip"
                  :class="{ 'is-active': selectedCategoryId === null }"
                  :aria-pressed="selectedCategoryId === null"
                  @click="toggleCategory(null)"
                >
                  {{ $t('board.detail.filter.all') }}
                </button>
                <button
                  v-for="category in categories"
                  :key="category.categoryId"
                  type="button"
                  class="nv-board-filter-chip"
                  :class="{ 'is-active': selectedCategoryId === category.categoryId }"
                  :aria-pressed="selectedCategoryId === category.categoryId"
                  @click="toggleCategory(category)"
                >
                  {{ category.name }}
                </button>
              </div>

              <p
                v-if="isSearching || selectedCategory"
                class="text-xs font-medium text-[var(--nv-muted)] sm:text-sm"
              >
                <template v-if="isSearching">{{ searchSummary }}</template>
                <template v-else-if="selectedCategory">{{ selectedCategory.name }} 카테고리</template>
              </p>
            </div>

            <div class="w-full xl:max-w-xl list-search-mobile">
              <div class="list-search-row">
                <div class="list-search-group">
                  <select v-model="searchType" class="list-search-select-inline" aria-label="검색 범위">
                    <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                    <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                    <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                    <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
                    <option value="TAG">{{ $t('board.detail.searchType.tag') }}</option>
                  </select>

                  <div class="list-search-input-inner">
                    <BaseInput
                      :id="searchInputElementId"
                      v-model="searchQuery"
                      :placeholder="$t('board.detail.searchPlaceholder')"
                      inputClass="list-search-input"
                      hideLabel
                      @keyup.enter="handleSearch"
                    >
                      <template #prefix>
                        <Search class="hidden h-5 w-5 text-gray-400 sm:block" />
                      </template>
                      <template #suffix>
                        <button
                          v-if="isSearching || searchQuery"
                          type="button"
                          :aria-label="t('layout.recentBoards.clear')"
                          class="hidden cursor-pointer items-center text-gray-400 hover:text-gray-500 dark:hover:text-gray-300 sm:flex"
                          @click="clearSearch"
                        >
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
          </div>
        </div>

        <PostList
          :posts="posts"
          :boardUrl="board.boardUrl"
          :totalCount="totalCount"
          :page="page"
          :size="size"
          :current-sort="sort"
          :currentPostId="currentPostId"
          :linkQuery="route.query"
          @update:sort="handleSortChange"
        />

        <div
          v-if="totalPages > 1"
          class="flex justify-center border-t border-[var(--nv-line)] px-3 py-3 sm:px-4"
        >
          <Pagination
            :currentPage="page"
            :totalPages="totalPages"
            :linkBuilder="buildPaginationRoute"
            @page-change="handlePageChange"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.nv-board-shell {
  color: var(--nv-ink);
}

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 92%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 2rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-icon-wrap {
  display: inline-flex;
  border-radius: 1.75rem;
}

.nv-board-icon,
.nv-board-icon-fallback {
  width: 5.5rem;
  height: 5.5rem;
  border-radius: 1.75rem;
}

.nv-board-icon {
  object-fit: cover;
  border: 1px solid var(--nv-line);
}

.nv-board-icon-fallback {
  align-items: center;
  background: var(--nv-accent-bg);
  color: var(--nv-accent);
  display: inline-flex;
  font-size: 2rem;
  font-weight: 700;
  justify-content: center;
}

.nv-board-manage-btn,
.nv-board-write-btn {
  align-items: center;
  border-radius: 9999px;
  display: inline-flex;
  font-weight: 600;
  justify-content: center;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-board-manage-btn {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  color: var(--nv-ink);
  gap: 0.375rem;
  min-height: 2.25rem;
  padding: 0.5rem 0.9rem;
}

.nv-board-manage-btn:hover {
  background: var(--nv-surface-2);
}

.nv-board-write-btn {
  background: var(--nv-accent);
  border: 1px solid var(--nv-accent);
  color: #fff;
  min-height: 2.75rem;
  padding: 0.75rem 1rem;
}

.nv-board-write-btn:hover {
  filter: brightness(1.03);
}

.nv-board-filter-chip {
  align-items: center;
  background: var(--nv-surface-2);
  border: 1px solid transparent;
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  cursor: pointer;
  display: inline-flex;
  font-size: 0.78rem;
  font-weight: 600;
  justify-content: center;
  min-height: 2.25rem;
  padding: 0.55rem 0.95rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-board-filter-chip:hover {
  border-color: color-mix(in srgb, var(--nv-accent) 18%, var(--nv-line));
  color: var(--nv-ink);
}

.nv-board-filter-chip.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 26%, var(--nv-line));
  color: var(--nv-accent);
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 1.5rem;
  }

  .nv-board-icon,
  .nv-board-icon-fallback {
    border-radius: 1.25rem;
    height: 4.5rem;
    width: 4.5rem;
  }

  .nv-board-filter-chip {
    font-size: 0.75rem;
    min-height: 2rem;
    padding: 0.45rem 0.8rem;
  }
}
</style>
