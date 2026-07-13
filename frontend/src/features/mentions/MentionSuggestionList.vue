<script setup lang="ts">
import type { MentionCandidate } from '@/types'

defineProps<{
  items: MentionCandidate[]
  selectedIndex: number
}>()

const emit = defineEmits<{
  (event: 'select', candidate: MentionCandidate): void
}>()
</script>

<template>
  <div class="mention-suggestion-menu" role="listbox">
    <button
      v-for="(candidate, index) in items"
      :key="candidate.userId"
      type="button"
      class="mention-suggestion-item"
      :data-active="index === selectedIndex"
      role="option"
      :aria-selected="index === selectedIndex"
      @mousedown.prevent="emit('select', candidate)"
    >
      <img
        v-if="candidate.profileImageUrl"
        :src="candidate.profileImageUrl"
        alt=""
        decoding="async"
        class="mention-suggestion-avatar"
      />
      <span v-else class="mention-suggestion-avatar mention-suggestion-avatar--empty">
        {{ candidate.displayName.slice(0, 1) }}
      </span>
      <span>{{ candidate.displayName }}</span>
    </button>
  </div>
</template>

<style scoped>
.mention-suggestion-menu {
  position: static;
  z-index: 20;
  max-height: 14rem;
  min-width: 12rem;
  overflow-y: auto;
  border: 1px solid var(--nv-line);
  border-radius: 0.5rem;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-lg, 0 12px 28px color-mix(in srgb, var(--nv-ink) 16%, transparent));
  padding: 0.25rem;
}

.mention-suggestion-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.5rem;
  border-radius: 0.375rem;
  padding: 0.45rem 0.5rem;
  color: var(--nv-ink);
  text-align: left;
  font-size: 0.875rem;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.mention-suggestion-item:hover,
.mention-suggestion-item[data-active="true"] {
  background: var(--nv-surface-hover, var(--nv-surface-2));
  color: var(--nv-accent);
}

.mention-suggestion-avatar {
  width: 1.5rem;
  height: 1.5rem;
  flex: 0 0 auto;
  border-radius: 9999px;
  object-fit: cover;
}

.mention-suggestion-avatar--empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--nv-surface-hover, var(--nv-surface-2));
  color: var(--nv-ink-soft);
  font-size: 0.75rem;
  font-weight: 700;
}
</style>
