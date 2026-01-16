<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex flex-col md:flex-row gap-8">
      <!-- Main Content -->
      <div class="flex-1">
        <div class="mb-6">
          <h2 class="text-2xl font-bold text-gray-900 dark:text-white">{{ $t('search.results') }}</h2>
          <p v-if="searchQuery" class="mt-2 text-gray-600 dark:text-gray-400">
            {{ $t('search.query') }}: <span class="font-semibold text-indigo-600 dark:text-indigo-400">"{{ searchQuery
            }}"</span>
          </p>
        </div>

        <div v-if="isLoading" class="text-center py-10">
          <BaseSpinner size="lg" />
        </div>

        <EmptyState v-else-if="posts.length === 0 && boards.length === 0" :title="$t('search.noResults')"
          :description="searchQuery ? $t('search.noResultsFor', { query: searchQuery }) : undefined" :icon="Search"
          container-class="bg-white dark:bg-gray-800 shadow rounded-lg" />

        <div v-else class="space-y-8">
          <!-- Board Results -->
          <div v-if="boards.length > 0">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
              <Layout class="w-5 h-5" />
              {{ $t('common.board') }}
            </h3>
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <router-link v-for="board in boards" :key="board.boardId" :to="`/board/${board.boardUrl}`"
                class="block p-4 bg-white dark:bg-gray-800 rounded-lg shadow hover:shadow-md transition-shadow border border-gray-200 dark:border-gray-700 flex items-center gap-3">
                <div
                  class="flex-shrink-0 h-10 w-10 rounded bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold overflow-hidden border border-gray-200">
                  <img v-if="board.iconUrl" :src="getOptimizedBoardIconUrl(board.iconUrl)"
                    class="h-full w-full object-contain bg-white" alt="" @error="handleImageError($event)" />
                  <span v-else class="text-sm">{{ board.boardName.substring(0, 1) }}</span>
                </div>
                <div class="flex-1 min-w-0">
                  <h4 class="font-medium text-gray-900 dark:text-white truncate">{{ board.boardName }}</h4>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">{{ board.description }}</p>
                </div>
              </router-link>
            </div>
          </div>

          <!-- Post Results -->
          <div v-if="posts.length > 0">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
              <Search class="w-5 h-5" />
              {{ $t('common.post') }}
            </h3>
            <PostList :posts="posts" :showBoardName="true" :hideNoColumn="true" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSearch } from '@/composables/useSearch'
import PostList from '@/components/board/PostList.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { Search, Layout } from 'lucide-vue-next'
import { getOptimizedBoardIconUrl, handleImageError } from '@/utils/image'

const route = useRoute()
const { useIntegratedSearch } = useSearch()

const query = computed(() => route.query.q)
const params = computed(() => ({
  q: query.value,
  page: 0,
  size: 20,
  t: route.query.t // Include timestamp to force refetch
}))

const { data: searchData, isLoading } = useIntegratedSearch(params)
const posts = computed(() => searchData.value?.posts?.content || [])
const boards = computed(() => searchData.value?.boards || [])
const searchQuery = computed(() => query.value)


</script>
