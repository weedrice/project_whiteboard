<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toast'
import ToastMessage from './ToastMessage.vue'

const toastStore = useToastStore()
const route = useRoute()

const topToasts = computed(() => toastStore.toasts.filter(t => t.position === 'top-center'))
const bottomToasts = computed(() => toastStore.toasts.filter(t => t.position === 'bottom-center'))
const usesBareLayout = computed(() => route.matched.some(record => record.meta.layout === 'BareLayout'))
const hasMobileBottomNav = computed(() => (
  !usesBareLayout.value && !route.path.startsWith('/admin')
))
</script>

<template>
  <div class="pointer-events-none fixed inset-0 z-[9999]">
    <!-- Top Center Container -->
    <div
      class="nv-toast-top-container absolute left-0 right-0 flex flex-col items-center space-y-4"
      :class="{ 'is-bare': usesBareLayout }"
    >
      <TransitionGroup enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="-translate-y-2 opacity-0" enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
        <ToastMessage v-for="toast in topToasts" :key="toast.id" :toast="toast"
          @close="toastStore.removeToast(toast.id)" />
      </TransitionGroup>
    </div>

    <!-- Bottom Center Container -->
    <div
      class="nv-toast-bottom-container absolute left-0 right-0 flex flex-col items-center space-y-4 p-6"
      :class="{ 'with-mobile-nav': hasMobileBottomNav }"
    >
      <TransitionGroup enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="translate-y-2 opacity-0" enter-to-class="translate-y-0 opacity-100"
        leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
        <ToastMessage v-for="toast in bottomToasts" :key="toast.id" :toast="toast"
          @close="toastStore.removeToast(toast.id)" />
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped>
.nv-toast-top-container {
  top: calc(4rem + env(safe-area-inset-top));
  padding: 0.75rem 1rem;
}

.nv-toast-top-container.is-bare {
  top: env(safe-area-inset-top);
}

.nv-toast-bottom-container {
  bottom: 0;
}

@media (max-width: 639px) {
  .nv-toast-bottom-container.with-mobile-nav {
    bottom: calc(var(--nv-bottom-nav-height) + env(safe-area-inset-bottom));
  }
}

@media (min-width: 640px) {
  .nv-toast-top-container {
    padding: 1rem 1.5rem;
  }
}
</style>
