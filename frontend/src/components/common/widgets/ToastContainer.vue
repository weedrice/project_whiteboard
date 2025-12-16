<script setup lang="ts">
import { computed } from 'vue'
import { useToastStore } from '@/stores/toast'
import ToastMessage from './ToastMessage.vue'

const toastStore = useToastStore()

const topToasts = computed(() => toastStore.toasts.filter(t => t.position === 'top-center'))
const bottomToasts = computed(() => toastStore.toasts.filter(t => t.position === 'bottom-center'))
</script>

<template>
  <div aria-live="assertive" class="pointer-events-none fixed inset-0 z-50">
    <!-- Top Center Container -->
    <div class="absolute top-0 left-0 right-0 flex flex-col items-center space-y-4 p-6">
      <TransitionGroup enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="-translate-y-2 opacity-0" enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
        <ToastMessage v-for="toast in topToasts" :key="toast.id" :toast="toast"
          @close="toastStore.removeToast(toast.id)" />
      </TransitionGroup>
    </div>

    <!-- Bottom Center Container -->
    <div class="absolute bottom-0 left-0 right-0 flex flex-col items-center space-y-4 p-6">
      <TransitionGroup enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="translate-y-2 opacity-0" enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
        <ToastMessage v-for="toast in bottomToasts" :key="toast.id" :toast="toast"
          @close="toastStore.removeToast(toast.id)" />
      </TransitionGroup>
    </div>
  </div>
</template>
