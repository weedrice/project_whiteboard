<template>
  <div
    class="nv-empty-state text-center py-12"
    :class="containerClass"
    role="status"
    aria-live="polite"
  >
    <div v-if="icon" class="nv-empty-state-icon mx-auto h-12 w-12 nv-text-subtle mb-4">
      <component :is="icon" class="h-full w-full" aria-hidden="true" />
    </div>
    <component :is="titleTag" class="mt-2 text-sm font-medium nv-title">
      {{ title || $t('common.noData') }}
    </component>
    <p v-if="description" class="mt-1 text-sm nv-text-subtle">
      {{ description }}
    </p>
    <div v-if="$slots.action || actionLabel" class="mt-6">
      <slot name="action"></slot>
      <router-link
        v-if="actionLabel && actionTo"
        :to="actionTo"
        class="nv-empty-state-action"
      >
        {{ actionLabel }}
      </router-link>
      <button
        v-else-if="actionLabel"
        type="button"
        class="nv-empty-state-action"
        @click="$emit('action')"
      >
        {{ actionLabel }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

withDefaults(defineProps<{
  title?: string
  description?: string
  icon?: Component
  containerClass?: string
  actionLabel?: string
  actionTo?: RouteLocationRaw
  titleTag?: 'h1' | 'h2' | 'h3' | 'h4'
}>(), {
  titleTag: 'h3',
})

defineEmits<{
  action: []
}>()
</script>

<style scoped>
.nv-empty-state {
  color: var(--nv-ink);
}

.nv-empty-state-icon {
  border-radius: 999px;
  color: var(--nv-muted);
}

.nv-empty-state-action {
  align-items: center;
  background: var(--nv-ink);
  border-radius: 999px;
  color: var(--nv-bg);
  display: inline-flex;
  font-size: 0.875rem;
  font-weight: 700;
  justify-content: center;
  min-height: 2.75rem;
  padding: 0 1rem;
  transition: filter 0.15s ease, transform 0.15s ease;
}

.nv-empty-state-action:hover {
  filter: brightness(0.94);
}

.nv-empty-state-action:active {
  transform: translateY(1px);
}
</style>
