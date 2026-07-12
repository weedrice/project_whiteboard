<script setup lang="ts">
import { Plus, X } from 'lucide-vue-next'

withDefaults(defineProps<{
  src: string
  alt: string
  actionLabel: string
  actionTitle?: string
  action: 'delete' | 'cancel'
  variant?: 'default' | 'new'
  muted?: boolean
}>(), {
  actionTitle: undefined,
  variant: 'default',
  muted: false,
})

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="relative min-w-0" :class="{ 'opacity-40': muted }">
    <img
      :src="src"
      :alt="alt"
      loading="lazy"
      decoding="async"
      class="aspect-square w-full object-contain rounded"
      :class="variant === 'new'
        ? 'bg-[var(--nv-success-bg)] border-2 border-[var(--nv-success-border)]'
        : 'nv-surface-muted'"
    />
    <button
      type="button"
      :aria-label="actionLabel"
      :title="actionTitle ?? actionLabel"
      class="absolute -right-3 -top-3 flex h-11 w-11 items-center justify-center rounded-full text-xs"
      :class="action === 'delete'
        ? 'bg-[var(--nv-danger)] text-white hover:brightness-95'
        : 'bg-[var(--nv-surface-muted)] text-[var(--nv-text-muted)] hover:bg-[var(--nv-surface-hover)]'"
      @click="emit('action')"
    >
      <X v-if="action === 'delete'" class="h-4 w-4" />
      <Plus v-else class="h-4 w-4" />
    </button>
  </div>
</template>
