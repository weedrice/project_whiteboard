import type { ShopMessages } from './types'

export const shop: ShopMessages = {
  title: '상점',
  description: '포인트로 사용할 수 있는 상품을 확인하세요.',
  currentPoints: '보유 포인트',
  loginHint: '구매하려면 로그인이 필요합니다.',
  loginToPurchase: '로그인 후 구매',
  itemType: '상품 유형',
  price: '가격',
  purchase: '구매',
  purchasing: '구매 중',
  purchaseConfirm: '{name}을(를) {price}P에 구매할까요?',
  purchaseSuccess: '상품을 구매했습니다.',
  purchaseFailed: '상품 구매에 실패했습니다.',
  insufficientPoints: '포인트가 부족합니다. 보유 {current}P / 필요 {price}P',
  empty: '판매 중인 상품이 없습니다.',
  purchases: {
    title: '구매 내역',
    empty: '구매 내역이 없습니다.',
    purchasedAt: '구매일',
    purchasedPrice: '구매가격',
    imageAlt: '{name} 이미지',
  },
}
