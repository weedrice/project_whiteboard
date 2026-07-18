<script setup lang="ts">
import type { MentionCandidate } from '@/types'

withDefaults(defineProps<{
  items: MentionCandidate[]
  selectedIndex: number
  id?: string
  loading?: boolean
  error?: boolean
}>(), {
  id: 'mention-suggestion-list',
})

const emit = defineEmits<{
  (event: 'select', candidate: MentionCandidate): void
  (event: 'retry'): void
}>()
</script>

<template>
  <div
    :id="id"
    class="mention-suggestion-menu"
    :role="error || loading ? undefined : 'listbox'"
    :aria-busy="loading"
  >
    <p v-if="loading" class="mention-suggestion-status" role="status" aria-live="polite">
      {{ $t('common.loading') }}
    </p>
    <div v-else-if="error" class="mention-suggestion-status" role="alert" aria-live="assertive">
      <span>{{ $t('common.messages.loadFailed') }}</span>
      <button type="button" class="mention-suggestion-retry" @click="emit('retry')">
        {{ $t('common.error.retry') }}
      </button>
    </div>
    <button
      v-for="(candidate, index) in error || loading ? [] : items"
      :key="candidate.userId"
      :id="`${id}-option-${candidate.userId}`"
      type="button"
      class="mention-suggestion-item"
      :data-active="index === selectedIndex"
      role="option"
      :aria-selected="index === selectedIndex"
      @pointerdown.prevent
      @click="emit('select', candidate)"
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

.mention-suggestion-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.5rem;
  color: var(--nv-ink-soft);
  font-size: 0.8125rem;
}

.mention-suggestion-retry {
  color: var(--nv-accent);
  font-weight: 600;
  cursor: pointer;
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
