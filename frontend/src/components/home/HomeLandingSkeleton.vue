<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
</script>

<template>
  <div
    class="space-y-8 pb-8"
    role="status"
    aria-live="polite"
    aria-busy="true"
    :aria-label="$t('common.loading')"
  >
    <section class="nv-home-hero nv-elevated-surface">
      <div class="flex items-center justify-between gap-4">
        <div class="nv-home-live-rollup">
          <BaseSkeleton width="0.875rem" height="0.875rem" rounded="rounded-full" className="flex-shrink-0" />
          <BaseSkeleton width="8.5rem" height="0.75rem" />
          <BaseSkeleton width="4.5rem" height="0.75rem" />
        </div>
        <BaseSkeleton width="8rem" height="0.75rem" className="hidden sm:block" />
      </div>

      <div class="mt-5 grid gap-5 lg:grid-cols-[1.45fr_0.95fr]">
        <article class="nv-home-card nv-elevated-surface nv-home-card-featured">
          <div class="nv-home-card-top">
            <div class="flex min-w-0 items-center gap-3">
              <BaseSkeleton width="2.25rem" height="2.25rem" rounded="rounded-full" className="flex-shrink-0" />
              <div class="min-w-0 space-y-2">
                <BaseSkeleton width="8rem" height="1rem" />
                <BaseSkeleton width="4.75rem" height="0.75rem" />
              </div>
            </div>
            <BaseSkeleton width="5rem" height="1.5rem" rounded="rounded-full" />
          </div>
          <BaseSkeleton width="100%" height="min(26rem, 52vw)" rounded="rounded-[14px]" />
          <div class="space-y-3">
            <BaseSkeleton width="82%" height="1.75rem" />
            <BaseSkeleton width="100%" height="1rem" />
            <BaseSkeleton width="68%" height="1rem" />
          </div>
          <div class="flex items-center gap-2">
            <BaseSkeleton width="7rem" height="1rem" />
            <BaseSkeleton width="4rem" height="1rem" />
            <BaseSkeleton width="4.5rem" height="1rem" className="ml-auto" />
          </div>
        </article>

        <div class="space-y-3">
          <div class="flex items-center gap-2">
            <BaseSkeleton width="1rem" height="1rem" rounded="rounded-full" />
            <BaseSkeleton width="7.5rem" height="0.75rem" />
          </div>
          <div class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)] shadow-[var(--nv-shadow-soft)]">
            <div class="grid grid-cols-2 gap-px">
              <article
                v-for="index in 4"
                :key="index"
                class="bg-[var(--nv-surface)] px-4 py-4"
              >
                <BaseSkeleton width="72%" height="0.75rem" />
                <BaseSkeleton width="58%" height="2rem" className="mt-3" />
                <BaseSkeleton width="86%" height="0.875rem" className="mt-2" />
              </article>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="space-y-4">
      <div class="flex items-center justify-between gap-3">
        <BaseSkeleton width="7rem" height="1.75rem" />
        <BaseSkeleton width="4.5rem" height="1rem" />
      </div>
      <div class="overflow-hidden rounded-[16px] border border-[var(--nv-line)] bg-[var(--nv-line)] shadow-[var(--nv-shadow-soft)]">
        <div class="grid grid-cols-2 gap-px xl:grid-cols-7">
          <div
            v-for="index in 7"
            :key="index"
            class="min-h-[68px] bg-[var(--nv-surface)] px-3.5 pb-2 pt-3"
          >
            <BaseSkeleton width="78%" height="1rem" />
            <BaseSkeleton width="3rem" height="0.75rem" className="mt-2" />
          </div>
        </div>
      </div>
    </section>

    <section class="space-y-4">
      <div class="flex items-center justify-between gap-3">
        <BaseSkeleton width="9rem" height="1.75rem" />
        <div class="flex items-center gap-1 rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface)] p-1">
          <BaseSkeleton v-for="index in 3" :key="index" width="4rem" height="1.75rem" rounded="rounded-full" />
        </div>
      </div>

      <div class="grid gap-4 lg:grid-cols-2">
        <article
          v-for="index in 2"
          :key="index"
          class="nv-home-card nv-elevated-surface nv-home-card-trending nv-home-card-has-media"
        >
          <div class="nv-home-card-top">
            <div class="flex min-w-0 items-center gap-3">
              <BaseSkeleton width="2.25rem" height="2.25rem" rounded="rounded-full" />
              <div class="space-y-2">
                <BaseSkeleton width="8rem" height="1rem" />
                <div class="flex gap-2">
                  <BaseSkeleton width="2.5rem" height="0.75rem" />
                  <BaseSkeleton width="2.5rem" height="0.75rem" />
                </div>
              </div>
            </div>
            <BaseSkeleton width="4.5rem" height="1.5rem" rounded="rounded-full" />
          </div>
          <div class="nv-home-media">
            <BaseSkeleton width="100%" height="100%" rounded="rounded-[14px]" />
          </div>
          <div class="nv-home-card-content space-y-3">
            <BaseSkeleton width="80%" height="1.5rem" />
            <BaseSkeleton width="100%" height="1rem" />
            <BaseSkeleton width="62%" height="1rem" />
          </div>
        </article>
      </div>
    </section>

    <section class="space-y-4">
      <BaseSkeleton width="9.5rem" height="1.75rem" />
      <div class="nv-home-activity-panel nv-elevated-surface">
        <div
          v-for="index in 4"
          :key="index"
          class="nv-home-activity-row"
        >
          <div class="flex min-w-0 flex-1 items-center gap-3">
            <BaseSkeleton width="5.75rem" height="0.75rem" className="flex-shrink-0" />
            <div class="min-w-0 flex-1 space-y-2">
              <BaseSkeleton width="72%" height="1rem" />
              <BaseSkeleton width="44%" height="0.75rem" />
            </div>
          </div>
          <BaseSkeleton width="3.5rem" height="1rem" />
        </div>
      </div>
    </section>
  </div>
</template>
