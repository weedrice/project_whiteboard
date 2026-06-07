<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import BaseButton from '@/components/common/ui/BaseButton.vue'

withDefaults(defineProps<{
  searchLabel?: string
  resetLabel?: string
  searchClass?: string
  resetClass?: string
  gapClass?: string
}>(), {
  searchLabel: '검색',
  resetLabel: '초기화',
  searchClass: 'btn-search',
  resetClass: 'btn-reset',
  gapClass: 'gap-2',
})

const emit = defineEmits<{
  search: []
  reset: []
}>()
</script>

<template>
  <div :class="['filter-item filter-item--actions flex items-center', gapClass]">
    <BaseButton type="button" variant="primary" size="sm" :class="searchClass" @click="emit('search')">
      <Search class="mr-1 h-4 w-4" aria-hidden="true" />
      {{ searchLabel }}
    </BaseButton>
    <BaseButton type="button" variant="secondary" size="sm" :class="resetClass" @click="emit('reset')">
      {{ resetLabel }}
    </BaseButton>
  </div>
</template>
