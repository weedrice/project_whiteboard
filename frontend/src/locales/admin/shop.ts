import type { AdminMessages } from '../types'

export const adminShopMessages = {
  shop: {
    title: '상점 관리',
    description: '상점 아이템의 원본 상태를 확인하고 신규 판매를 중지하거나 재개합니다.',
    empty: '조건에 맞는 상점 아이템이 없습니다.',
    filter: {
      title: '아이템 검색',
      search: '이름',
      searchPlaceholder: '아이템 이름 검색',
      itemType: '아이템 유형',
      sourceStatus: '원본 상태',
      saleStatus: '판매 허용 상태',
      enabled: '활성',
      disabled: '비활성',
    },
    table: {
      id: 'ID',
      name: '아이템',
      type: '유형',
      price: '가격',
      sourceStatus: '원본 상태',
      saleStatus: '판매 상태',
      modifiedAt: '수정일',
      actions: '관리',
    },
    status: {
      active: '활성',
      inactive: '비활성',
      onSale: '판매 중',
      suspended: '관리자 판매 중지',
      sourceInactive: '원본 비활성',
      retired: '폐기됨',
    },
    actions: {
      suspend: '판매 중지',
      resume: '판매 재개',
    },
    modal: {
      suspendTitle: '아이템 판매 중지',
      resumeTitle: '아이템 판매 재개',
      description: '“{name}” 아이템의 판매 상태를 변경합니다. 기존 구매자의 권한은 변경되지 않습니다.',
      reason: '처리 사유',
      reasonPlaceholder: '운영 기록에 남길 사유를 입력하세요.',
    },
    messages: {
      reasonRequired: '처리 사유를 입력해 주세요.',
      reasonTooLong: '처리 사유는 500자 이하여야 합니다.',
      suspended: '아이템 판매를 중지했습니다.',
      resumed: '아이템 판매를 재개했습니다.',
      updateFailed: '판매 상태 변경에 실패했습니다.',
    },
  },
} satisfies Pick<AdminMessages, 'shop'>
