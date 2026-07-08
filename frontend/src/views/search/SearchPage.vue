<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex flex-col md:flex-row gap-8">
      <!-- Main Content -->
      <div class="flex-1">
        <div class="mb-6">
          <h1 class="text-2xl font-bold nv-title">{{ $t('search.results') }}</h1>
          <p v-if="searchQuery" class="mt-2 nv-text-muted">
            {{ $t('search.query') }}: <span class="font-semibold nv-accent-text">"{{ searchQuery
            }}"</span>
          </p>

          <form class="mt-4 flex flex-col gap-2 sm:flex-row" role="search" @submit.prevent="handleSearchSubmit">
            <BaseInput
              id="search-page-query"
              v-model="searchInput"
              name="searchPageQuery"
              autocomplete="off"
              :label="$t('search.placeholder')"
              :placeholder="$t('search.placeholder')"
              inputClass="h-11"
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
        </div>

        <div v-if="isLoading" class="text-center py-10">
          <BaseSpinner size="lg" />
        </div>

        <EmptyState v-else-if="!hasSearchQuery" :title="$t('search.placeholder')" :icon="Search"
          container-class="nv-surface shadow rounded-lg" />

        <EmptyState v-else-if="!hasAnyResults && !isSemanticLoading" :title="$t('search.noResults')"
          :description="searchQuery ? `${$t('search.noResultsFor', { query: searchQuery })} ${$t('search.noResultsSuggestion')}` : $t('search.noResultsSuggestion')" :icon="Search"
          container-class="nv-surface shadow rounded-lg" />

        <div v-else class="space-y-8">
          <div v-if="keywordResultsEmpty && isSemanticLoading" class="text-center py-6">
            <BaseSpinner size="md" />
          </div>

          <section
            v-if="keywordResultsEmpty && semanticResults.length > 0"
            class="space-y-3"
          >
            <h3 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('search.semanticRelated') }}
            </h3>
            <RouterLink
              v-for="result in semanticResults"
              :key="`${result.contentType}-${result.contentId}`"
              :to="{ name: 'post-detail', params: { boardUrl: result.boardUrl, postId: result.postId } }"
              class="block rounded-md border nv-border p-4 nv-surface nv-hover-surface"
            >
              <p class="truncate text-sm font-semibold nv-title">{{ result.title }}</p>
              <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ result.excerpt }}</p>
              <p class="mt-2 text-xs nv-accent-text">{{ result.boardName }}</p>
            </RouterLink>
          </section>

          <!-- Board Results -->
          <div v-if="boards.length > 0">
            <h3 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Layout class="w-5 h-5" />
              {{ $t('common.board') }}
            </h3>
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <BoardCard
                v-for="board in boards"
                :key="board.boardId"
                :board="board"
                variant="compact"
              />
            </div>
          </div>

          <!-- Post Results -->
          <div v-if="posts.length > 0">
            <h3 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('common.post') }}
            </h3>
            <PostList
              :posts="posts"
              :showBoardName="true"
              :hideNoColumn="true"
              :resolve-post-route="resolvePostDetailRoute"
              :resolve-board-route="resolveBoardRoute"
              :show-inquiry-status="isInquiryPostItem"
            />
          </div>

          <section
            v-if="!keywordResultsEmpty && semanticResults.length > 0"
            class="space-y-3"
          >
            <h3 class="text-lg font-semibold nv-title mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('search.semanticRelated') }}
            </h3>
            <RouterLink
              v-for="result in semanticResults"
              :key="`${result.contentType}-${result.contentId}`"
              :to="{ name: 'post-detail', params: { boardUrl: result.boardUrl, postId: result.postId } }"
              class="block rounded-md border nv-border p-4 nv-surface nv-hover-surface"
            >
              <p class="truncate text-sm font-semibold nv-title">{{ result.title }}</p>
              <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ result.excerpt }}</p>
              <p class="mt-2 text-xs nv-accent-text">{{ result.boardName }}</p>
            </RouterLink>
          </section>
        </div>
      </div>
      <aside class="w-full md:w-72 lg:w-80 space-y-4">
        <section class="rounded-lg border nv-border nv-surface p-4">
          <h2 class="text-sm font-semibold nv-title">{{ $t('search.popularKeywords') }}</h2>
          <div class="mt-3 flex flex-wrap gap-2">
            <button
              v-for="keyword in popularKeywords"
              :key="keyword.keyword"
              type="button"
              class="rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface"
              :aria-label="$t('search.searchByKeyword', { keyword: keyword.keyword })"
              @click="searchKeyword(keyword.keyword)"
            >
              {{ keyword.keyword }}
            </button>
            <p v-if="popularKeywords.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <section class="rounded-lg border nv-border nv-surface p-4">
          <h2 class="text-sm font-semibold nv-title">{{ $t('common.tags') }}</h2>
          <div class="mt-3 flex flex-wrap gap-2">
            <RouterLink
              v-for="tag in popularTags"
              :key="tag.tagId"
              :to="{ name: 'tag-posts', params: { name: tag.tagName } }"
              class="rounded-full border nv-border px-3 py-1.5 text-sm nv-text nv-hover-surface no-underline"
            >
              #{{ tag.tagName }}
            </RouterLink>
            <p v-if="popularTags.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <section class="rounded-lg border nv-border nv-surface p-4">
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-semibold nv-title">{{ $t('search.recentKeywords') }}</h2>
            <button
              v-if="recentKeywords.length > 0"
              type="button"
              class="text-xs font-medium nv-accent-text hover:underline"
              @click="clearRecentKeywords"
            >
              {{ $t('search.clearRecent') }}
            </button>
          </div>
          <div class="mt-3 space-y-2">
            <div
              v-for="keyword in recentKeywords"
              :key="keyword.logId"
              class="flex items-center gap-2 rounded-md border nv-border px-3 py-2"
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
                class="shrink-0 text-xs nv-text-subtle nv-hover-danger"
                :aria-label="$t('search.deleteRecent')"
                @click="deleteRecentKeyword(keyword.logId)"
              >
                ×
              </button>
            </div>
            <p v-if="recentKeywords.length === 0" class="text-sm nv-text-subtle">
              {{ $t('search.noResults') }}
            </p>
          </div>
        </section>

        <p v-if="hasSearchQuery && !isSemanticLoading && semanticResults.length === 0" class="rounded-lg border nv-border nv-surface p-4 text-sm nv-text-subtle">
          {{ $t('search.semanticEmpty') }}
        </p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { searchApi } from '@/api/search'
