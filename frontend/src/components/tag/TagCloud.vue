<template>
  <div class="bg-white shadow rounded-lg p-4">
    <h3 class="text-lg font-medium text-gray-900 mb-4">Popular Tags</h3>
    
    <div v-if="loading" class="text-center py-4">
      <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600 mx-auto"></div>
    </div>
    
    <div v-else-if="tags.length === 0" class="text-gray-500 text-sm">
      No tags found.
    </div>
    
    <div v-else class="flex flex-wrap gap-2">
      <router-link
        v-for="tag in tags"
        :key="tag.name"
        :to="{ name: 'search', query: { tag: tag.name } }"
        class="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-gray-100 text-gray-800 hover:bg-gray-200 transition-colors"
        :style="{ fontSize: calculateFontSize(tag.count) }"
      >
        #{{ tag.name }}
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import logger from '@/utils/logger'
import type { Tag } from '@/types'
import { tagApi } from '@/api/tag'

const tags = ref<Tag[]>([])
const loading = ref(false)

// Mock data as fallback when backend API is not ready or empty
const mockTags: Tag[] = [
  { name: 'Vue', count: 10 },
  { name: 'Vite', count: 8 },
  { name: 'JavaScript', count: 15 },
  { name: 'Frontend', count: 12 },
  { name: 'Backend', count: 5 }
]

const fetchTags = async () => {
  loading.value = true
  try {
    const { data } = await tagApi.getTags()
    if (data.success && data.data && data.data.length > 0) {
      tags.value = data.data
    } else {
      // Fallback to mock data if API returns empty result
      tags.value = mockTags
    }
  } catch (error) {
    logger.error('Failed to fetch tags:', error)
  } finally {
    loading.value = false
  }
}

const calculateFontSize = (count: number) => {
  const minSize = 0.875 // 14px (text-sm)
  const maxSize = 1.25 // 20px (text-xl)
  
  if (tags.value.length === 0) return `${minSize}rem`

  const maxCount = Math.max(...tags.value.map((t: Tag) => t.count))
  const minCount = Math.min(...tags.value.map((t: Tag) => t.count))
  
  if (maxCount === minCount) return `${minSize}rem`
  
  const size = minSize + (count - minCount) * (maxSize - minSize) / (maxCount - minCount)
  return `${size}rem`
}

onMounted(() => {
  fetchTags()
})
</script>
