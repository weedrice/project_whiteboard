<template>
  <div class="space-y-4">
    <div v-for="i in count" :key="i" 
      class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4 transition-colors duration-200">
      <div class="flex items-start space-x-3">
        <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" />
        <div class="flex-1">
          <div class="flex items-center justify-between mb-2">
            <BaseSkeleton width="100px" height="16px" />
            <BaseSkeleton width="60px" height="14px" />
          </div>
          <BaseSkeleton width="100%" height="16px" className="mb-1" />
          <BaseSkeleton width="80%" height="16px" className="mb-2" />
          <div class="flex items-center space-x-4 mt-2">
            <BaseSkeleton width="40px" height="16px" />
            <BaseSkeleton width="40px" height="16px" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

withDefaults(defineProps<{
  count?: number
}>(), {
  count: 5
})
</script>