import { tagApi } from '@/api/tag'
import { useSearch } from '@/composables/useSearch'
import { useApiQuery } from '@/composables/useApiQuery'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { useSearchRouteQuery } from '@/composables/useSearchRouteQuery'
import BoardCard from '@/components/board/BoardCard.vue'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { Search, Layout } from 'lucide-vue-next'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const authStore = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const { useIntegratedSearch, useSemanticSearch, usePopularKeywords, useRecentSearches } = useSearch()
const {
  searchInput,
  searchQuery,
  hasSearchQuery,
  params,
  handleSearchSubmit,
} = useSearchRouteQuery()

const { data: searchData, isLoading } = useIntegratedSearch(params)
const semanticParams = computed(() => ({ ...params.value, size: 5, contentType: 'ALL' }))
const { data: semanticData, isLoading: isSemanticLoading } = useSemanticSearch(semanticParams)
const { data: popularKeywordData } = usePopularKeywords()
const { data: popularTagData } = useApiQuery({
  queryKey: ['tags', 'popular'],
  request: () => tagApi.getPopularTags(),
  staleTime: 300_000,
})
const { data: recentKeywordData } = useRecentSearches(computed(() => authStore.isAuthenticated))
const posts = computed(() => searchData.value?.postResults || [])
const boards = computed(() => searchData.value?.boardResults || [])
const semanticResults = computed(() => semanticData.value?.content || [])
const keywordResultsEmpty = computed(() => posts.value.length === 0 && boards.value.length === 0)
const hasAnyResults = computed(() => !keywordResultsEmpty.value || semanticResults.value.length > 0)
const popularKeywords = computed(() => popularKeywordData.value || [])
const popularTags = computed(() => popularTagData.value?.tags || [])
const recentKeywords = computed(() => recentKeywordData.value?.content || [])

function searchKeyword(keyword: string) {
  const normalizedKeyword = keyword.trim()
  if (!normalizedKeyword) return

  router.push({
    name: 'search',
    query: { q: normalizedKeyword },
  })
}

async function deleteRecentKeyword(logId: number) {
  await searchApi.deleteRecentSearch(logId)
  await queryClient.invalidateQueries({ queryKey: searchQueryKeys.recent })
}

async function clearRecentKeywords() {
  await searchApi.deleteAllRecentSearches()
  await queryClient.invalidateQueries({ queryKey: searchQueryKeys.recent })
}

</script>
