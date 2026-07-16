import type { EmoticonSearchParams } from '@/types/emoticon'

export type EmoticonPeriod = 'daily' | 'weekly' | 'monthly'

export const emoticonQueryKeys = {
  detail: (emoticonId: number) => ['emoticon', emoticonId] as const,
  purchaseStatus: (emoticonId: number) => ['emoticon', emoticonId, 'purchased'] as const,
  listRoot: ['emoticons'] as const,
  popular: (period: EmoticonPeriod) => ['emoticons', 'popular', period] as const,
  searchable: (
    page: number,
    sortBy: NonNullable<EmoticonSearchParams['sortBy']>,
    keyword: string,
    searchType: NonNullable<EmoticonSearchParams['searchType']>,
  ) => ['emoticons', 'list', page, sortBy, keyword, searchType] as const,
  accessiblePicker: ['emoticons', 'accessible', 'picker'] as const,
}
