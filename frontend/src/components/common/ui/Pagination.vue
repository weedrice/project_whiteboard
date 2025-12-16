<template>
  <nav v-if="totalPages >= 1" class="flex items-center justify-center space-x-1" aria-label="Pagination">
    <BaseButton :disabled="currentPage === 0" @click="$emit('page-change', currentPage - 1)" variant="secondary">
      {{ $t('common.previous') }}
    </BaseButton>

    <template v-for="page in displayedPages" :key="page">
      <span v-if="page === '...'" class="px-3 py-2 text-gray-500 dark:text-gray-400">...</span>
      <BaseButton v-else @click="$emit('page-change', (page as number) - 1)"
        :variant="currentPage === (page as number) - 1 ? 'primary' : 'secondary'"
        :class="currentPage === (page as number) - 1 ? 'z-10' : ''">
        {{ page }}
      </BaseButton>
    </template>

    <BaseButton :disabled="currentPage === totalPages - 1" @click="$emit('page-change', currentPage + 1)"
      variant="secondary">
      {{ $t('common.next') }}
    </BaseButton>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'

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

