<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  value: number | string
  tone?: 'default' | 'warning' | 'success'
}>(), {
  tone: 'default',
})

const toneClass = computed(() => {
  if (props.tone === 'warning') return 'admin-metric-card--warning'
  if (props.tone === 'success') return 'admin-metric-card--success'
  return ''
})
</script>

<template>
  <div class="admin-metric-card" :class="toneClass">
    <div class="admin-metric-card__body">
      <div v-if="$slots.icon" class="admin-metric-card__icon" aria-hidden="true">
        <slot name="icon" />
      </div>
      <div class="admin-metric-card__content">
        <div class="admin-metric-card__label">{{ label }}</div>
        <div class="admin-metric-card__value">{{ value }}</div>
      </div>
    </div>
    <div v-if="$slots.footer" class="admin-metric-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped>
.admin-metric-card {
  border: 1px solid var(--nv-border);
  border-radius: 8px;
  background: var(--nv-surface);
  box-shadow: var(--nv-shadow-card);
  overflow: hidden;
}

.admin-metric-card--warning {
  border-left: 4px solid var(--nv-warning);
}

.admin-metric-card--success {
  border-left: 4px solid var(--nv-success);
}

.admin-metric-card__body {
  align-items: center;
  display: flex;
  gap: 16px;
  padding: 16px 20px;
}

.admin-metric-card__icon {
  color: var(--nv-text-subtle);
  flex-shrink: 0;
}

.admin-metric-card__content {
  min-width: 0;
}

.admin-metric-card__label {
  color: var(--nv-text-subtle);
  font-size: 0.75rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.admin-metric-card__value {
  margin-top: 4px;
  color: var(--nv-text);
  font-size: 1.5rem;
  font-weight: 700;
}

.admin-metric-card__footer {
  background: var(--nv-surface-muted);
  border-top: 1px solid var(--nv-border);
  padding: 10px 20px;
}
</style>
