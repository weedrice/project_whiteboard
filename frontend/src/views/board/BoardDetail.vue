<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
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
import { useBoardListState } from '@/composables/useBoardListState'
import { useRecentBoards } from '@/composables/useRecentBoards'
import { useAuthStore } from '@/stores/auth'
import type { Category } from '@/types'
import { canWriteBoardPost, isGeneralCategoryName } from '@/utils/board'
import { isRestrictedResourceError } from '@/utils/errorHandler'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'
import { isInputFocused } from '@/utils/keyboard'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const {
  page,
  searchQuery,
  searchType,
  isSearching,
  selectedCategoryId,
  sort,
  queryParams,
  searchSummary,
  buildPaginationRoute,
  handleSearch,
  clearSearch,
  toggleCategory,
  handleSortChange,
  handlePageChange,
  resetListState
} = useBoardListState(route, router)

const { useBoardDetail, useBoardPosts, useSubscribeBoard } = useBoard()
const { addRecentBoard } = useRecentBoards()

const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)
const searchInputElementId = 'board-search-input'

const buildBoardListRoute = () => ({
  path: `/board/${boardUrl.value}`,
  query: route.query
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

const boardContentEnabled = computed(() => !!board.value && !boardError.value)
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
  mutate: subscribeMutate,
  isPending: isSubscribePending
} = useSubscribeBoard({
  meta: { errorMessage: false }
})

const categories = computed(() => {
  return board.value?.categories?.filter((category) => !isGeneralCategoryName(category.name)) ?? []
})

const selectedCategory = computed(() => {
  return categories.value.find((category) => category.categoryId === selectedCategoryId.value) ?? null
})

const posts = computed(() => postsData.value?.content ?? [])
const totalCount = computed(() => postsData.value?.totalElements || 0)
const totalPages = computed(() => postsData.value?.totalPages || 0)
const isInitialLoading = computed(() => isBoardLoading.value && !board.value)
const isPostsBusy = computed(() => isPostsLoading.value || isPostsFetching.value)

const error = computed(() => {
  const sourceError = boardError.value ?? postsError.value
  if (!sourceError) return ''
  if (isRestrictedResourceError(sourceError)) {
    return 'This board is restricted.'
  }
  return t('board.loadFailed')
})

const canWrite = computed(() => (
  canWriteBoardPost(board.value, authStore.isAuthenticated, authStore.user?.role)
))

const boardStats = computed(() => ([
  {
    label: 'SUBS',
    value: String(board.value?.subscriberCount ?? 0)
  },
  {
    label: 'CATS',
    value: String(categories.value.length)
  },
  {
    label: 'POSTS',
    value: String(totalCount.value)
  }
]))

const sortOptions = computed(() => ([
  { value: 'createdAt,desc', label: t('common.date') },
  { value: 'likeCount,desc', label: t('common.likes') },
  { value: 'viewCount,desc', label: t('common.views') }
]))

function onCategorySelect(category: Category | null) {
  toggleCategory(category?.categoryId ?? null)
}

function onPageChange(newPage: number) {
  handlePageChange(newPage, totalPages.value)
}

function isSortOptionActive(optionValue: string): boolean {
  const [optionField] = optionValue.split(',')
  const [currentField] = sort.value.split(',')
  return optionField === currentField
}

function handleSubscribe() {
  if (!board.value || isSubscribePending.value) return
  if (board.value.isSubscribed && !window.confirm(t('user.subscriptions.unsubscribeConfirm'))) return

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
  if (isInputFocused() || hasInteractiveFocus()) return

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
      if (authStore.isAuthenticated && !isSubscribePending.value) {
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
  <div class="nv-board-shell space-y-4 sm:space-y-6">
    <div v-if="isInitialLoading" class="space-y-4 sm:space-y-6">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="space-y-5">
          <div class="space-y-3">
            <BaseSkeleton width="96px" height="14px" />
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
              <div class="space-y-2 lg:w-56">
                <BaseSkeleton width="100%" height="96px" rounded="rounded-[28px]" />
                <BaseSkeleton width="100%" height="42px" rounded="rounded-full" />
              </div>
            </div>
          </div>

          <div class="grid gap-3 sm:grid-cols-3">
            <BaseSkeleton v-for="index in 3" :key="index" width="100%" height="72px" rounded="rounded-[24px]" />
          </div>
        </div>
      </section>

      <section class="nv-board-panel overflow-hidden">
        <div class="border-b border-[var(--nv-line)] px-4 py-4 sm:px-5">
          <div class="flex flex-col gap-4">
            <div class="flex flex-wrap gap-2">
              <BaseSkeleton v-for="index in 4" :key="index" width="72px" height="36px" rounded="rounded-full" />
            </div>
            <div class="flex flex-wrap gap-2">
              <BaseSkeleton v-for="index in 3" :key="`sort-${index}`" width="88px" height="34px" rounded="rounded-full" />
            </div>
          </div>
        </div>
        <div class="space-y-3 px-4 py-5 sm:px-5">
          <BaseSkeleton v-for="index in 5" :key="index" width="100%" height="54px" rounded="rounded-2xl" />
        </div>
      </section>
    </div>

    <section v-else-if="error" class="nv-board-panel nv-board-state-panel px-4 py-12 text-center sm:px-6">
      <p class="nv-board-state-kicker">BOARD</p>
      <p class="mt-3 text-sm text-red-500">{{ error }}</p>
    </section>

    <template v-else-if="board">
      <section class="nv-board-panel nv-board-header-panel p-4 sm:p-6">
        <div class="nv-board-header-grid">
          <div class="min-w-0 space-y-5">
            <div class="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-[0.22em] text-[var(--nv-muted)]">
              <span class="nv-board-kicker">Board / {{ board.boardUrl }}</span>
              <span v-if="board.isSubscribed" class="nv-board-status-pill">Following</span>
            </div>

            <div class="flex min-w-0 items-start gap-4 sm:gap-5">
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
                  <h1 class="truncate text-3xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)] sm:text-4xl">
                    {{ board.boardName }}
                  </h1>
                </router-link>

                <div class="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--nv-ink-soft)]">
                  <span class="inline-flex items-center gap-1.5">
                    <User class="h-4 w-4" />
                    <UserMenu
                      v-if="board.adminUserId"
                      :user-id="board.adminUserId"
                      :display-name="board.adminDisplayName || t('board.detail.defaultAdminName')"
                      size="inherit"
                    />
                    <span v-else>{{ board.adminDisplayName || t('board.detail.defaultAdminName') }}</span>
                  </span>
                  <span class="inline-flex items-center gap-1.5 text-[var(--nv-muted)]">
                    <span class="nv-board-dot" aria-hidden="true"></span>
                    <span>{{ board.subscriberCount || 0 }}</span>
                  </span>
                </div>

                <p class="max-w-3xl text-sm leading-6 text-[var(--nv-ink-soft)] sm:text-[15px]">
                  {{ board.description || t('board.list.noDesc') }}
                </p>
              </div>
            </div>
          </div>

          <aside class="nv-board-action-card">
            <div class="space-y-3">
              <p class="nv-board-kicker">At A Glance</p>
              <div class="grid gap-3 sm:grid-cols-3 lg:grid-cols-1">
                <div v-for="stat in boardStats" :key="stat.label" class="nv-board-stat-card">
                  <span class="nv-board-stat-label">{{ stat.label }}</span>
                  <strong class="nv-board-stat-value">{{ stat.value }}</strong>
                </div>
              </div>
            </div>

            <div class="flex flex-col gap-2">
              <BaseButton
                v-if="authStore.isAuthenticated"
                @click="handleSubscribe"
                size="sm"
                :variant="board.isSubscribed ? 'secondary' : 'primary'"
                :disabled="isSubscribePending"
                :aria-busy="isSubscribePending ? 'true' : 'false'"
                class="w-full"
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

            <p class="text-xs text-[var(--nv-muted)]">
              <span class="font-medium text-[var(--nv-ink-soft)]">/</span> search
              <span class="mx-2 text-[var(--nv-line)]">|</span>
              <span class="font-medium text-[var(--nv-ink-soft)]">n</span> write
            </p>
          </aside>
        </div>
      </section>

      <div class="mb-3 sm:mb-6">
        <router-view />
      </div>

      <section id="board-post-list" class="nv-board-panel overflow-hidden">
        <div class="nv-board-toolbar-sticky border-b border-[var(--nv-line)]">
          <div class="nv-board-toolbar-shell px-4 py-4 sm:px-5">
            <div class="flex flex-col gap-4">
              <div class="flex flex-col gap-3 xl:flex-row xl:items-end xl:justify-between">
                <div class="min-w-0">
                  <p class="nv-board-kicker mb-2">Post Index</p>
                  <p class="text-sm font-medium text-[var(--nv-ink)] sm:text-base">
                    <template v-if="isSearching">{{ searchSummary }}</template>
                    <template v-else-if="selectedCategory">{{ selectedCategory.name }}</template>
                    <template v-else>{{ $t('board.detail.filter.all') }}</template>
                  </p>
                </div>

                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="option in sortOptions"
                    :key="option.value"
                    type="button"
                    class="nv-board-sort-pill"
                    :class="{ 'is-active': isSortOptionActive(option.value) }"
                    :aria-pressed="isSortOptionActive(option.value)"
                    @click="handleSortChange(option.value)"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>

              <div class="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="nv-board-filter-chip"
                  :class="{ 'is-active': selectedCategoryId === null }"
                  :aria-pressed="selectedCategoryId === null"
                  @click="onCategorySelect(null)"
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
                  @click="onCategorySelect(category)"
                >
                  {{ category.name }}
                </button>
              </div>

              <div class="nv-board-search-row">
                <div class="nv-board-search-group">
                  <select v-model="searchType" class="nv-board-search-select" aria-label="Search scope">
                    <option value="TITLE_CONTENT">{{ $t('board.detail.searchType.titleContent') }}</option>
                    <option value="TITLE">{{ $t('board.detail.searchType.title') }}</option>
                    <option value="CONTENT">{{ $t('board.detail.searchType.content') }}</option>
                    <option value="AUTHOR">{{ $t('board.detail.searchType.author') }}</option>
                  </select>

                  <div class="nv-board-search-input-wrap">
                    <BaseInput
                      :id="searchInputElementId"
                      v-model="searchQuery"
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
                          :aria-label="t('layout.recentBoards.clear')"
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
              </div>
            </div>
          </div>
        </div>

        <PostList
          :posts="posts"
          :loading="isPostsBusy"
          :boardUrl="board.boardUrl"
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

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
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

.nv-board-kicker,
.nv-board-stat-label,
.nv-board-state-kicker {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  font-weight: 500;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-board-header-grid {
  display: grid;
  gap: 1.5rem;
}

.nv-board-status-pill {
  align-items: center;
  background: color-mix(in srgb, var(--nv-accent) 12%, var(--nv-surface));
  border: 1px solid color-mix(in srgb, var(--nv-accent) 24%, var(--nv-line));
  border-radius: 9999px;
  color: var(--nv-accent);
  display: inline-flex;
  padding: 0.3rem 0.7rem;
}

.nv-board-dot {
  background: var(--nv-accent);
  border-radius: 9999px;
  display: inline-flex;
  height: 0.35rem;
  width: 0.35rem;
}

.nv-board-action-card {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-accent-bg) 55%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid color-mix(in srgb, var(--nv-accent) 12%, var(--nv-line));
  border-radius: 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
  padding: 1.15rem;
}

.nv-board-stat-card {
  background: color-mix(in srgb, var(--nv-surface) 86%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-height: 4.5rem;
  padding: 0.9rem 1rem;
}

.nv-board-stat-value {
  color: var(--nv-ink);
  font-size: 1.2rem;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.nv-board-manage-btn,
.nv-board-write-btn {
  align-items: center;
  border-radius: 1rem;
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
  min-height: 2.75rem;
  padding: 0.7rem 1rem;
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
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 0.95rem;
  color: var(--nv-ink-soft);
  cursor: pointer;
  display: inline-flex;
  font-size: 0.8rem;
  font-weight: 600;
  justify-content: center;
  min-height: 2.35rem;
  padding: 0.55rem 0.95rem;
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

.nv-board-toolbar-shell {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
}

.nv-board-sort-pill {
  align-items: center;
  background: color-mix(in srgb, var(--nv-surface) 88%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  color: var(--nv-ink-soft);
  display: inline-flex;
  font-size: 0.76rem;
  font-weight: 600;
  justify-content: center;
  min-height: 2.1rem;
  padding: 0.45rem 0.8rem;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.nv-board-sort-pill:hover,
.nv-board-sort-pill.is-active {
  border-color: color-mix(in srgb, var(--nv-accent) 22%, var(--nv-line));
  color: var(--nv-accent);
}

.nv-board-sort-pill.is-active {
  background: color-mix(in srgb, var(--nv-accent) 12%, var(--nv-surface));
}

.nv-board-search-row {
  width: 100%;
}

.nv-board-search-group {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: minmax(8rem, 10rem) minmax(0, 1fr) auto;
}

.nv-board-search-select,
:deep(.nv-board-search-input) {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-color: var(--nv-line);
  border-radius: 1rem;
  color: var(--nv-ink);
  min-height: 2.9rem;
}

.nv-board-search-select {
  outline: none;
  padding: 0.75rem 0.95rem;
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
  min-height: 2.9rem;
  white-space: nowrap;
}

.nv-board-state-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, #ef4444 8%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 94%, transparent);
}

@media (min-width: 1024px) {
  .nv-board-header-grid {
    align-items: start;
    grid-template-columns: minmax(0, 1fr) minmax(18rem, 20rem);
  }

  .nv-board-toolbar-sticky {
    position: sticky;
    top: 4.75rem;
    z-index: 19;
    background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
    backdrop-filter: blur(14px);
  }
}

@media (max-width: 1023px) {
  .nv-board-search-group {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 1.5rem;
  }

  .nv-board-action-card,
  .nv-board-stat-card {
    border-radius: 1.35rem;
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

  .nv-board-sort-pill {
    min-height: 2rem;
  }
}
</style>
