import type { Ref } from 'vue'
import type { SearchParams } from '@/types'

export const searchQueryKeys = {
  posts: (params: Ref<SearchParams>) => ['search', 'posts', params] as const,
  integrated: (params: Ref<SearchParams>) => ['search', 'integrated', params] as const,
  integratedAll: ['search', 'integrated'] as const,
  popular: ['search', 'popular'] as const,
}
