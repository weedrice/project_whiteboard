<script setup lang="ts">
import { Smile } from 'lucide-vue-next'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

type PickerState = 'loading' | 'empty' | 'error'

defineProps<{
  state: PickerState
  message?: string
  retryLabel?: string
  backLabel?: string
}>()

const emit = defineEmits<{
  (e: 'retry'): void
  (e: 'back'): void
}>()
</script>

<template>
  <div v-if="state === 'loading'" class="loading-state">
    <div class="h-6 w-6 flex items-center justify-center">
      <BaseSpinner size="sm" class="scale-150" />
    </div>
  </div>

  <div v-else-if="state === 'empty'" class="empty-state" role="status" aria-live="polite">
    <Smile class="w-8 h-8 nv-text-subtle mb-2" aria-hidden="true" />
    <p>{{ message }}</p>
  </div>

  <div v-else class="error-state" role="alert">
    <p>{{ message }}</p>
    <div v-if="retryLabel || backLabel" class="error-actions">
      <button v-if="retryLabel" type="button" class="retry-btn nv-focus-ring min-h-11" @click="emit('retry')">
        {{ retryLabel }}
      </button>
      <button v-if="backLabel" type="button" class="retry-btn secondary nv-focus-ring min-h-11" @click="emit('back')">
        {{ backLabel }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.loading-state {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--nv-text-subtle);
  font-size: 13px;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--nv-danger-text);
  font-size: 13px;
  text-align: center;
}

.error-actions {
  display: flex;
  gap: 8px;
}

.retry-btn {
  border-radius: 6px;
  background: var(--nv-accent);
  color: white;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
}

.retry-btn.secondary {
  background: var(--nv-surface-muted);
  color: var(--nv-text-muted);
}

.retry-btn:hover {
  background: color-mix(in srgb, var(--nv-accent) 88%, black 12%);
}

.retry-btn.secondary:hover {
  background: var(--nv-surface-hover);
  color: var(--nv-text);
}
</style>
