import type { ShopPageParams } from '@/api/shop'

export const shopQueryKeys = {
  root: ['shop'] as const,
  itemsRoot: ['shop', 'items'] as const,
  purchasesRoot: ['shop', 'purchases'] as const,
  purchases: (params: Readonly<ShopPageParams>) => [
    'shop',
    'purchases',
    params.page ?? 0,
    params.size ?? 20,
  ] as const,
}
