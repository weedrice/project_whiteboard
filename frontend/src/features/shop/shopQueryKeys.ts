import type { ShopItemPageParams, ShopPageParams } from '@/api/shop'

export const shopQueryKeys = {
  root: ['shop'] as const,
  itemsRoot: ['shop', 'items'] as const,
  items: (params: Readonly<ShopItemPageParams>) => [
    'shop',
    'items',
    params.page ?? 0,
    params.size ?? 20,
    params.itemType ?? '',
  ] as const,
  purchasesRoot: ['shop', 'purchases'] as const,
  purchases: (params: Readonly<ShopPageParams>) => [
    'shop',
    'purchases',
    params.page ?? 0,
    params.size ?? 20,
  ] as const,
}
