<script setup lang="ts">
defineProps<{
  label: string
}>()
</script>

<template>
  <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
    <div class="mb-3">
      <p class="nv-compose-kicker">{{ $t('board.writePost.sections.draftState') }}</p>
    </div>
    <p class="text-sm text-[var(--nv-ink-soft)]">{{ label }}</p>
  </section>
</template>

<style scoped>
.nv-compose-kicker {
  color: var(--nv-muted);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
}
</style>
