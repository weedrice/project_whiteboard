<script setup lang="ts">
import { X, CheckCircle, AlertCircle, Info, AlertTriangle } from 'lucide-vue-next'
import type { Toast } from '@/stores/toast'

const props = defineProps<{
  toast: Toast
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const icons: Record<Toast['type'], any> = {
  success: CheckCircle,
  error: AlertCircle,
  info: Info,
  warning: AlertTriangle
}

const colors: Record<Toast['type'], string> = {
  success: 'bg-green-50 text-green-800 border-green-200',
  error: 'bg-red-50 text-red-800 border-red-200',
  info: 'bg-blue-50 text-blue-800 border-blue-200',
  warning: 'bg-yellow-50 text-yellow-800 border-yellow-200'
}

const iconColors: Record<Toast['type'], string> = {
  success: 'text-green-400',
  error: 'text-red-400',
  info: 'text-blue-400',
  warning: 'text-yellow-400'
}
</script>

<template>
  <div
    class="pointer-events-auto w-full max-w-sm overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-black ring-opacity-5 transition-all duration-300 ease-in-out transform hover:scale-102"
    :class="colors[toast.type] || colors.info"
  >
    <div class="p-4">
      <div class="flex items-start">
        <div class="flex-shrink-0">
          <component 
            :is="icons[toast.type] || icons.info" 
            class="h-6 w-6" 
            :class="iconColors[toast.type] || iconColors.info"
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
            class="inline-flex rounded-md bg-transparent text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
            @click="emit('close')"
          >
            <span class="sr-only">Close</span>
            <X class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
