import type { ShopMessages } from './types'

export const shopEn: ShopMessages = {
  title: 'Shop',
  description: 'Browse items available for purchase with points.',
  currentPoints: 'Current points',
  loginHint: 'Sign in to purchase an item.',
  loginToPurchase: 'Sign in to purchase',
  itemType: 'Item type',
  price: 'Price',
  purchase: 'Purchase',
  purchasing: 'Purchasing',
  purchaseConfirm: 'Purchase {name} for {price}P?',
  purchaseSuccess: 'Item purchased.',
  purchaseFailed: 'Failed to purchase the item.',
  insufficientPoints: 'Not enough points. You have {current}P / Need {price}P',
  empty: 'No items are currently available.',
  itemTypes: {
    ALL: 'All types',
    EMOTICON: 'Emoticon',
  },
  purchases: {
    title: 'Purchase history',
    empty: 'No purchase history.',
    purchasedAt: 'Purchased',
    purchasedPrice: 'Price paid',
    imageAlt: '{name} image',
  },
}
