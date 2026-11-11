<template>
  <div class="search-page min-w-0 w-full" data-testid="search-page">
    <div class="search-query-panel mb-6 rounded-xl border nv-border-strong nv-elevated-surface p-4 sm:p-6" data-testid="search-query-panel">
      <PageHeader :title="$t('search.results')" />
      <p v-if="searchQuery" class="mt-2 nv-text-muted">
        {{ $t('search.query') }}: <span class="font-semibold nv-accent-text">"{{ searchQuery
        }}"</span>
      </p>

      <form class="mt-4 space-y-3" role="search" @submit.prevent="handleSearchSubmit">
        <div class="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-2 sm:grid-cols-[auto_minmax(0,1fr)_auto]" data-testid="search-query-controls">
          <BaseSelect
            v-model="searchTypeInput"
            name="searchType"
            :label="$t('search.scopeFilter')"
            :options="searchTypeOptions"
            input-class="h-11"
            class="min-w-0 w-32 sm:w-40"
            hide-label
          />
          <BaseInput
            id="search-page-query"
            v-model="searchInput"
            name="searchPageQuery"
            autocomplete="off"
            :label="$t('search.placeholder')"
            :placeholder="$t('search.placeholder')"
            inputClass="h-11"
            class="min-w-0"
            hideLabel
          />
          <BaseButton
            type="submit"
            variant="secondary"
            class="col-span-2 h-11 sm:col-span-1"
            :disabled="!searchInput.trim() || !isCustomDateRangeValid"
          >
            {{ $t('search.doSearch', { query: searchInput.trim() }) }}
          </BaseButton>
        </div>
        <div class="flex min-w-0 flex-wrap items-end gap-2" data-testid="search-period-controls">
          <div class="relative min-w-0 w-32 sm:w-40">
            <BaseSelect
              v-model="periodInput"
              name="period"
              :label="$t('search.periodFilter')"
              :options="periodOptions"
              label-class="pl-3"
              input-class="h-11 appearance-none pr-10"
              class="min-w-0"
            />
            <ChevronDown class="pointer-events-none absolute bottom-3 right-3 h-5 w-5 nv-text-muted" aria-hidden="true" />
          </div>
          <BaseInput
            v-if="periodInput === 'CUSTOM'"
            id="search-from-filter"
            v-model="fromInput"
            name="from"
            type="date"
            @click="openDatePicker"
            :label="$t('search.fromDate')"
            :max="toInput || undefined"
            label-class="pl-3"
            inputClass="search-date-input h-11"
            class="min-w-0 w-40"
          />
          <BaseInput
            v-if="periodInput === 'CUSTOM'"
            id="search-to-filter"
            v-model="toInput"
            name="to"
            type="date"
            @click="openDatePicker"
            :label="$t('search.toDate')"
            :min="fromInput || undefined"
            label-class="pl-3"
            inputClass="search-date-input h-11"
            class="min-w-0 w-40"
          />
          <BaseInput
            v-if="searchTypeInput !== 'AUTHOR'"
            id="search-author-filter"
            v-model="authorInput"
            name="author"
            :label="$t('search.authorFilter')"
            :placeholder="$t('search.authorFilter')"
            label-class="pl-3"
            inputClass="h-11"
            class="min-w-0 w-32 sm:w-40"
          />
        </div>
      </form>

      <section
        v-if="recentKeywords.length > 0 || hasRecentSearchError"
        data-testid="recent-keywords"
        class="mt-4 min-w-0"
        :aria-label="$t('search.recentKeywords')"
      >
        <div class="recent-boards-inner">
          <h2 class="recent-boards-label">{{ $t('search.recentKeywords') }}</h2>
          <div class="recent-boards-list">
            <div v-for="keyword in recentKeywords" :key="keyword.keyword" class="recent-board-chip">
              <button
                type="button"
                class="recent-board-link cursor-pointer nv-press-surface"
                :aria-label="$t('search.searchByKeyword', { keyword: keyword.keyword })"
                :title="keyword.keyword"
                @click="searchKeyword(keyword.keyword)"
              >
                <span class="recent-board-name">{{ keyword.keyword }}</span>
              </button>
              <button
                type="button"
                class="recent-board-remove nv-press-surface"
                :aria-label="$t('search.deleteRecent', { keyword: keyword.keyword })"
                @click="handleDeleteRecentKeyword(keyword.logId, keyword.keyword)"
              >
                <X class="h-3 w-3" aria-hidden="true" />
              </button>
            </div>
          </div>
          <button
            v-if="recentKeywords.length > 0"
            type="button"
            class="recent-boards-clear nv-press-surface"
            @click="handleClearRecentKeywords"
          >
            {{ $t('search.clearRecent') }}
          </button>
        </div>
        <ErrorState
          v-if="hasRecentSearchError"
          class="py-3"
          title-tag="h3"
          :show-icon="false"
          :message="$t('common.messages.loadFailed')"
          show-retry
          @retry="retryRecentKeywords"
        />
      </section>

      <div
        v-if="activeFilterChips.length > 0"
        class="mt-4 flex flex-wrap items-center gap-2"
        :aria-label="$t('search.activeFilters')"
      >
        <button
          v-for="chip in activeFilterChips"
          :key="chip.key"
          type="button"
          class="cursor-pointer nv-focus-ring nv-press-surface nv-touch-target inline-flex max-w-full items-center gap-1 rounded-full border nv-border px-3 py-1.5 text-xs font-medium nv-text nv-hover-surface"
          :aria-label="$t('search.clearFilter', { label: chip.label })"
          @click="removeFilter(chip.key)"
        >
          <span class="min-w-0 truncate">{{ chip.label }}</span>
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <p v-if="hasKeywordFilters" class="mt-2 text-xs nv-text-muted">
        {{ $t('search.keywordFilterNotice') }}
      </p>
      <p
        v-if="hasSearchQuery && !isLoading && !isSemanticLoading && !hasSearchError"
        class="mt-3 text-sm nv-text-muted"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {{ t('search.resultSummary', { count: totalResultCount }) }}
      </p>
    </div>

    <div data-testid="search-results" class="min-w-0">
      <SearchSidebarPanel
        data-testid="popular-keywords"
        class="mb-6"
        :title="$t('search.popularKeywords')"
        :loading="isPopularKeywordsLoading"
        :error="isPopularKeywordsError"
        :empty="popularKeywords.length === 0"
        :empty-text="$t('search.noResults')"
        @retry="refetchPopularKeywords"
      >
        <div class="flex flex-wrap gap-2">
          <button
            v-for="keyword in popularKeywords"
            :key="keyword.keyword"
            type="button"
            class="cursor-pointer nv-focus-ring nv-press-surface nv-touch-target max-w-full rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface"
            :aria-label="$t('search.searchByKeyword', { keyword: keyword.keyword })"
            @click="searchKeyword(keyword.keyword)"
          >
            <span class="block max-w-full truncate">{{ keyword.keyword }}</span>
          </button>
        </div>
      </SearchSidebarPanel>

      <div v-if="isLoading" class="text-center py-10">
        <BaseSpinner size="lg" />
      </div>

      <EmptyState v-else-if="!hasSearchQuery" data-testid="search-page-empty" title-tag="h2" :title="$t('search.placeholder')" :icon="Search"
                  container-class="nv-surface nv-elevated-surface shadow rounded-lg" />

      <ErrorState
        v-else-if="hasSearchError"
        title-tag="h2"
        :title="$t('common.messages.defaultTitle')"
        :message="$t('common.messages.loadFailed')"
        show-retry
        @retry="retrySearch"
      />

      <EmptyState v-else-if="!hasAnyResults && !isSemanticLoading" data-testid="search-page-empty" title-tag="h2" :title="$t('search.noResults')"
                  :description="searchQuery ? `${$t('search.noResultsFor', { query: searchQuery })} ${$t('search.noResultsSuggestion')}` : $t('search.noResultsSuggestion')" :icon="Search"
                  container-class="nv-surface nv-elevated-surface shadow rounded-lg" />

      <div v-else class="space-y-8">
        <div v-if="keywordResultsEmpty && isSemanticLoading" class="text-center py-6">
          <BaseSpinner size="md" />
        </div>

        <SemanticSearchResults
          v-if="keywordResultsEmpty && semanticResults.length > 0"
          :results="semanticResults"
          class="search-result-group"
        />

        <!-- Board Results -->
        <div v-if="boards.length > 0" class="search-result-group min-w-0">
          <h2 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
            <Layout class="w-5 h-5" />
            {{ $t('common.board') }}
          </h2>
          <div class="grid min-w-0 grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <BoardCard
              v-for="board in boards"
              :key="board.boardId"
              :board="board"
              variant="compact"
            />
          </div>
        </div>

        <!-- Post Results -->
        <div v-if="posts.length > 0 || postTotalPages > 0" class="search-result-group min-w-0" data-testid="post-results">
          <h2 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
            <Search class="w-5 h-5" />
            {{ $t('common.post') }}
          </h2>
          <PostList
            show-mobile-sort
            :posts="posts"
            :current-sort="sortQuery"
            :showBoardName="true"
            :hideNoColumn="true"
            :resolve-post-route="resolvePostDetailRoute"
            :resolve-board-route="resolveBoardRoute"
            :show-inquiry-status="isInquiryPostItem"
            @update:sort="changeSort"
          />
          <Pagination
            v-if="postTotalPages > 1"
            class="mt-4"
            :aria-label="`${$t('common.post')} ${$t('common.pagination')}`"
            :current-page="postPageQuery"
            :total-pages="postTotalPages"
            :link-builder="(page) => buildSearchPageLink(page, 'post')"
          />
        </div>

        <section v-if="comments.length > 0 || commentTotalPages > 0" class="search-result-group min-w-0 space-y-3" data-testid="comment-results">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold nv-title">
            <MessageSquare class="h-5 w-5" />
            {{ $t('common.comment') }}
          </h2>
          <RouterLink
            v-for="comment in comments"
            :key="comment.commentId"
            :to="{
              name: 'post-detail',
              params: { boardUrl: comment.boardUrl, postId: comment.postId },
              hash: `#comment-${comment.commentId}`,
            }"
            class="block min-w-0 rounded-md border nv-border p-4 nv-surface nv-hover-surface"
          >
            <p class="line-clamp-2 text-sm nv-text">{{ comment.content || comment.postTitle }}</p>
            <p class="mt-2 truncate text-xs nv-text-muted">
              {{ comment.author?.withdrawn ? $t('user.deletedUser') : (comment.author?.displayName || '-') }} · {{ comment.postTitle }}
            </p>
          </RouterLink>
          <Pagination
            v-if="commentTotalPages > 1"
            class="mt-4"
            :aria-label="`${$t('common.comment')} ${$t('common.pagination')}`"
            :current-page="commentPageQuery"
            :total-pages="commentTotalPages"
            :link-builder="(page) => buildSearchPageLink(page, 'comment')"
          />
        </section>

        <section v-if="users.length > 0" class="search-result-group min-w-0 space-y-3" data-testid="user-results">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold nv-title">
            <User class="h-5 w-5" />
            {{ $t('common.user') }}
          </h2>
          <div class="grid min-w-0 grid-cols-1 gap-3 sm:grid-cols-2">
            <RouterLink
              v-for="user in users"
              :key="user.userId"
              :to="{ name: 'user-profile', params: { userId: user.userId } }"
              class="flex min-w-0 items-center gap-3 rounded-md border nv-border p-4 nv-surface nv-hover-surface"
            >
              <UserAvatar
                :image-url="user.profileImageUrl"
                :name="user.displayName"
                size-class="h-10 w-10"
              />
              <span class="min-w-0 truncate text-sm font-medium nv-title">{{ user.displayName }}</span>
            </RouterLink>
          </div>
        </section>

        <Pagination
          v-if="otherTotalPages > 1"
          :current-page="pageQuery"
          :total-pages="otherTotalPages"
          :link-builder="(page) => buildSearchPageLink(page, 'other')"
        />

        <SemanticSearchResults
          v-if="!keywordResultsEmpty && semanticResults.length > 0"
          :results="semanticResults"
          class="search-result-group"
        />
      </div>
      <ErrorState
        v-if="semanticEnabled && semanticSearchError"
        class="mt-6"
        title-tag="h3"
        :message="$t('common.messages.loadFailed')"
        show-retry
        @retry="refetchSemanticSearch"
      />
      <p v-else-if="semanticEnabled && !isSemanticLoading && semanticResults.length === 0" class="mt-6 rounded-lg border nv-border nv-surface p-4 text-sm nv-text-muted">
        {{ $t('search.semanticEmpty') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { searchApi } from '@/api/search'
import { useSearch } from '@/features/search/queries/useSearch'
import { useRecentSearchSync } from '@/features/search/queries/useRecentSearchSync'
import { searchQueryKeys } from '@/features/search/queries/searchQueryKeys'
import {
  useSearchRouteQuery,
  type SearchFilterKey,
  type SearchPageGroup,
} from '@/features/search/navigation/useSearchRouteQuery'
import BoardCard from '@/components/board/BoardCard.vue'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import SemanticSearchResults from '@/components/search/SemanticSearchResults.vue'
import SearchSidebarPanel from '@/components/search/SearchSidebarPanel.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'
import { Search, Layout, MessageSquare, User, X, ChevronDown } from 'lucide-vue-next'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import {
  isSessionGenerationCurrent,
  sessionQueryKey,
  subscribeAuthSessionBoundary,
} from '@/queryAuthScope'

const authStore = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const { t } = useI18n()
const { useIntegratedSearch, useSemanticSearch, usePopularKeywords, useRecentSearches } = useSearch()
const {
  searchInput,
  searchTypeInput,
  authorInput,
  periodInput,
  fromInput,
  toInput,
  searchQuery,
  searchTypeQuery,
  authorQuery,
  periodQuery,
  fromQuery,
  toQuery,
  pageQuery,
  postPageQuery,
  commentPageQuery,
  sortQuery,
  hasSearchQuery,
  params,
  handleSearchSubmit,
  buildQueryWithoutFilter,
  buildPageQuery,
  buildPaginationQuery,
  buildSortQuery,
  isCustomDateRangeValid,
} = useSearchRouteQuery()

const {
  data: recentKeywordData,
  isError: isRecentKeywordsError,
  refetch: refetchRecentKeywords,
} = useRecentSearches(computed(() => authStore.isAuthenticated))
const recentSearchSync = useRecentSearchSync({
  data: recentKeywordData,
  refetch: refetchRecentKeywords,
})
const recentKeywords = recentSearchSync.items
const hasRecentSearchError = computed(() => authStore.isAuthenticated
  && (isRecentKeywordsError.value || recentSearchSync.hasSyncError.value))

const {
  data: searchData,
  isLoading,
  isFetching,
  error: integratedSearchError,
  refetch: refetchIntegratedSearch,
} = useIntegratedSearch(params, recentSearchSync.beginSearch)
const hasSemanticUnsupportedFilters = computed(() => Boolean(
  params.value.searchType !== 'TITLE_CONTENT'
  || params.value.author
  || params.value.period
  || params.value.from
  || params.value.to,
))
const semanticEnabled = computed(() => hasSearchQuery.value && !hasSemanticUnsupportedFilters.value)
const semanticParams = computed(() => ({
  q: searchQuery.value,
  size: 5,
  contentType: 'ALL',
  ...(params.value.boardUrl ? { boardUrl: params.value.boardUrl } : {}),
}))
const {
  data: semanticData,
  isLoading: semanticQueryLoading,
  error: semanticSearchError,
  refetch: refetchSemanticSearch,
} = useSemanticSearch(semanticParams, semanticEnabled)
const {
  data: popularKeywordData,
  isLoading: isPopularKeywordsLoading,
  isError: isPopularKeywordsError,
  refetch: refetchPopularKeywords,
} = usePopularKeywords()
const posts = computed(() => searchData.value?.postResults || [])
const comments = computed(() => searchData.value?.commentResults || [])
const users = computed(() => searchData.value?.userResults || [])
const boards = computed(() => searchData.value?.boardResults || [])
const isSemanticLoading = computed(() => semanticEnabled.value && semanticQueryLoading.value)
const semanticResults = computed(() => semanticEnabled.value ? (semanticData.value?.content || []) : [])
const totalResultCount = computed(() => (
  (searchData.value?.postPage?.totalElements ?? posts.value.length)
  + (searchData.value?.commentPage?.totalElements ?? comments.value.length)
  + (searchData.value?.userPage?.totalElements ?? users.value.length)
  + (searchData.value?.boardPage?.totalElements ?? boards.value.length)
))
const postTotalPages = computed(() => searchData.value?.postPage?.totalPages ?? 0)
const commentTotalPages = computed(() => searchData.value?.commentPage?.totalPages ?? 0)
const otherTotalPages = computed(() => Math.max(
  searchData.value?.userPage?.totalPages ?? 0,
  searchData.value?.boardPage?.totalPages ?? 0,
))
const keywordResultsEmpty = computed(() => (
  posts.value.length === 0
  && comments.value.length === 0
  && users.value.length === 0
  && boards.value.length === 0
))
const hasAnyResults = computed(() => totalResultCount.value > 0 || semanticResults.value.length > 0)
const popularKeywords = computed(() => popularKeywordData.value || [])
const hasKeywordFilters = computed(() => Boolean(
  params.value.searchType !== 'TITLE_CONTENT' || params.value.author || params.value.period,
))
const searchTypeOptions = computed(() => [
  { value: 'TITLE_CONTENT', label: t('search.scopeTitleContent') },
  { value: 'TITLE', label: t('search.scopeTitle') },
  { value: 'CONTENT', label: t('search.scopeContent') },
  { value: 'AUTHOR', label: t('search.scopeAuthor') },
])
const periodOptions = computed(() => [
  { value: '', label: t('search.periodAll') },
  { value: 'TODAY', label: t('search.periodToday') },
  { value: 'WEEK', label: t('search.periodWeek') },
  { value: 'MONTH', label: t('search.periodMonth') },
  { value: 'CUSTOM', label: t('search.periodCustom') },
])
const hasSearchError = computed(() => Boolean(
  integratedSearchError.value,
))
const activeFilterChips = computed<Array<{ key: SearchFilterKey, label: string }>>(() => {
  const chips: Array<{ key: SearchFilterKey, label: string }> = []
  if (searchTypeQuery.value !== 'TITLE_CONTENT') {
    chips.push({
      key: 'searchType',
      label: t('search.scopeFilterChip', { value: getSearchTypeLabel(searchTypeQuery.value) }),
    })
  }
  if (authorQuery.value && searchTypeQuery.value !== 'AUTHOR') {
    chips.push({
      key: 'author',
      label: t('search.authorFilterChip', { value: authorQuery.value }),
    })
  }
  if (periodQuery.value) {
    chips.push({
      key: 'period',
      label: t('search.periodFilterChip', { value: getPeriodLabel(periodQuery.value) }),
    })
  }
  if (periodQuery.value === 'CUSTOM' && (fromQuery.value || toQuery.value)) {
    chips.push({
      key: 'dateRange',
      label: t('search.dateRangeFilterChip', {
        from: fromQuery.value || '...',
        to: toQuery.value || '...',
      }),
    })
  }
  return chips
})

function getSearchTypeLabel(searchType: string) {
  switch (searchType) {
    case 'TITLE':
      return t('search.scopeTitle')
    case 'CONTENT':
      return t('search.scopeContent')
    case 'AUTHOR':
      return t('search.scopeAuthor')
    default:
      return t('search.scopeTitleContent')
  }
}

function getPeriodLabel(period: string) {
  switch (period) {
    case 'TODAY':
      return t('search.periodToday')
    case 'WEEK':
      return t('search.periodWeek')
    case 'MONTH':
      return t('search.periodMonth')
    case 'CUSTOM':
      return t('search.periodCustom')
    default:
      return period
  }
}

function removeFilter(filterKey: SearchFilterKey) {
  router.push({ name: 'search', query: buildQueryWithoutFilter(filterKey) })
}

function searchKeyword(keyword: string) {
  const normalizedKeyword = keyword.trim()
  if (!normalizedKeyword) return

  router.push({
    name: 'search',
    query: { q: normalizedKeyword },
  })
}

const recentMutationControllers = new Set<AbortController>()

async function runRecentKeywordMutation(request: (signal: AbortSignal) => Promise<unknown>, keyword?: string) {
  const generation = authStore.sessionGeneration
  const controller = new AbortController()
  recentMutationControllers.add(controller)
  const completeDeletion = recentSearchSync.beginDeletion(keyword)

  try {
    await request(controller.signal)
    if (controller.signal.aborted
      || !isSessionGenerationCurrent(authStore, generation)) return
    completeDeletion()
    await queryClient.invalidateQueries({
      queryKey: sessionQueryKey(generation, searchQueryKeys.recent),
    })
  } catch (error) {
    if (controller.signal.aborted
      || !isSessionGenerationCurrent(authStore, generation)) return
    throw error
  } finally {
    recentMutationControllers.delete(controller)
  }
}

function buildSearchPageLink(page: number, group: SearchPageGroup) {
  return {
    name: 'search',
    query: buildPageQuery(page, group),
  }
}

function changeSort(sort: string) {
  void router.push({ name: 'search', query: buildSortQuery(sort) })
}

watch([searchData, isFetching], ([data, fetching]) => {
  if (!data || fetching || hasSearchError.value || !hasSearchQuery.value) return
  const clampPage = (page: number, totalPages: number) => Math.min(page, Math.max(totalPages - 1, 0))
  const pages = {
    page: clampPage(pageQuery.value, otherTotalPages.value),
    postPage: data.postPage ? clampPage(postPageQuery.value, postTotalPages.value) : postPageQuery.value,
    commentPage: data.commentPage ? clampPage(commentPageQuery.value, commentTotalPages.value) : commentPageQuery.value,
  }
  if (pages.page === pageQuery.value && pages.postPage === postPageQuery.value
    && pages.commentPage === commentPageQuery.value) return
  void router.replace({ name: 'search', query: buildPaginationQuery(pages) })
}, { immediate: true })

function openDatePicker(event: MouseEvent) {
  const input = event.currentTarget as HTMLInputElement
  try {
    input.showPicker?.()
  } catch {
    // Unsupported or restricted pickers retain native keyboard date entry.
  }
}

async function deleteRecentKeyword(logId: number, keyword: string) {
  await runRecentKeywordMutation((signal) => searchApi.deleteRecentSearch(logId, { signal }), keyword)
}

function retryRecentKeywords() {
  if (recentSearchSync.isReconciling.value) recentSearchSync.retry()
  else void refetchRecentKeywords()
}

async function clearRecentKeywords() {
  await runRecentKeywordMutation((signal) => searchApi.deleteAllRecentSearches({ signal }))
}

function handleDeleteRecentKeyword(logId: number, keyword: string) {
  void deleteRecentKeyword(logId, keyword).catch(() => undefined)
}

function handleClearRecentKeywords() {
  void clearRecentKeywords().catch(() => undefined)
}

const unsubscribeSessionBoundary = subscribeAuthSessionBoundary(() => {
  recentMutationControllers.forEach((controller) => controller.abort())
  recentMutationControllers.clear()
})

onScopeDispose(() => {
  recentMutationControllers.forEach((controller) => controller.abort())
  recentMutationControllers.clear()
  unsubscribeSessionBoundary()
})

async function retrySearch() {
  await refetchIntegratedSearch()
}

</script>

<style scoped>
:deep(.search-date-input) {
  color-scheme: light;
}

:global(.dark .search-page .search-date-input) {
  color-scheme: dark;
}

.search-result-group {
  --nv-line-soft: var(--nv-border-strong);
  padding: 1rem;
  border: 1px solid var(--nv-border-strong);
  border-radius: var(--nv-radius-lg);
  background: var(--nv-elevated);
  color: var(--nv-ink);
}

@media (min-width: 640px) {
  .search-result-group {
    padding: 1.5rem;
  }
}
</style>
