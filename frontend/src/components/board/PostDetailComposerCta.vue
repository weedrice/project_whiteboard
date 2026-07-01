<script setup lang="ts">
defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'focus'): void
}>()
</script>

<template>
  <Transition name="slide-up">
    <button
      v-if="visible"
      type="button"
      class="nv-post-mobile-comment-cta sm:hidden"
      @click="emit('focus')"
    >
      {{ $t('board.postDetail.focusComposer') }}
    </button>
  </Transition>
</template>

<style scoped>
.nv-post-mobile-comment-cta {
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 9999px;
  bottom: calc(1rem + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-popup);
  color: var(--nv-ink);
  left: 50%;
  padding: 0.75rem 1rem;
  position: fixed;
  transform: translateX(-50%);
  z-index: 75;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translate(-50%, 0.4rem);
}
</style>
