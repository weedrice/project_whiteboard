import type { SearchParams } from '@/types'

export const searchQueryKeys = {
  posts: (params: Readonly<SearchParams>) => ['search', 'posts', { ...params }] as const,
  integrated: (params: Readonly<SearchParams>) => ['search', 'integrated', { ...params }] as const,
  integratedAll: ['search', 'integrated'] as const,
  semantic: (params: Readonly<SearchParams>) => ['search', 'semantic', { ...params }] as const,
  popular: ['search', 'popular'] as const,
  recent: ['search', 'recent'] as const,
}
