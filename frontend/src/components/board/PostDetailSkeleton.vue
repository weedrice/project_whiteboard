<script setup lang="ts">
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
</script>

<template>
  <div aria-busy="true">
    <header class="nv-post-header px-4 py-4 sm:px-6 sm:py-5">
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0 flex-1 space-y-4">
          <div class="flex items-center justify-between gap-2">
            <BaseSkeleton width="6rem" height="2.25rem" rounded="rounded-full" />
            <div class="flex gap-2">
              <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" />
              <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" />
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex flex-wrap items-center gap-2">
              <BaseSkeleton width="5.5rem" height="0.875rem" />
              <BaseSkeleton width="0.25rem" height="0.25rem" rounded="rounded-full" />
              <BaseSkeleton width="3.75rem" height="0.875rem" />
            </div>
            <BaseSkeleton width="82%" height="2.25rem" />
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
              <BaseSkeleton width="7.5rem" height="1rem" />
              <BaseSkeleton width="8rem" height="1rem" />
              <BaseSkeleton width="3.5rem" height="1rem" />
              <BaseSkeleton width="4rem" height="1rem" />
            </div>
          </div>
        </div>
      </div>
    </header>

    <div class="px-4 pt-5 lg:px-6">
      <article class="nv-post-content-stack min-w-0">
        <div class="nv-post-content-top">
          <BaseSkeleton width="min(16rem, 42vw)" height="2rem" rounded="rounded-full" />
        </div>

        <div class="nv-post-article space-y-3">
          <BaseSkeleton width="100%" height="1.25rem" />
          <BaseSkeleton width="94%" height="1.25rem" />
          <BaseSkeleton width="88%" height="1.25rem" />
          <BaseSkeleton width="100%" height="18rem" rounded="rounded-[18px]" />
          <BaseSkeleton width="76%" height="1.25rem" />
          <BaseSkeleton width="52%" height="1.25rem" />
        </div>

        <div class="nv-post-tags">
          <BaseSkeleton width="4rem" height="0.875rem" />
          <div class="flex flex-wrap gap-2">
            <BaseSkeleton width="4.5rem" height="1.75rem" rounded="rounded-full" />
            <BaseSkeleton width="5rem" height="1.75rem" rounded="rounded-full" />
            <BaseSkeleton width="3.75rem" height="1.75rem" rounded="rounded-full" />
          </div>
        </div>

        <div class="hidden sm:block">
          <div class="nv-post-reaction-row">
            <BaseSkeleton v-for="index in 3" :key="index" width="3.1rem" height="3.1rem" rounded="rounded-full" />
          </div>
        </div>

        <section class="nv-post-comments">
          <div class="mt-6 sm:mt-8">
            <BaseSkeleton width="6rem" height="1.5rem" className="mb-4 sm:mb-6" />

            <div class="mb-6 rounded-[24px] border border-[var(--nv-line)] bg-[color:var(--nv-surface)] px-4 py-4 sm:mb-8 sm:px-5">
              <BaseSkeleton width="100%" height="5.5rem" rounded="rounded-[18px]" />
              <div class="mt-3 flex justify-end">
                <BaseSkeleton width="5rem" height="2.25rem" rounded="rounded-lg" />
              </div>
            </div>

            <div class="space-y-4 sm:space-y-6">
              <div v-for="index in 3" :key="index" class="flex space-x-3">
                <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" className="flex-shrink-0" />
                <div class="min-w-0 flex-1 space-y-2">
                  <div class="flex items-center justify-between gap-3">
                    <BaseSkeleton width="6.25rem" height="1rem" />
                    <BaseSkeleton width="3.75rem" height="0.875rem" />
                  </div>
                  <BaseSkeleton width="100%" height="1rem" />
                  <BaseSkeleton width="80%" height="1rem" />
                </div>
              </div>
            </div>
          </div>
        </section>
      </article>
    </div>
  </div>
</template>
