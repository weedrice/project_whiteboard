<script setup lang="ts">
import BaseCard from '@/components/common/ui/BaseCard.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

withDefaults(defineProps<{
  title: string
  loading?: boolean
  error?: boolean
  empty?: boolean
  emptyText?: string
  errorText?: string
}>(), {
  loading: false,
  error: false,
  empty: false,
  emptyText: '',
  errorText: '',
})

defineEmits<{
  retry: []
}>()
</script>

<template>
  <BaseCard as="section" padding="sm" elevation="none" bordered :aria-busy="loading">
    <div class="flex items-center justify-between gap-2">
      <h2 class="text-sm font-semibold nv-title">{{ title }}</h2>
      <slot name="actions" />
    </div>
    <div v-if="loading" class="mt-3" role="status" aria-live="polite">
      <BaseSpinner size="sm" />
    </div>
    <ErrorState
      v-else-if="error"
      class="mt-3 py-3"
      title-tag="h3"
      :message="errorText || $t('common.messages.loadFailed')"
      :show-icon="false"
      show-retry
      @retry="$emit('retry')"
    />
    <EmptyState
      v-else-if="empty"
      class="mt-3"
      title-tag="h3"
      :title="emptyText || $t('search.noResults')"
      container-class="py-2"
    />
    <div v-else class="mt-3">
      <slot />
    </div>
  </BaseCard>
</template>
