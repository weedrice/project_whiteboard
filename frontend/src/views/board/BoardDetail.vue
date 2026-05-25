<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, ChevronUp, Megaphone, Search, ShieldCheck, User, X } from 'lucide-vue-next'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { useBoard } from '@/composables/useBoard'
import { useBoardListState } from '@/composables/useBoardListState'
import { useBoardDetailShortcuts } from '@/composables/useKeyboardShortcuts'
import { useRecentBoards } from '@/composables/useRecentBoards'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthStore } from '@/stores/auth'
import { canWriteBoardPost, resolveDefaultCategory } from '@/utils/board'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { formatRelativeDate } from '@/utils/date'
import type { PostSummary } from '@/types'

const NOTICE_PREVIEW_LIMIT = 3
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const {
  page,
  searchQuery,
  searchType,
  isSearching,
  conceptOnly,
  selectedCategoryId,
  sort,
  queryParams,
  buildPaginationRoute,
  handleSearch,
  clearSearch,
  activateAllPostsFilter,
  toggleConceptPosts,
  toggleCategory,
  handleSortChange,
  handlePageChange,
  resetListState
} = useBoardListState(route, router)

const { useBoardDetail, useBoardPosts, useBoardNotices, useSubscribeBoard } = useBoard()
const { addRecentBoard } = useRecentBoards()
const { confirm } = useConfirm()

const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)
const suppressedCurrentPostId = ref<string | null>(null)
const searchInputElementId = 'board-search-input'
const listQuery = computed(() => {
  const { fromCreate, ...query } = route.query
  return query
})
const highlightedPostId = computed(() => (
  currentPostId.value && currentPostId.value !== suppressedCurrentPostId.value
    ? currentPostId.value
    : undefined
))

const buildBoardListRoute = () => ({
  path: `/board/${boardUrl.value}`,
  query: listQuery.value
})

const {
  data: board,
  isLoading: isBoardLoading,
  error: boardError
} = useBoardDetail(boardUrl, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})

function decodeBoardUrlTitle(rawBoardUrl: string): string {
  try {
    return decodeURIComponent(rawBoardUrl).trim()
  } catch {
    return rawBoardUrl.trim()
  }
}

