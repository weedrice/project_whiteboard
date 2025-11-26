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

<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api'

const tags = ref([])
const loading = ref(false)

// Mock data for now as backend API might not be ready or empty
// In real implementation, remove mock and rely on API
const mockTags = [
  { name: 'Vue', count: 10 },
  { name: 'Vite', count: 8 },
  { name: 'JavaScript', count: 15 },
  { name: 'Frontend', count: 12 },
  { name: 'Backend', count: 5 }
]

const fetchTags = async () => {
  loading.value = true
  try {
    // const { data } = await axios.get('/api/tags')
    // if (data.success) {
    //   tags.value = data.data
    // }
    
    // Using mock data for demonstration until API is confirmed
    await new Promise(resolve => setTimeout(resolve, 500))
    tags.value = mockTags
  } catch (error) {
    console.error('Failed to fetch tags:', error)
  } finally {
    loading.value = false
  }
}

const calculateFontSize = (count) => {
  const minSize = 0.875 // 14px (text-sm)
  const maxSize = 1.25 // 20px (text-xl)
  const maxCount = Math.max(...tags.value.map(t => t.count))
  const minCount = Math.min(...tags.value.map(t => t.count))
  
  if (maxCount === minCount) return `${minSize}rem`
  
  const size = minSize + (count - minCount) * (maxSize - minSize) / (maxCount - minCount)
  return `${size}rem`
}

onMounted(() => {
  fetchTags()
})
</script>
