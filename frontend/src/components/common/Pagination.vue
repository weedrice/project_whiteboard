<template>
  <nav v-if="totalPages > 1" class="flex items-center justify-center space-x-1" aria-label="Pagination">
    <button
      :disabled="currentPage === 0"
      @click="$emit('page-change', currentPage - 1)"
      class="px-3 py-2 rounded-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      Previous
    </button>
    
    <template v-for="page in displayedPages" :key="page">
      <span v-if="page === '...'" class="px-3 py-2 text-gray-500">...</span>
      <button
        v-else
        @click="$emit('page-change', page - 1)"
        :class="[
          'px-3 py-2 rounded-md border text-sm font-medium',
          currentPage === page - 1
            ? 'z-10 bg-blue-50 border-blue-500 text-blue-600'
            : 'bg-white border-gray-300 text-gray-500 hover:bg-gray-50'
        ]"
      >
        {{ page }}
      </button>
    </template>

    <button
      :disabled="currentPage === totalPages - 1"
      @click="$emit('page-change', currentPage + 1)"
      class="px-3 py-2 rounded-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
    >
      Next
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  }
})

defineEmits(['page-change'])

const displayedPages = computed(() => {
  const delta = 2
  const range = []
  const rangeWithDots = []
  let l

  for (let i = 1; i <= props.totalPages; i++) {
    if (i === 1 || i === props.totalPages || (i >= props.currentPage + 1 - delta && i <= props.currentPage + 1 + delta)) {
      range.push(i)
    }
  }

  for (let i of range) {
    if (l) {
      if (i - l === 2) {
        rangeWithDots.push(l + 1)
      } else if (i - l !== 1) {
        rangeWithDots.push('...')
      }
    }
    rangeWithDots.push(i)
    l = i
  }

  return rangeWithDots
})
</script>
