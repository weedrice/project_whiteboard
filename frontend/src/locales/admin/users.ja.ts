import type { AdminMessages } from '../types'

export const adminUserMessagesJa = {
  users: {
    title: 'ユーザー管理', description: 'すべてのユーザーを確認・管理します。', searchPlaceholder: 'ユーザーを検索（IDまたはメールアドレス）',
    detail: {
      title: 'ユーザー詳細', basicInfo: '基本情報', status: 'ステータス', role: '権限', emailVerified: 'メール認証済み',
      emailNotVerified: 'メール未認証', dateInfo: '日時情報', createdAt: '登録日時', modifiedAt: '更新日時', lastLoginAt: '最終ログイン',
      bio: '自己紹介', profile: 'プロフィール', superAdmin: '最高管理者', writtenPosts: '投稿', writtenComments: 'コメント',
      subscribedBoards: '購読中のスペース', sanctions: '制裁', reportsAndSanctions: '通報・制裁', joinedAndRecentLogin: '登録 / 最近のログイン',
      joined: '登録日時', deletedAt: '退会日時', recentAccess: '最近のアクセス', accessTime: '日時', postsEmpty: '投稿はありません。',
      commentsEmpty: 'コメントはありません。', subscriptionsEmpty: '購読中のスペースはありません。', sanctionsEmpty: '制裁履歴はありません。',
      loadFailed: 'ユーザー情報を読み込めませんでした。',
    },
    filters: {
      status: 'ステータス', role: '権限', emailVerified: 'メール認証', superAdmin: '最高管理者', withdrawn: '退会状態',
      verified: '認証済み', unverified: '未認証', withdrawnOnly: '退会済み', activeOrSuspended: '有効 / 停止中',
      createdFrom: '登録日（開始）', createdTo: '登録日（終了）', lastLoginFrom: '最終ログイン（開始）', lastLoginTo: '最終ログイン（終了）',
      minActivityCount: '最小アクティビティ数', userSearch: 'ユーザー検索', unit: '人',
    },
    detailTabs: {
      visible: '表示中', notice: 'お知らせ', secret: '非公開投稿', spoiler: 'ネタバレ', reply: '返信', sourceDeleted: '元のコンテンツは削除済み',
      boardInactive: 'スペース無効', boardPrivate: '非公開スペース', subscriptionInactive: '無効', subscriptionPrivate: '非公開',
      subscriptionRestricted: '制限あり', subscriptionAccessible: 'アクセス可能', boardActive: '有効', boardInactiveShort: '無効',
      boardPublic: '公開', boardPrivateShort: '非公開', category: 'カテゴリ：{name}',
      postStats: '{views}回閲覧 · {likes}件のいいね · {comments}件のコメント', commentStats: '{likes}件のいいね · 階層 {depth}', sortOrder: '表示順 {order}',
    },
    table: { nickname: '表示名', email: 'メールアドレス', status: 'ステータス', joinedAt: '登録日時' },
    status: { ACTIVE: '有効', SUSPENDED: '停止中', DELETED: '退会済み' },
    role: { USER: 'ユーザー', ADMIN: '管理者', SUPER_ADMIN: '最高管理者', BOARD_ADMIN: 'スペース管理者', MODERATOR: 'モデレーター' },
    actions: { ban: '利用禁止', mute: '発言制限' },
    messages: {
      confirmStatusChange: 'このユーザーに「{action}」を実行しますか？', statusChanged: 'ステータスを変更しました。', statusChangeFailed: 'ステータスを変更できませんでした',
      enterReason: '{type}の理由を入力してください：', sanctionTitle: '{type}', sanctionComplete: '{type}を実行しました。', sanctionFailed: '制裁を適用できませんでした',
    },
  },
  sanction: {
    title: '制裁を適用', type: '制裁の種類', typeRequired: '制裁の種類を選択してください。',
    types: { WARNING: '警告', MUTE: '発言制限', BAN: '利用禁止' }, userLabel: 'ユーザー', reason: '理由', description: '詳細',
    descriptionPlaceholder: '詳細を入力してください。', duration: '期間（日）', durationHintMute: '1日以上の整数を入力してください。',
    durationHintBan: '無期限にする場合は空欄にしてください。期限付きの場合は日数を入力してください。', durationRequired: '1日以上の整数を入力してください。',
    historyProcessor: '処理者 #{id}', historyPeriod: '{start} - {end}', permanent: '無期限', relatedContent: '{type} #{id}',
    cancel: 'キャンセル', processing: '処理中...', submit: '制裁を適用', success: '{name}に制裁を適用しました。',
    reasons: { SPAM: 'スパム', ABUSIVE_LANGUAGE: '暴言', INAPPROPRIATE_CONTENT: '不適切なコンテンツ', OTHER: 'その他' },
  },
} satisfies Pick<AdminMessages, 'users' | 'sanction'>
