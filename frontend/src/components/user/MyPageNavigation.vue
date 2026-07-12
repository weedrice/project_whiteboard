<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { UserNavigationGroup } from '@/types/userNavigation'

const props = defineProps<{
  activePath: string
  groups: UserNavigationGroup[]
}>()

const isActive = (href: string) => href === '/mypage'
  ? props.activePath === href
  : props.activePath.startsWith(href)

const activeGroupKey = computed(() => props.groups.find((group) => (
  group.items.some((item) => isActive(item.href))
))?.nameKey)
</script>

<template>
  <nav :aria-label="$t('user.navigation.ariaLabel')">
    <div class="space-y-2 lg:hidden">
      <details
        v-for="group in groups"
        :key="group.nameKey"
        class="rounded-[var(--nv-radius-md)] border nv-border nv-surface"
        :open="group.nameKey === activeGroupKey"
      >
        <summary class="cursor-pointer px-4 py-3 text-sm font-semibold nv-title">
          {{ $t(group.nameKey) }}
        </summary>
        <div class="grid grid-cols-2 gap-2 border-t nv-border p-3 sm:grid-cols-3">
          <RouterLink
            v-for="item in group.items"
            :key="item.href"
            :to="item.href"
            class="rounded-[var(--nv-radius-sm)] px-3 py-2 text-sm nv-text-subtle nv-hover-surface"
            :class="isActive(item.href) && 'bg-[var(--nv-accent-soft)] font-semibold nv-accent-text'"
            :aria-current="isActive(item.href) ? 'page' : undefined"
          >
            {{ $t(item.nameKey) }}
          </RouterLink>
        </div>
      </details>
    </div>

    <div class="sticky top-20 hidden space-y-6 lg:block">
      <section v-for="group in groups" :key="group.nameKey">
        <h2 class="nv-kicker px-3">{{ $t(group.nameKey) }}</h2>
        <div class="mt-2 space-y-1">
          <RouterLink
            v-for="item in group.items"
            :key="item.href"
            :to="item.href"
            class="block rounded-[var(--nv-radius-sm)] px-3 py-2 text-sm font-medium nv-text-subtle nv-hover-surface"
            :class="isActive(item.href) && 'bg-[var(--nv-accent-soft)] nv-accent-text'"
            :aria-current="isActive(item.href) ? 'page' : undefined"
          >
            {{ $t(item.nameKey) }}
          </RouterLink>
        </div>
      </section>
    </div>
  </nav>
</template>
