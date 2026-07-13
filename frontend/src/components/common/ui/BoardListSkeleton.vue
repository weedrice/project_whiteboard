<template>
  <div
    class="space-y-6"
    role="status"
    aria-live="polite"
    aria-busy="true"
    :aria-label="$t('common.loading')"
  >
    <div v-if="showSubscribed" class="mb-12">
      <BaseSkeleton width="9rem" height="1.75rem" className="mb-6" />
      <div class="flex space-x-6 overflow-hidden p-2 pb-4">
        <div
          v-for="i in subscribedCount"
          :key="`subscribed-${i}`"
          class="flex min-w-[80px] flex-shrink-0 flex-col items-center"
        >
          <BaseSkeleton width="4rem" height="4rem" rounded="rounded-full" />
          <BaseSkeleton width="4.5rem" height="1rem" className="mt-3" />
        </div>
      </div>
    </div>

    <BaseSkeleton v-if="showSubscribed" width="7rem" height="1.75rem" className="mb-6" />

    <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="i in count"
        :key="i"
        class="block overflow-hidden rounded-lg border nv-border nv-surface nv-elevated-surface shadow transition-colors duration-200"
      >
        <div class="p-6">
          <div class="flex items-center">
            <BaseSkeleton width="3rem" height="3rem" rounded="rounded-md" className="flex-shrink-0" />
            <div class="ml-4 min-w-0 flex-1">
              <BaseSkeleton width="70%" height="1.75rem" />
              <div class="mt-1 flex items-center">
                <BaseSkeleton width="1rem" height="1rem" rounded="rounded-full" className="mr-1.5 flex-shrink-0" />
                <BaseSkeleton width="45%" height="1rem" />
              </div>
            </div>
          </div>

          <div class="mt-4 space-y-2">
            <BaseSkeleton width="100%" height="1rem" />
            <BaseSkeleton width="72%" height="1rem" />
          </div>

          <div class="mt-4 flex items-center justify-between border-t border-[var(--nv-line)] pt-4">
            <div class="flex items-center">
              <BaseSkeleton width="1rem" height="1rem" rounded="rounded-full" className="mr-1.5 flex-shrink-0" />
              <BaseSkeleton width="7.5rem" height="1rem" />
            </div>
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
  showSubscribed?: boolean
  subscribedCount?: number
}>(), {
  count: 6,
  showSubscribed: false,
  subscribedCount: 6
})
</script>
