<script setup lang="ts">
import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-vue-next'
import type { Component } from 'vue'
import type { Toast } from '@/stores/toast'

defineProps<{
  toast: Toast
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const icons: Record<Toast['type'], Component> = {
  success: CheckCircle,
  error: AlertCircle,
  info: Info,
  warning: AlertTriangle
}

const colors: Record<Toast['type'], string> = {
  success: 'nv-status-success',
  error: 'nv-status-danger',
  info: 'nv-status-info',
  warning: 'nv-status-warning'
}
</script>

<template>
  <div
    class="pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg border shadow-lg transition-all duration-300 ease-in-out transform hover:scale-102"
    :class="colors[toast.type] || colors.info"
    role="status"
    :aria-live="toast.type === 'error' ? 'assertive' : 'polite'"
  >
    <div class="p-4">
      <div class="flex items-start">
        <div class="flex-shrink-0">
          <component 
            :is="icons[toast.type] || icons.info" 
            class="h-6 w-6"
            aria-hidden="true" 
          />
        </div>
        <div class="ml-3 w-0 flex-1 pt-0.5">
          <p class="text-sm font-medium">
            {{ toast.message }}
          </p>
        </div>
        <div class="ml-4 flex flex-shrink-0">
          <button
            type="button"
            :aria-label="$t('common.close')"
            class="inline-flex rounded-md bg-transparent nv-text-subtle hover:text-[var(--nv-text)] nv-focus-ring"
            @click="emit('close')"
          >
            <span class="sr-only">{{ $t('common.close') }}</span>
            <X class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
