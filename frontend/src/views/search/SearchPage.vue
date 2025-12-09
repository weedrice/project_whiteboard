<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <div class="flex flex-col md:flex-row gap-8">
      <!-- Main Content -->
      <div class="flex-1">
        <div class="mb-6">
          <h2 class="text-2xl font-bold text-gray-900">{{ $t('search.results') }}</h2>
          <p v-if="searchQuery" class="mt-2 text-gray-600">
            {{ $t('search.query') }}: <span class="font-semibold text-indigo-600">"{{ searchQuery }}"</span>
          </p>
        </div>

        <div v-if="loading" class="text-center py-10">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
        </div>

        <div v-else-if="posts.length === 0" class="text-center py-10 bg-white shadow rounded-lg">
          <p class="text-gray-500">{{ $t('search.noResults') }}</p>
        </div>

        <div v-else class="space-y-4">
          <PostList :posts="posts" :showBoardName="true" :hideNoColumn="true" />
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

const route = useRoute()
const { useSearchPosts } = useSearch()

const query = computed(() => route.query.q)
const params = computed(() => ({
    q: query.value,
    page: 0,
    size: 20
}))

const { data: postsData, isLoading } = useSearchPosts(params)
const posts = computed(() => postsData.value?.content || [])
const searchQuery = computed(() => query.value)

</script>
