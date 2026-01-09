<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
    <div v-for="i in count" :key="i" 
      class="bg-white dark:bg-gray-800 rounded-lg shadow p-6 flex items-center space-x-4 transition-colors duration-200">
      <BaseSkeleton width="3rem" height="3rem" rounded="rounded-full" />
      <div class="flex-1">
        <BaseSkeleton width="60%" height="24px" className="mb-2" />
        <BaseSkeleton width="90%" height="16px" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

withDefaults(defineProps<{
  count?: number
}>(), {
  count: 6
})
</script>
