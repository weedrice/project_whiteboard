import type { AdminMessages } from '../types'

export const adminModerationMessagesJa = {
  admins: {
    title: '管理者管理', description: '管理者権限を付与・管理します。', addSuperAdmin: '最高管理者を追加',
    addSuperAdminDesc: 'ユーザーに最高管理者権限を付与します。', addBoardAdmin: 'スペース管理者を追加',
    addBoardAdminDesc: 'ユーザーをスペースの管理者に任命します。', boardId: 'スペースID', superAdmins: '最高管理者', boardAdmins: 'スペース管理者',
    loginIdPlaceholder: 'ユーザーのログインID', table: { loginId: 'ログインID' },
    messages: {
      added: '管理者を追加しました。', addFailed: '管理者を追加できませんでした', statusChanged: 'ステータスを変更しました。',
      statusChangeFailed: 'ステータスを変更できませんでした', inputLoginId: 'ログインIDを入力してください。',
    },
  },
  reports: {
    title: '通報管理', description: '寄せられた通報を確認・処理します。', targetType: '対象の種類', reasonType: '通報の種類',
    targetContentId: '対象コンテンツID', remark: '理由', statusFilter: 'ステータス', detail: { title: '通報詳細', reportInfo: '通報情報' },
    table: { reporter: '通報者', createdAt: '通報日時', processor: '処理者' }, status: { PENDING: '未処理', RESOLVED: '処理済み', REJECTED: '却下' },
    actions: { resolve: '承認', reject: '却下', sanction: '制裁' },
    messages: {
      confirmResolve: 'この通報を承認しますか？', resolved: '通報を処理しました。', resolveFailed: '通報を処理できませんでした',
      sanctionResolved: '制裁を適用し、通報を処理しました。', sanctionResolveFailed: '制裁は適用されましたが、通報のステータスを更新できませんでした。',
      confirmReject: 'この通報を却下しますか？', rejected: '通報を却下しました。', rejectFailed: '通報を却下できませんでした',
    },
  },
  inquiries: {
    title: 'お問い合わせ', description: 'お問い合わせスペースの投稿を確認し、コメントで回答します。', empty: 'お問い合わせはありません。',
    total: '全{count}件', refreshing: '更新中...', sort: { label: '並び順', latest: '新しい順', oldest: '古い順' },
    table: { summary: '内容' }, status: { answered: '回答済み', pending: '回答待ち' }, detail: { title: 'お問い合わせ詳細' },
  },
} satisfies Pick<AdminMessages, 'admins' | 'reports' | 'inquiries'>
