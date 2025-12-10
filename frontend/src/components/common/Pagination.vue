<template>
  <nav v-if="totalPages >= 1" class="flex items-center justify-center space-x-1" aria-label="Pagination">
    <button
      :disabled="currentPage === 0"
      @click="$emit('page-change', currentPage - 1)"
      class="btn-secondary disabled:opacity-50 disabled:cursor-not-allowed dark:bg-gray-700 dark:text-gray-200 dark:border-gray-600 dark:hover:bg-gray-600"
    >
      {{ $t('common.previous') }}
    </button>
    
    <template v-for="page in displayedPages" :key="page">
      <span v-if="page === '...'" class="px-3 py-2 text-gray-500 dark:text-gray-400">...</span>
      <button
        v-else
        @click="$emit('page-change', (page as number) - 1)"
        :class="[
          currentPage === (page as number) - 1
            ? 'px-4 py-2 rounded-md border text-sm font-medium z-10 bg-blue-50 border-blue-500 text-blue-600 dark:bg-blue-900/50 dark:border-blue-500 dark:text-blue-300'
            : 'btn-secondary dark:bg-gray-700 dark:text-gray-200 dark:border-gray-600 dark:hover:bg-gray-600'
        ]"
      >
        {{ page }}
      </button>
    </template>

    <button
      :disabled="currentPage === totalPages - 1"
      @click="$emit('page-change', currentPage + 1)"
      class="btn-secondary disabled:opacity-50 disabled:cursor-not-allowed dark:bg-gray-700 dark:text-gray-200 dark:border-gray-600 dark:hover:bg-gray-600"
    >
      {{ $t('common.next') }}
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  currentPage: number
  totalPages: number
}>()

defineEmits<{
  (e: 'page-change', page: number): void
}>()

const displayedPages = computed(() => {
  const delta = 2
  const range: number[] = []
  const rangeWithDots: (number | string)[] = []
  let l: number | undefined

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
