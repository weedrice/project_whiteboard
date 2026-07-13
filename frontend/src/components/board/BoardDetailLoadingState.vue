<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
</script>

<template>
  <div class="nv-board-stack">
    <section class="nv-board-panel nv-elevated-surface nv-board-header-panel p-4 sm:p-6">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div class="flex items-start gap-3">
          <BaseSkeleton width="5rem" height="5rem" rounded="rounded-[14px]" className="flex-shrink-0" />
          <div class="space-y-2.5">
            <BaseSkeleton width="220px" height="34px" />
            <div class="flex gap-3">
              <BaseSkeleton width="90px" height="18px" />
              <BaseSkeleton width="120px" height="18px" />
            </div>
            <BaseSkeleton width="320px" height="18px" />
          </div>
        </div>
        <div class="space-y-2 lg:w-32">
          <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
          <BaseSkeleton width="100%" height="34px" rounded="rounded-[10px]" />
        </div>
      </div>
    </section>

    <section class="nv-board-panel nv-elevated-surface nv-board-list-panel overflow-hidden">
      <div class="border-b border-[var(--nv-line)] px-4 py-3 sm:px-5">
        <div class="flex gap-2 overflow-hidden">
          <BaseSkeleton v-for="index in 4" :key="index" width="72px" height="34px" rounded="rounded-[10px]" />
        </div>
      </div>
      <div class="space-y-3 px-4 py-5 sm:px-5">
        <BaseSkeleton v-for="index in 5" :key="index" width="100%" height="54px" rounded="rounded-[12px]" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.nv-board-stack {
  display: grid;
  gap: 0;
}

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-header-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.nv-board-list-panel {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 0.85rem;
  }
}
</style>