const boardTitle = computed(() => {
  const boardName = board.value?.boardName?.trim()
  if (boardName) return boardName
  return decodeBoardUrlTitle(boardUrl.value || '') || 'Board'
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

const boardContentEnabled = computed(() => !boardError.value)
const {
  data: postsData,
  isLoading: isPostsLoading,
  isFetching: isPostsFetching,
  error: postsError
} = useBoardPosts(boardUrl, queryParams, isSearching, boardContentEnabled, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})

const {
  data: noticesData
} = useBoardNotices(boardUrl, boardContentEnabled, {
  meta: { errorMessage: false },
  requestConfig: { skipGlobalErrorHandler: true }
})

const {
  mutate: subscribeMutate,
  isPending: isSubscribePending
} = useSubscribeBoard({
  meta: { errorMessage: false }
})

const defaultCategory = computed(() => resolveDefaultCategory(board.value?.categories))
const categories = computed(() => (
  board.value?.categories?.filter((category) => category.categoryId !== defaultCategory.value?.categoryId) ?? []
))

const posts = computed(() => postsData.value?.content ?? [])
const isNoticesExpanded = ref(false)
const notices = computed(() => (
  [...(noticesData.value ?? [])].sort((left, right) => {
    const leftTime = new Date(left.createdAt).getTime()
    const rightTime = new Date(right.createdAt).getTime()
    if (leftTime !== rightTime) {
      return rightTime - leftTime
    }
    return Number(right.postId ?? 0) - Number(left.postId ?? 0)
  })
))
const visibleNotices = computed(() => (
  isNoticesExpanded.value ? notices.value : notices.value.slice(0, NOTICE_PREVIEW_LIMIT)
))
const hasNoticeOverflow = computed(() => notices.value.length > NOTICE_PREVIEW_LIMIT)
const totalPages = computed(() => postsData.value?.totalPages || 0)
const isInitialLoading = computed(() => isBoardLoading.value && !board.value)
const currentListKey = computed(() => JSON.stringify({
  boardUrl: boardUrl.value,
  ...queryParams.value
}))
const lastResolvedListKey = ref(currentListKey.value)
const showPostListLoading = computed(() => (
  (isPostsLoading.value && posts.value.length === 0)
  || (isPostsFetching.value && currentListKey.value !== lastResolvedListKey.value)
))

const blockingError = computed(() => {
  const sourceError = boardError.value ?? (posts.value.length === 0 ? postsError.value : null)
  if (!sourceError) return ''
  if (isRestrictedResourceError(sourceError)) {
    return 'This board is restricted.'
  }
  return t('board.loadFailed')
})

const transientListError = computed(() => {
  if (!postsError.value || posts.value.length === 0) {
    return ''
  }
  return t('board.loadFailed')
})

const canWrite = computed(() => (
  canWriteBoardPost(board.value, authStore.isAuthenticated, authStore.user?.role)
))
const isAllPostsActive = computed(() => (
  !conceptOnly.value
  && selectedCategoryId.value === null
))

function onPageChange(newPage: number) {
  handlePageChange(newPage, totalPages.value)
}

function getNoticeRoute(notice: PostSummary) {
  return {
    path: `/board/${boardUrl.value}/post/${notice.postId}`,
    query: listQuery.value
  }
}

async function handleSubscribe() {
  if (!board.value || isSubscribePending.value) return
  if (board.value.isSubscribed) {
    const isConfirmed = await confirm(t('user.subscriptions.unsubscribeConfirm'))
    if (!isConfirmed) return
  }

  subscribeMutate({
    boardUrl: board.value.boardUrl,
    isSubscribed: board.value.isSubscribed ?? false
  })
}

function hasInteractiveFocus(): boolean {
  if (typeof document === 'undefined') {
    return false
  }

  const activeElement = document.activeElement as HTMLElement | null
  if (!activeElement || activeElement === document.body) {
    return false
  }

  if (activeElement.isContentEditable) {
    return true
  }

  const tagName = activeElement.tagName.toLowerCase()
  return ['input', 'textarea', 'select', 'button', 'a'].includes(tagName)
}

watch(() => route.params.boardUrl, () => {
  resetListState()
  isNoticesExpanded.value = false
  suppressedCurrentPostId.value = null
})

watch([currentPostId, () => route.query.fromCreate], ([postId, fromCreate]) => {
  if (!postId) {
    suppressedCurrentPostId.value = null
    return
  }

  if (fromCreate === '1') {
    suppressedCurrentPostId.value = postId
    router.replace({
      path: route.path,
      query: listQuery.value
    })
  }
}, { immediate: true })

watch(board, (newBoard) => {
  if (!newBoard) return

  addRecentBoard({
    boardUrl: newBoard.boardUrl,
    boardName: newBoard.boardName,
    iconUrl: newBoard.iconUrl
  })
}, { immediate: true })

watch([currentListKey, isPostsFetching], ([nextListKey, fetching]) => {
  if (!fetching) {
    lastResolvedListKey.value = nextListKey
  }
}, { immediate: true })

useBoardDetailShortcuts({
  goToNextPage: () => {
    if (page.value < totalPages.value - 1) {
      page.value++
    }
  },
  goToPrevPage: () => {
    if (page.value > 0) {
      page.value--
    }
  },
  goToFirstPage: () => {
    page.value = 0
  },
  goToLastPage: () => {
    if (totalPages.value > 0) {
      page.value = totalPages.value - 1
    }
  },
  goToWrite: () => {
    if (board.value) {
      router.push(`/board/${board.value.boardUrl}/write`)
    }
  },
  toggleSubscribe: handleSubscribe,
  focusSearch: () => {
    const searchInput = document.getElementById(searchInputElementId) as HTMLInputElement | null
    searchInput?.focus()
  },
  canWrite: () => canWrite.value && !!board.value,
  canGoNext: () => page.value < totalPages.value - 1,
  canGoPrev: () => page.value > 0,
  canToggleSubscribe: () => !isSubscribePending.value,
  shouldIgnoreShortcut: hasInteractiveFocus
})
</script>

<template>
  <div class="nv-board-shell">
    <div v-if="isInitialLoading" class="nv-board-stack">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex items-start gap-3">
            <BaseSkeleton width="5rem" height="5rem" rounded="rounded-[14px]" className="flex-shrink-0" />
            <div class="space-y-2.5">
              <BaseSkeleton width="220px" height="34px" />
              <div class="flex gap-3">
                <BaseSkeleton width="90px" height="18px" />
                <BaseSkeleton width="120px" height="18px" />
              </div>
              <BaseSkeleton width="320px" height="18px" />
            </div>
          </div>
          <div class="space-y-2 lg:w-32">
            <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
            <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
          </div>
        </div>
      </section>

      <section class="nv-board-panel nv-board-list-panel overflow-hidden">
        <div class="border-b border-[var(--nv-line)] px-4 py-3 sm:px-5">
          <div class="flex gap-2 overflow-hidden">
            <BaseSkeleton v-for="index in 4" :key="index" width="72px" height="34px" rounded="rounded-[10px]" />
          </div>
        </div>
        <div class="space-y-3 px-4 py-5 sm:px-5">
          <BaseSkeleton v-for="index in 5" :key="index" width="100%" height="54px" rounded="rounded-[12px]" />
        </div>
      </section>
    </div>

    <section v-else-if="blockingError" class="nv-board-panel nv-board-state-panel px-4 py-12 text-center sm:px-6">
      <p class="nv-board-state-kicker">BOARD</p>
      <p class="mt-3 text-sm text-red-500">{{ blockingError }}</p>
    </section>

    <template v-else-if="board">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div class="flex min-w-0 flex-1 items-start gap-3 sm:gap-4">
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

            <div class="min-w-0 flex-1 space-y-2.5">
              <div class="flex flex-wrap items-center gap-2">
                <router-link :to="buildBoardListRoute()" class="inline-flex min-w-0 max-w-full items-center gap-2">
                  <h1 class="truncate text-2xl font-semibold tracking-[-0.04em] text-[var(--nv-ink)] sm:text-3xl">
                    {{ board.boardName }}
                  </h1>
                </router-link>

                <BaseButton
                  v-if="authStore.isAuthenticated"
                  @click="handleSubscribe"
                  size="sm"
                  :variant="board.isSubscribed ? 'secondary' : 'primary'"
                  :disabled="isSubscribePending"
                  :aria-busy="isSubscribePending ? 'true' : 'false'"
                  class="nv-board-subscribe-btn"
                >
                  {{ board.isSubscribed ? $t('common.unsubscribe') : $t('common.subscribe') }}
                </BaseButton>
              </div>

              <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                <span class="inline-flex items-center gap-1.5">
                  <User class="h-4 w-4" />
                  {{ $t('common.subscribers') }} {{ board.subscriberCount || 0 }}
                </span>
                <span class="inline-flex items-center gap-1.5">
                  <span class="font-medium text-[var(--nv-ink)]">{{ t('board.detail.defaultAdminName') }}</span>
                  <UserMenu
                    v-if="board.adminUserId"
                    :user-id="board.adminUserId"
                    :display-name="board.adminDisplayName || t('board.detail.defaultAdminName')"
                    size="inherit"
                  />
                  <span v-else>{{ board.adminDisplayName || t('board.detail.defaultAdminName') }}</span>
                </span>
              </div>

              <p class="max-w-3xl text-sm leading-6 text-[var(--nv-ink-soft)] sm:text-[15px]">
                {{ board.description || t('board.list.noDesc') }}
              </p>
            </div>
          </div>

          <div class="flex w-full flex-col gap-2 lg:w-auto lg:min-w-[7rem] lg:items-end lg:self-stretch">
            <div class="flex gap-2 lg:justify-end">
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
              class="nv-board-write-btn lg:mt-auto"
            >
              {{ $t('common.write') }}
            </router-link>
          </div>
        </div>
      </section>

      <div v-if="currentPostId" class="mb-3 sm:mb-6">
        <router-view />
      </div>

      <section id="board-post-list" class="nv-board-panel nv-board-list-panel overflow-hidden">
        <div v-if="notices.length" class="nv-board-notices">
          <div class="nv-board-notice-heading">
            <span class="inline-flex items-center gap-1.5">
              <Megaphone class="h-4 w-4" />
              {{ $t('board.detail.notices.title') }}
            </span>
            <span class="text-[11px] font-medium text-[var(--nv-muted)]">
              {{ notices.length }}
            </span>
          </div>

          <div class="divide-y divide-[var(--nv-line-soft)]">
            <router-link
              v-for="notice in visibleNotices"
              :key="notice.postId"
              :to="getNoticeRoute(notice)"
              class="nv-board-notice-row"
              :class="{ 'is-current': String(notice.postId) === String(highlightedPostId ?? '') }"
            >
              <span class="nv-board-notice-badge">{{ $t('common.notice') }}</span>
              <span class="min-w-0 flex-1 truncate text-sm font-semibold text-[var(--nv-ink)]">
                {{ notice.title }}
              </span>
              <span class="hidden flex-shrink-0 text-xs text-[var(--nv-muted)] sm:inline">
                {{ formatRelativeDate(notice.createdAt) }}
              </span>
            </router-link>
          </div>

          <button
            v-if="hasNoticeOverflow"
            type="button"
            class="nv-board-notice-more"
            :aria-expanded="isNoticesExpanded"
            @click="isNoticesExpanded = !isNoticesExpanded"
          >
            <span></span>
            <span class="inline-flex items-center gap-1">
              {{ isNoticesExpanded ? $t('board.detail.notices.collapse') : $t('board.detail.notices.more') }}
              <ChevronUp v-if="isNoticesExpanded" class="h-3.5 w-3.5" />
              <ChevronDown v-else class="h-3.5 w-3.5" />
            </span>
            <span></span>
          </button>
        </div>

        <div class="nv-board-toolbar-sticky px-4 py-3 sm:px-5">
          <div class="nv-board-filter-rail" role="group" :aria-label="t('board.detail.filterLabel')">
            <div class="nv-board-filter-track">
              <button
                type="button"
                class="nv-board-filter-chip"
                :class="{ 'is-active': isAllPostsActive }"
                :aria-pressed="isAllPostsActive"
                @click="activateAllPostsFilter"
              >
                {{ $t('board.detail.filter.all') }}
              </button>
              <button
                type="button"
                class="nv-board-filter-chip"
                :class="{ 'is-active': conceptOnly }"
                :aria-pressed="conceptOnly"
                @click="toggleConceptPosts"
              >
                {{ $t('board.detail.filter.concept') }}
              </button>
              <button
                v-for="category in categories"
                :key="category.categoryId"
                type="button"
                class="nv-board-filter-chip"
                :class="{ 'is-active': selectedCategoryId === category.categoryId }"
                :aria-pressed="selectedCategoryId === category.categoryId"
                @click="toggleCategory(category.categoryId)"
              >
                {{ category.name }}
              </button>
            </div>
          </div>
        </div>

        <PostList
          :posts="posts"
          :loading="showPostListLoading"
          :boardUrl="board.boardUrl"
          :current-sort="sort"
          :currentPostId="highlightedPostId"
          :linkQuery="listQuery"
          @update:sort="handleSortChange"
        />

        <div class="border-t border-[var(--nv-line)] px-4 py-4 sm:px-5">
          <div class="nv-board-search-row">
            <div class="nv-board-search-group">
              <select
                id="board-search-type"
                v-model="searchType"
                name="searchType"
                class="nv-board-search-select"
                :aria-label="t('board.detail.searchScopeLabel')"
              >
                <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
              </select>

              <div class="nv-board-search-input-wrap">
                <BaseInput
                  :id="searchInputElementId"
                  v-model="searchQuery"
                  name="searchQuery"
                  autocomplete="off"
                  :label="$t('board.detail.searchPlaceholder')"
                  :aria-label="$t('board.detail.searchPlaceholder')"
                  :placeholder="$t('board.detail.searchPlaceholder')"
                  inputClass="nv-board-search-input"
                  hideLabel
                  @keyup.enter="handleSearch"
                >
                  <template #prefix>
                    <Search class="h-4 w-4 text-[var(--nv-muted)]" />
                  </template>
                  <template #suffix>
                    <button
                      v-if="isSearching || searchQuery"
                      type="button"
                      :aria-label="t('board.detail.clearSearch')"
                      class="flex cursor-pointer items-center text-[var(--nv-muted)] hover:text-[var(--nv-ink)]"
                      @click="clearSearch"
                    >
                      <X class="h-4 w-4" />
                    </button>
                  </template>
                </BaseInput>
              </div>

              <BaseButton @click="handleSearch" variant="secondary" type="button" class="nv-board-search-btn">
                {{ $t('search.doSearch') }}
              </BaseButton>
            </div>

            <router-link
              v-if="canWrite"
              :to="`/board/${board.boardUrl}/write`"
              class="nv-board-write-btn nv-board-search-write-btn"
            >
              {{ $t('common.write') }}
            </router-link>
          </div>

          <p v-if="transientListError" class="mt-2 text-center text-xs text-red-500">
            {{ transientListError }}
          </p>
        </div>

        <div
          v-if="totalPages > 1"
          class="flex justify-center px-3 py-3 sm:px-4"
        >
          <Pagination
            :currentPage="page"
            :totalPages="totalPages"
            :linkBuilder="buildPaginationRoute"
            @page-change="onPageChange"
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

.nv-board-stack {
  display: grid;
  gap: 0;
}

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-header-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.nv-board-list-panel {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.nv-board-icon-wrap {
  border-radius: 0.9rem;
  display: inline-flex;
}

.nv-board-icon,
.nv-board-icon-fallback {
  border-radius: 0.9rem;
  height: 5.5rem;
  width: 5.5rem;
}

.nv-board-icon {
  border: 1px solid var(--nv-line);
  object-fit: cover;
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

.nv-board-state-kicker {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  font-weight: 500;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-board-manage-btn,
.nv-board-subscribe-btn,
.nv-board-write-btn {
  align-items: center;
  border-radius: 0.55rem;
  display: inline-flex;
  font-size: 0.82rem;
  font-weight: 600;
  justify-content: center;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, filter 0.2s ease, box-shadow 0.2s ease;
}

.nv-board-subscribe-btn {
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  color: var(--nv-ink);
  gap: 0.375rem;
  min-height: 1.95rem;
  padding: 0.35rem 0.65rem;
}

.nv-board-manage-btn:hover {
  background: var(--nv-surface-2);
}

.nv-board-write-btn {
  background: var(--nv-accent);
  border: 1px solid var(--nv-accent);
  color: #fff;
  height: 2.2rem;
  justify-content: center;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 4.75rem;
  padding: 0 0.8rem;
}

.nv-board-write-btn:hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn.btn-secondary:not(:disabled):hover {
  background: var(--nv-surface-2);
}

.nv-board-subscribe-btn.btn-primary:not(:disabled):hover {
  filter: brightness(0.94);
}

.nv-board-subscribe-btn:not(:disabled),
.nv-board-write-btn,
.nv-board-search-btn:not(:disabled) {
  cursor: pointer;
}

.nv-board-subscribe-btn:not(:disabled):active,
.nv-board-write-btn:active,
.nv-board-search-btn:not(:disabled):active {
  filter: brightness(0.9);
}

.nv-board-subscribe-btn:focus-visible,
.nv-board-write-btn:focus-visible,
.nv-board-search-btn:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 18%, transparent);
  outline: none;
}

.nv-board-subscribe-btn:disabled,
.nv-board-search-btn:disabled {
  cursor: not-allowed;
}

.nv-board-filter-chip {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 0.6rem;
  color: var(--nv-ink-soft);
  cursor: pointer;
  display: inline-flex;
  font-size: 0.8rem;
  font-weight: 600;
  justify-content: center;
  min-height: 2.1rem;
  padding: 0.45rem 0.8rem;
  transition: transform 0.12s ease, background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-board-filter-chip:hover {
  border-color: color-mix(in srgb, var(--nv-accent) 18%, var(--nv-line));
  color: var(--nv-ink);
}

.nv-board-filter-chip:active {
  transform: scale(0.98);
}

.nv-board-filter-chip.is-active {
  background: var(--nv-accent-bg);
  border-color: color-mix(in srgb, var(--nv-accent) 26%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-board-filter-rail {
  margin-inline: -0.15rem;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 0.15rem;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.nv-board-filter-rail::-webkit-scrollbar {
  display: none;
}

.nv-board-filter-track {
  display: inline-flex;
  gap: 0.5rem;
  min-width: 100%;
  width: max-content;
}

.nv-board-filter-track > .nv-board-filter-chip {
  flex: 0 0 auto;
}

.nv-board-notices {
  background: color-mix(in srgb, var(--nv-surface-2) 42%, transparent);
  border-bottom: 1px solid var(--nv-line);
}

.nv-board-notice-heading {
  align-items: center;
  color: var(--nv-ink-soft);
  display: flex;
  font-size: 0.72rem;
  font-weight: 700;
  justify-content: space-between;
  letter-spacing: 0.12em;
  padding: 0.85rem 1rem 0.4rem;
  text-transform: uppercase;
}

.nv-board-notice-row {
  align-items: center;
  display: flex;
  gap: 0.65rem;
  min-height: 2.75rem;
  padding: 0.7rem 1rem;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.nv-board-notice-row:hover,
.nv-board-notice-row.is-current {
  background: color-mix(in srgb, var(--nv-accent-bg) 70%, transparent);
}

.nv-board-notice-badge {
  align-items: center;
  background: color-mix(in srgb, #ef4444 12%, transparent);
  border: 1px solid color-mix(in srgb, #ef4444 25%, var(--nv-line));
  border-radius: 9999px;
  color: #dc2626;
  display: inline-flex;
  flex-shrink: 0;
  font-size: 0.62rem;
  font-weight: 700;
  justify-content: center;
  line-height: 1;
  min-height: 1.35rem;
  padding: 0.15rem 0.55rem;
}

.nv-board-notice-more {
  align-items: center;
  color: var(--nv-muted);
  cursor: pointer;
  display: grid;
  font-size: 0.72rem;
  font-weight: 700;
  gap: 0.65rem;
  grid-template-columns: 1fr auto 1fr;
  letter-spacing: 0.08em;
  min-height: 2rem;
  padding: 0 1rem 0.55rem;
  text-transform: uppercase;
  width: 100%;
}

.nv-board-notice-more > span:first-child,
.nv-board-notice-more > span:last-child {
  background: var(--nv-line);
  height: 1px;
}

.nv-board-notice-more:hover {
  color: var(--nv-accent);
}

.nv-board-toolbar-sticky {
  position: relative;
}

.nv-board-search-row {
  align-items: center;
  display: grid;
  gap: 0.75rem;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  width: 100%;
}

.nv-board-search-group {
  display: grid;
  grid-column: 2;
  gap: 0.5rem;
  grid-template-columns: minmax(6.1rem, 7.25rem) minmax(0, 20rem) auto;
  justify-self: center;
  width: min(100%, 34rem);
}

.nv-board-search-select,
:deep(.nv-board-search-input) {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-color: var(--nv-line);
  border-radius: 0.45rem;
  color: var(--nv-ink);
  height: 2.2rem;
  line-height: 1;
  min-height: 2.2rem;
}

.nv-board-search-select {
  font-size: 0.74rem;
  font-weight: 500;
  outline: none;
  padding: 0 0.55rem;
}

:deep(.nv-board-search-input) {
  padding-block: 0;
}

.nv-board-search-select:focus,
:deep(.nv-board-search-input:focus) {
  border-color: color-mix(in srgb, var(--nv-accent) 30%, var(--nv-line));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 14%, transparent);
}

.nv-board-search-input-wrap {
  min-width: 0;
}

.nv-board-search-btn {
  background: var(--nv-accent);
  border-color: var(--nv-accent);
  border-radius: 0.45rem;
  color: #fff;
  height: 2.2rem;
  line-height: 1;
  min-height: 2.2rem;
  min-width: 3.9rem;
  padding-inline: 0.6rem;
  padding-block: 0;
  transition: filter 0.2s ease, box-shadow 0.2s ease;
  white-space: nowrap;
}

.nv-board-search-btn:hover {
  filter: brightness(0.94);
}

.nv-board-search-write-btn {
  grid-column: 3;
  justify-self: end;
  white-space: nowrap;
}

.nv-board-state-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, #ef4444 8%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 94%, transparent);
}

@media (max-width: 1023px) {
  .nv-board-search-row {
    grid-template-columns: 1fr;
  }

  .nv-board-search-group {
    grid-column: 1;
    grid-template-columns: 1fr;
    justify-self: center;
    width: min(100%, 24rem);
  }

  .nv-board-search-write-btn {
    grid-column: 1;
    justify-self: center;
    width: min(100%, 24rem);
  }
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 0.85rem;
  }

  .nv-board-icon,
  .nv-board-icon-fallback {
    border-radius: 0.8rem;
    height: 4.5rem;
    width: 4.5rem;
  }

  .nv-board-filter-chip {
    font-size: 0.75rem;
    min-height: 1.95rem;
    padding: 0.4rem 0.7rem;
  }
}
</style>
