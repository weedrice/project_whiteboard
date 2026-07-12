<script setup lang="ts">
defineProps<{
  code?: string
  title: string
  message: string
  notice?: string
}>()
</script>

<template>
  <section class="nv-error-state" role="alert" aria-live="polite">
    <p v-if="code" class="nv-kicker">{{ code }}</p>
    <h1 class="mt-3 text-3xl font-bold nv-title">{{ title }}</h1>
    <p class="mt-3 text-sm leading-6 nv-text-muted">{{ message }}</p>
    <p v-if="notice" class="mt-3 text-xs nv-text-subtle">{{ notice }}</p>
    <div v-if="$slots.default" class="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
      <slot />
    </div>
    <slot name="details" />
  </section>
</template>

<style scoped>
.nv-error-state {
  margin: 0 auto;
  max-width: 32rem;
  text-align: center;
  width: 100%;
}
</style>
