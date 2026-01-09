<template>
  <div class="space-y-4">
    <div v-for="i in count" :key="i" 
      class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4 sm:px-6 transition-colors duration-200">
      <!-- Header Skeleton -->
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center space-x-3">
          <BaseSkeleton width="2rem" height="2rem" rounded="rounded-full" />
          <div>
            <BaseSkeleton width="80px" height="14px" className="mb-1" />
            <BaseSkeleton width="60px" height="12px" />
          </div>
        </div>
        <BaseSkeleton width="60px" height="28px" />
      </div>
      <!-- Content Skeleton -->
      <div class="mb-4">
        <BaseSkeleton width="90%" height="24px" className="mb-2" />
        <BaseSkeleton width="100%" height="16px" className="mb-1" />
        <BaseSkeleton width="80%" height="16px" className="mb-4" />
        <BaseSkeleton v-if="showImage" width="100%" height="192px" rounded="rounded-lg" />
      </div>
      <!-- Footer Skeleton -->
      <div class="flex justify-around pt-3 border-t border-gray-200 dark:border-gray-700">
        <BaseSkeleton width="40px" height="20px" />
        <BaseSkeleton width="40px" height="20px" />
        <BaseSkeleton width="40px" height="20px" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'

withDefaults(defineProps<{
  count?: number
  showImage?: boolean
}>(), {
  count: 5,
  showImage: true
})
</script>
