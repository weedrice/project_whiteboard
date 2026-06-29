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

        <EmptyState v-else-if="posts.length === 0 && boards.length === 0" :title="$t('search.noResults')"
          :description="searchQuery ? $t('search.noResultsFor', { query: searchQuery }) : undefined" :icon="Search"
          container-class="nv-surface shadow rounded-lg" />

        <div v-else class="space-y-8">
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSearch } from '@/composables/useSearch'
import { useSearchRouteQuery } from '@/composables/useSearchRouteQuery'
import BoardCard from '@/components/board/BoardCard.vue'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import { Search, Layout } from 'lucide-vue-next'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const { useIntegratedSearch } = useSearch()
const {
  searchInput,
  searchQuery,
  hasSearchQuery,
  params,
  handleSearchSubmit,
} = useSearchRouteQuery()

const { data: searchData, isLoading } = useIntegratedSearch(params)
const posts = computed(() => searchData.value?.postResults || [])
const boards = computed(() => searchData.value?.boardResults || [])
</script>
