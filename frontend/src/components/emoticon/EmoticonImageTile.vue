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
      class="aspect-square w-full object-contain rounded"
      :class="variant === 'new'
        ? 'bg-[var(--nv-success-bg)] border-2 border-[var(--nv-success-border)]'
        : 'nv-surface-muted'"
    />
    <button
      type="button"
      :aria-label="actionLabel"
      :title="actionTitle ?? actionLabel"
      class="absolute -top-1 -right-1 w-5 h-5 rounded-full flex items-center justify-center text-xs"
      :class="action === 'delete'
        ? 'bg-[var(--nv-danger)] text-white hover:brightness-95'
        : 'bg-[var(--nv-surface-muted)] text-[var(--nv-text-muted)] hover:bg-[var(--nv-surface-hover)]'"
      @click="emit('action')"
    >
      <X v-if="action === 'delete'" class="w-3 h-3" />
      <Plus v-else class="w-3 h-3" />
    </button>
  </div>
</template>
