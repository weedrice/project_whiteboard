<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import type { SemanticSearchResult } from '@/types'
import { resolveSemanticSearchResultRoute } from '@/utils/semanticSearchNavigation'

defineProps<{
  results: SemanticSearchResult[]
}>()
</script>

<template>
  <section class="min-w-0 space-y-3" data-testid="semantic-results">
    <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold nv-title">
      <Search class="h-5 w-5" />
      {{ $t('search.semanticRelated') }}
    </h2>
    <RouterLink
      v-for="result in results"
      :key="`${result.contentType}-${result.contentId}`"
      :to="resolveSemanticSearchResultRoute(result)"
      class="block min-w-0 rounded-md border nv-border p-4 nv-surface nv-hover-surface"
    >
      <p class="truncate text-sm font-semibold nv-title">{{ result.title }}</p>
      <p class="mt-1 line-clamp-2 text-sm nv-text-subtle">{{ result.excerpt }}</p>
      <p class="mt-2 text-xs nv-accent-text">{{ result.boardName }}</p>
    </RouterLink>
  </section>
</template>
