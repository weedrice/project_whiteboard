<template>
  <div class="mx-auto max-w-6xl">
    <div class="flex min-w-0 flex-col gap-8 md:flex-row">
      <!-- Main Content -->
      <div class="min-w-0 flex-1">
        <div class="mb-6">
          <h1 class="text-2xl font-bold nv-title">{{ $t('search.results') }}</h1>
          <p v-if="searchQuery" class="mt-2 nv-text-muted">
            {{ $t('search.query') }}: <span class="font-semibold nv-accent-text">"{{ searchQuery
            }}"</span>
          </p>

          <form class="mt-4 flex min-w-0 flex-col gap-2 sm:flex-row" role="search" @submit.prevent="handleSearchSubmit">
            <BaseInput
              id="search-page-query"
              v-model="searchInput"
              name="searchPageQuery"
              autocomplete="off"
              :label="$t('search.placeholder')"
              :placeholder="$t('search.placeholder')"
              inputClass="h-11"
              class="min-w-0 flex-1"
              hideLabel
            />
            <BaseButton
              type="submit"
              variant="secondary"
              class="h-11 shrink-0"
              :disabled="!searchInput.trim()"
            >
              {{ $t('search.doSearch', { query: searchInput.trim() }) }}
            </BaseButton>
          </form>

          <div v-if="hasSearchQuery" class="mt-4 flex flex-wrap items-center gap-2">
            <BaseButton
              type="button"
              variant="secondary"
              size="sm"
              @click="isFilterOpen = !isFilterOpen"
            >
              {{ $t('search.filterToggle') }}
            </BaseButton>
            <div
              v-if="activeFilterChips.length > 0"
              class="flex flex-wrap items-center gap-2"
              :aria-label="$t('search.activeFilters')"
            >
              <button
                v-for="chip in activeFilterChips"
                :key="chip.key"
                type="button"
                class="nv-touch-target inline-flex max-w-full items-center gap-1 rounded-full border nv-border px-3 py-1.5 text-xs font-medium nv-text nv-hover-surface"
                :aria-label="$t('search.clearFilter', { label: chip.label })"
                @click="removeFilter(chip.key)"
              >
                <span class="min-w-0 truncate">{{ chip.label }}</span>
                <span aria-hidden="true">×</span>
              </button>
            </div>
          </div>

          <form
            v-if="hasSearchQuery && isFilterOpen"
            class="mt-4 grid gap-3 rounded-lg border nv-border nv-surface p-4 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_0.9fr_0.9fr_0.9fr_auto]"
            @submit.prevent="applyFilters"
          >
            <BaseSelect
              v-model="searchTypeInput"
              name="searchType"
              :label="$t('search.scopeFilter')"
              :options="searchTypeOptions"
              input-class="h-10"
            />
            <BaseInput
              v-if="searchTypeInput !== 'AUTHOR'"
              id="search-author-filter"
              v-model="authorInput"
              name="author"
              :label="$t('search.authorFilter')"
              :placeholder="$t('search.authorFilter')"
              inputClass="h-10"
            />
            <BaseSelect
              v-model="periodInput"
              name="period"
              :label="$t('search.periodFilter')"
              :options="periodOptions"
              input-class="h-10"
            />
            <BaseInput
              v-if="periodInput === 'CUSTOM'"
              id="search-from-filter"
              v-model="fromInput"
              name="from"
              type="date"
              :label="$t('search.fromDate')"
              inputClass="h-10"
            />
            <BaseInput
              v-if="periodInput === 'CUSTOM'"
              id="search-to-filter"
              v-model="toInput"
              name="to"
              type="date"
              :label="$t('search.toDate')"
              inputClass="h-10"
            />
            <BaseButton type="submit" variant="secondary" class="h-10 self-end">
              {{ $t('search.applyFilters') }}
            </BaseButton>
          </form>
          <p v-if="hasKeywordFilters" class="mt-2 text-xs nv-text-subtle">
            {{ $t('search.keywordFilterNotice') }}
          </p>
          <p
            v-if="hasSearchQuery && !isLoading && !isSemanticLoading && !hasSearchError"
            class="mt-3 text-sm nv-text-subtle"
            role="status"
            aria-live="polite"
            aria-atomic="true"
          >
            {{ t('search.resultSummary', { count: totalResultCount }) }}
          </p>
        </div>

        <div v-if="isLoading" class="text-center py-10">
          <BaseSpinner size="lg" />
        </div>

        <EmptyState v-else-if="!hasSearchQuery" title-tag="h2" :title="$t('search.placeholder')" :icon="Search"
          container-class="nv-surface nv-elevated-surface shadow rounded-lg" />

        <ErrorState
          v-else-if="hasSearchError"
          title-tag="h2"
          :title="$t('common.messages.defaultTitle')"
          :message="$t('common.messages.loadFailed')"
          show-retry
          @retry="retrySearch"
        />

        <EmptyState v-else-if="!hasAnyResults && !isSemanticLoading" title-tag="h2" :title="$t('search.noResults')"
          :description="searchQuery ? `${$t('search.noResultsFor', { query: searchQuery })} ${$t('search.noResultsSuggestion')}` : $t('search.noResultsSuggestion')" :icon="Search"
          container-class="nv-surface nv-elevated-surface shadow rounded-lg" />

        <div v-else class="space-y-8">
          <div v-if="keywordResultsEmpty && isSemanticLoading" class="text-center py-6">
            <BaseSpinner size="md" />
          </div>

          <section
            v-if="keywordResultsEmpty && semanticResults.length > 0"
            class="min-w-0 space-y-3"
          >
            <h2 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('search.semanticRelated') }}
            </h2>
            <RouterLink
              v-for="result in semanticResults"
              :key="`${result.contentType}-${result.contentId}`"
              :to="{ name: 'post-detail', params: { boardUrl: result.boardUrl, postId: result.postId } }"
              class="block min-w-0 rounded-md border nv-border p-4 nv-surface nv-hover-surface"
            >
              <p class="truncate text-sm font-semibold nv-title">{{ result.title }}</p>
              <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ result.excerpt }}</p>
              <p class="mt-2 text-xs nv-accent-text">{{ result.boardName }}</p>
            </RouterLink>
          </section>

          <!-- Board Results -->
          <div v-if="boards.length > 0" class="min-w-0">
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
          <div v-if="posts.length > 0" class="min-w-0">
            <h2 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('common.post') }}
            </h2>
            <PostList
              :posts="posts"
              :showBoardName="true"
              :hideNoColumn="true"
              :resolve-post-route="resolvePostDetailRoute"
              :resolve-board-route="resolveBoardRoute"
              :show-inquiry-status="isInquiryPostItem"
            />
          </div>

          <section v-if="comments.length > 0" class="min-w-0 space-y-3" data-testid="comment-results">
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
              <p class="mt-2 truncate text-xs nv-text-subtle">
                {{ comment.author?.displayName || '-' }} · {{ comment.postTitle }}
              </p>
            </RouterLink>
          </section>

          <section v-if="users.length > 0" class="min-w-0 space-y-3" data-testid="user-results">
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

          <section
            v-if="!keywordResultsEmpty && semanticResults.length > 0"
            class="min-w-0 space-y-3"
          >
            <h2 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('search.semanticRelated') }}
            </h2>
            <RouterLink
              v-for="result in semanticResults"
              :key="`${result.contentType}-${result.contentId}`"
              :to="{ name: 'post-detail', params: { boardUrl: result.boardUrl, postId: result.postId } }"
              class="block min-w-0 rounded-md border nv-border p-4 nv-surface nv-hover-surface"
            >
              <p class="truncate text-sm font-semibold nv-title">{{ result.title }}</p>
              <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ result.excerpt }}</p>
              <p class="mt-2 text-xs nv-accent-text">{{ result.boardName }}</p>
            </RouterLink>
          </section>
        </div>
      </div>
      <aside class="w-full min-w-0 space-y-4 md:w-72 md:shrink-0 lg:w-80">
        <section
          class="min-w-0 overflow-hidden rounded-lg border nv-border nv-surface p-4"
          :aria-busy="isPopularKeywordsLoading"
        >
          <h2 class="text-sm font-semibold nv-title">{{ $t('search.popularKeywords') }}</h2>
          <BaseSpinner v-if="isPopularKeywordsLoading" class="mt-3" size="sm" />
          <ErrorState
            v-else-if="isPopularKeywordsError"
            class="mt-3"
            title-tag="h3"
            :message="$t('common.messages.loadFailed')"
            show-retry
            @retry="refetchPopularKeywords"
          />
          <div v-else class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="keyword in popularKeywords"
              :key="keyword.keyword"
              type="button"
              class="nv-touch-target max-w-full rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface"
              :aria-label="$t('search.searchByKeyword', { keyword: keyword.keyword })"
              @click="searchKeyword(keyword.keyword)"
            >
              <span class="block max-w-full truncate">{{ keyword.keyword }}</span>
            </button>
            <p v-if="popularKeywords.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <section
          class="min-w-0 overflow-hidden rounded-lg border nv-border nv-surface p-4"
          :aria-busy="isPopularTagsLoading"
        >
          <h2 class="text-sm font-semibold nv-title">{{ $t('common.tags') }}</h2>
          <BaseSpinner v-if="isPopularTagsLoading" class="mt-3" size="sm" />
          <ErrorState
            v-else-if="isPopularTagsError"
            class="mt-3"
            title-tag="h3"
            :message="$t('common.messages.loadFailed')"
            show-retry
            @retry="refetchPopularTags"
          />
          <div v-else class="mt-3 flex flex-wrap gap-2">
            <RouterLink
              v-for="tag in popularTags"
              :key="tag.tagId"
              :to="{ name: 'tag-posts', params: { name: tag.tagName } }"
              class="nv-touch-target inline-flex max-w-full items-center rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface no-underline"
            >
              <span class="block max-w-full truncate">#{{ tag.tagName }}</span>
            </RouterLink>
            <p v-if="popularTags.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <section
          class="min-w-0 overflow-hidden rounded-lg border nv-border nv-surface p-4"
          :aria-busy="isRecentKeywordsLoading"
        >
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-semibold nv-title">{{ $t('search.recentKeywords') }}</h2>
            <button
              v-if="recentKeywords.length > 0"
              type="button"
              class="nv-touch-target rounded-md px-1 text-xs font-medium nv-accent-text hover:underline"
              @click="handleClearRecentKeywords"
            >
              {{ $t('search.clearRecent') }}
            </button>
          </div>
          <BaseSpinner v-if="isRecentKeywordsLoading" class="mt-3" size="sm" />
          <ErrorState
            v-else-if="isRecentKeywordsError"
            class="mt-3"
            title-tag="h3"
            :message="$t('common.messages.loadFailed')"
            show-retry
            @retry="refetchRecentKeywords"
          />
          <div v-else class="mt-3 space-y-2">
            <div
              v-for="keyword in recentKeywords"
              :key="keyword.logId"
              class="flex min-h-11 min-w-0 items-center gap-2 rounded-md border nv-border px-3 py-2"
            >
              <button
                type="button"
                class="min-w-0 flex-1 truncate text-left text-sm nv-text nv-hover-accent"
                :aria-label="$t('search.searchByKeyword', { keyword: keyword.keyword })"
                @click="searchKeyword(keyword.keyword)"
              >
                {{ keyword.keyword }}
              </button>
              <button
                type="button"
                class="nv-touch-target-square -my-2 -mr-3 shrink-0 rounded-md text-xs nv-text-subtle nv-hover-danger"
                :aria-label="$t('search.deleteRecent', { keyword: keyword.keyword })"
                @click="handleDeleteRecentKeyword(keyword.logId)"
              >
                ×
              </button>
            </div>
            <p v-if="recentKeywords.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <p v-if="semanticEnabled && !isSemanticLoading && semanticResults.length === 0" class="rounded-lg border nv-border nv-surface p-4 text-sm nv-text-subtle">
          {{ $t('search.semanticEmpty') }}
        </p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { searchApi } from '@/api/search'
