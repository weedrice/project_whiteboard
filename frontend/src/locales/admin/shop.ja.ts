import type { AdminMessages } from '../types'

export const adminShopMessagesJa = {
  shop: {
    title: 'ショップ管理', description: '元のコンテンツの利用状況を確認し、商品の新規販売を停止・再開します。',
    empty: '条件に一致する商品はありません。',
    filter: {
      title: '商品検索', search: '商品名', searchPlaceholder: '商品名を検索', itemType: '商品の種類', sourceStatus: '元のコンテンツの状態',
      saleStatus: '販売許可', enabled: '有効', disabled: '無効',
    },
    table: { id: 'ID', name: '商品', type: '種類', price: '価格', sourceStatus: '元のコンテンツの状態', saleStatus: '販売状況', modifiedAt: '更新日時', actions: '操作' },
    status: { active: '有効', inactive: '無効', onSale: '販売中', suspended: '管理者による販売停止', sourceInactive: '元のコンテンツが無効', retired: '公開終了' },
    actions: { suspend: '販売停止', resume: '販売再開' },
    modal: {
      suspendTitle: '商品の販売を停止', resumeTitle: '商品の販売を再開', description: '「{name}」の販売状況を変更します。既存の購入者は引き続き利用できます。',
      reason: '理由', reasonPlaceholder: '監査ログに記録する理由を入力してください。',
    },
    messages: {
      reasonRequired: '理由を入力してください。', reasonTooLong: '理由は500文字以内で入力してください。', suspended: '商品の販売を停止しました。',
      resumed: '商品の販売を再開しました。', updateFailed: '販売状況を変更できませんでした。',
    },
  },
} satisfies Pick<AdminMessages, 'shop'>
