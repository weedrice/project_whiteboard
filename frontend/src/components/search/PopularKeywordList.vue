<template>
  <div class="card p-4">
    <h3 class="text-lg font-medium text-gray-900 mb-4">Popular Keywords</h3>
    
    <div v-if="loading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else-if="keywords.length === 0" class="text-gray-500 text-sm">
      No popular keywords yet.
    </div>
    
    <ul v-else class="space-y-2">
      <li v-for="(item, index) in keywords" :key="item.keyword" class="flex items-center justify-between">
        <router-link 
          :to="{ name: 'search', query: { keyword: item.keyword } }"
          class="text-sm text-gray-700 hover:text-indigo-600 truncate"
        >
          <span class="font-medium mr-2 text-gray-400">{{ index + 1 }}.</span>
          {{ item.keyword }}
        </router-link>
        <span class="text-xs text-gray-400">{{ item.count }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useSearch } from '@/composables/useSearch'

const { usePopularKeywords } = useSearch()
const { data: keywordsData, isLoading: loading } = usePopularKeywords()

const keywords = computed(() => keywordsData.value || [])
</script>