import { tagApi } from '@/api/tag'
import { useSearch } from '@/composables/useSearch'
import { useApiQuery } from '@/composables/useApiQuery'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { useSearchRouteQuery, type SearchFilterKey } from '@/composables/useSearchRouteQuery'
import BoardCard from '@/components/board/BoardCard.vue'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import UserAvatar from '@/components/common/ui/UserAvatar.vue'
import { Search, Layout, MessageSquare, User } from 'lucide-vue-next'
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
  hasSearchQuery,
  params,
  handleSearchSubmit,
  buildFilterQuery,
  buildQueryWithoutFilter,
} = useSearchRouteQuery()
const isFilterOpen = ref(false)

const {
  data: searchData,
  isLoading,
  error: integratedSearchError,
  refetch: refetchIntegratedSearch,
} = useIntegratedSearch(params)
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
const {
  data: popularTagData,
  isLoading: isPopularTagsLoading,
  isError: isPopularTagsError,
  refetch: refetchPopularTags,
} = useApiQuery({
  queryKey: ['tags', 'popular'],
  request: () => tagApi.getPopularTags(),
  staleTime: 300_000,
  meta: { errorMessage: false },
})
const {
  data: recentKeywordData,
  isLoading: isRecentKeywordsLoading,
  isError: isRecentKeywordsError,
  refetch: refetchRecentKeywords,
} = useRecentSearches(computed(() => authStore.isAuthenticated))
const posts = computed(() => searchData.value?.postResults || [])
const comments = computed(() => searchData.value?.commentResults || [])
const users = computed(() => searchData.value?.userResults || [])
const boards = computed(() => searchData.value?.boardResults || [])
const isSemanticLoading = computed(() => semanticEnabled.value && semanticQueryLoading.value)
const semanticResults = computed(() => semanticEnabled.value ? (semanticData.value?.content || []) : [])
const totalResultCount = computed(() => (
  posts.value.length
  + comments.value.length
  + users.value.length
  + boards.value.length
  + semanticResults.value.length
))
const keywordResultsEmpty = computed(() => (
  posts.value.length === 0
  && comments.value.length === 0
  && users.value.length === 0
  && boards.value.length === 0
))
const hasAnyResults = computed(() => !keywordResultsEmpty.value || semanticResults.value.length > 0)
const popularKeywords = computed(() => popularKeywordData.value || [])
const popularTags = computed(() => popularTagData.value?.tags || [])
const recentKeywords = computed(() => recentKeywordData.value?.content || [])
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
  integratedSearchError.value || (semanticEnabled.value && semanticSearchError.value),
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

function applyFilters() {
  const query = buildFilterQuery()
  if (!query.q) return
  router.push({ name: 'search', query })
  isFilterOpen.value = false
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

async function runRecentKeywordMutation(request: (signal: AbortSignal) => Promise<unknown>) {
  const generation = authStore.sessionGeneration
  const controller = new AbortController()
  recentMutationControllers.add(controller)

  try {
    await request(controller.signal)
    if (controller.signal.aborted
      || !isSessionGenerationCurrent(authStore, generation)) return
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

async function deleteRecentKeyword(logId: number) {
  await runRecentKeywordMutation((signal) => searchApi.deleteRecentSearch(logId, { signal }))
}

async function clearRecentKeywords() {
  await runRecentKeywordMutation((signal) => searchApi.deleteAllRecentSearches({ signal }))
}

function handleDeleteRecentKeyword(logId: number) {
  void deleteRecentKeyword(logId).catch(() => undefined)
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
  if (semanticEnabled.value) {
    await Promise.all([refetchIntegratedSearch(), refetchSemanticSearch()])
    return
  }
  await refetchIntegratedSearch()
}

</script>
