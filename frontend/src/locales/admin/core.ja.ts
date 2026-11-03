import type { AdminMessages } from '../types'

export const adminCoreMessagesJa = {
  common: {
    search: '検索', reset: 'リセット', all: 'すべて', pageSize: '表示件数', detail: '詳細',
    rowDetailAria: '{name}の詳細を表示',
    footerDoubleClickHint: 'ユーザーの行をダブルクリックすると、詳細ダイアログが開きます。',
  },
  layout: { title: 'Noviis 管理画面' },
  menu: {
    dashboard: 'ダッシュボード', users: 'ユーザー管理', admins: '管理者管理', boards: 'スペース管理',
    inquiries: 'お問い合わせ', reports: '通報管理', security: 'セキュリティ設定', settings: '全体設定',
    errorLogs: 'エラーログ', commonCodes: '共通コード', shop: 'ショップ管理',
  },
  dashboard: {
    title: 'ダッシュボード', totalUsers: '総ユーザー数', pendingReports: '未処理の通報', blockedIps: 'ブロック中のIP',
    activeUsers24h: '過去24時間のアクティブユーザー', viewDetail: '詳細を表示', recentActivity: '最近のアクティビティ',
    noActivity: '最近のアクティビティはありません。', deepStats: '詳細統計', days30: '30日間', days90: '90日間',
    dailyActivity: '日別アクティビティ', postsLine: '折れ線：投稿', commentsBar: '棒グラフ：コメント',
    moderationStatus: 'モデレーション状況', pending: '未処理', resolved: '処理済み', rejected: '却下',
    autoBlinds: '自動非表示', managerBlinds: '管理者による非表示', topSpaces: 'アクティブなスペース',
    auditLogs: '監査ログ', auditAction: '操作', auditActorType: '実行者の種類', auditBoardUrl: 'スペースURL',
    auditBoardName: 'スペース名', auditActorUserId: 'ユーザーID', auditActorName: 'ユーザー名',
    auditStartDate: '開始日', auditEndDate: '終了日', auditEmpty: '監査ログはありません。', auditTarget: '対象',
    auditActor: '実行者', auditReason: '理由', auditCreatedAt: '日時',
    auditActions: {
      POST_BLIND: '投稿を非表示', POST_UNBLIND: '投稿を再表示',
      POST_AUTO_BLIND: '投稿を自動非表示', COMMENT_AUTO_BLIND: 'コメントを自動非表示', REPORT_RESOLVE: '通報を処理', REPORT_REJECT: '通報を却下',
      USER_ACTIVATE: 'ユーザーを有効化', USER_SUSPEND: 'ユーザーを停止', SUPER_ADMIN_GRANT: '最高管理者権限を付与', SUPER_ADMIN_REVOKE: '最高管理者権限を取り消し',
      BOARD_DEACTIVATE: 'スペースを無効化',
      SHOP_ITEM_SALE_SUSPEND: 'ショップ商品の販売を停止', SHOP_ITEM_SALE_RESUME: 'ショップ商品の販売を再開',
    },
    auditTargets: { POST: '投稿', COMMENT: 'コメント', REPORT: '通報', USER: 'ユーザー', BOARD: 'スペース', SHOP_ITEM: 'ショップ商品' },
  },
} satisfies Pick<AdminMessages, 'common' | 'layout' | 'menu' | 'dashboard'>
