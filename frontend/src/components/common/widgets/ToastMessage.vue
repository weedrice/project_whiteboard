<script setup lang="ts">
import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-vue-next'
import { ref, type Component } from 'vue'
import type { Toast } from '@/stores/toast'

defineProps<{
  toast: Toast
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'pause'): void
  (e: 'resume'): void
}>()

const toastRef = ref<HTMLElement | null>(null)
const isHovered = ref(false)
const hasFocusWithin = ref(false)

const handleMouseEnter = () => {
  if (!isHovered.value && !hasFocusWithin.value) emit('pause')
  isHovered.value = true
}

const handleMouseLeave = () => {
  isHovered.value = false
  if (!hasFocusWithin.value) emit('resume')
}

const handleFocusIn = () => {
  if (!isHovered.value && !hasFocusWithin.value) emit('pause')
  hasFocusWithin.value = true
}

const handleFocusOut = (event: FocusEvent) => {
  if (event.relatedTarget instanceof Node && toastRef.value?.contains(event.relatedTarget)) return
  hasFocusWithin.value = false
  if (!isHovered.value) emit('resume')
}

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
    ref="toastRef"
    class="pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg border shadow-lg transition-all duration-300 ease-in-out transform hover:scale-102"
    :class="colors[toast.type] || colors.info"
    :role="toast.type === 'error' ? 'alert' : 'status'"
    :aria-live="toast.type === 'error' ? undefined : 'polite'"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
    @focusin="handleFocusIn"
    @focusout="handleFocusOut"
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
            class="-m-3 inline-flex min-h-11 min-w-11 items-center justify-center rounded-md bg-transparent nv-text-subtle hover:text-[var(--nv-ink)] nv-focus-ring"
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
