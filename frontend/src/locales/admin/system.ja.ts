import type { AdminMessages } from '../types'

export const adminSystemMessagesJa = {
  security: {
    title: 'セキュリティ設定', description: 'IPブロックなどのシステムセキュリティ設定を管理します。',
    detail: { title: 'IPブロック詳細', blockInfo: 'ブロック情報', endDate: '終了日' }, addTitle: 'IPブロックを追加',
    ipAddress: 'IPアドレス', ipPlaceholder: '例：192.168.0.1', reason: 'ブロックの理由', reasonPlaceholder: 'ブロックする理由を入力',
    table: { ipAddress: 'IPアドレス', reason: '理由', adminId: '管理者ID', createdAt: 'ブロック日時' }, actions: { unblock: 'ブロック解除' },
    messages: {
      blocked: 'IPアドレスをブロックしました。', blockFailed: 'IPアドレスをブロックできませんでした', confirmUnblock: '{ip}のブロックを解除しますか？',
      unblocked: 'IPアドレスのブロックを解除しました。', unblockFailed: 'IPアドレスのブロックを解除できませんでした',
    },
  },
  settings: {
    title: '全体設定', description: 'サイト全体の設定を管理します。', addConfig: '設定を追加', table: { desc: '説明' },
    messages: { saved: '設定を保存しました。', saveFailed: '設定を保存できませんでした' },
  },
  commonCodes: {
    title: '共通コード管理', description: '運用ポリシーで使用するコードグループと詳細コードを管理します。', master: 'コードグループ',
    details: '詳細コード', addCode: 'コードグループを追加', addDetail: '詳細コードを追加', editDetail: '詳細コードを編集',
    typeCode: '種別コード', typeName: '種別名', codeValue: 'コード値', codeName: 'コード名',
    messages: {
      invalid: '必須項目と入力文字数を確認してください。', saved: 'コードグループを保存しました。', detailSaved: '詳細コードを保存しました。',
      confirmDiscardChanges: 'コードグループに未保存の変更があります。このページを離れますか？', confirmDelete: 'この詳細コードを削除しますか？', deleted: '詳細コードを削除しました。',
    },
  },
  boards: {
    title: 'スペース管理', description: 'すべてのスペースを確認・管理します。', addTitle: 'スペースを作成', editTitle: 'スペースを編集',
    iconEmpty: 'アイコンなし', iconUrlEmpty: 'アイコンURLは登録されていません。', chooseFile: 'ファイルを選択', managerTitle: 'スペース管理者',
    chooseManager: '管理者を選択', userSelectTitle: 'ユーザーを選択', unlisted: '一覧非表示',
    table: { desc: '説明', active: 'ステータス', sortOrder: '表示順', actions: '管理' },
    messages: {
      created: 'スペースを作成しました。', updated: 'スペースを更新しました。', deleted: 'スペースを削除しました。', createFailed: 'スペースを作成できませんでした',
      updateFailed: 'スペースを更新できませんでした', deleteFailed: 'スペースを削除できませんでした', confirmDelete: 'このスペースを削除しますか？',
      confirmDiscardChanges: '未保存の変更があります。このページを離れますか？', confirmDeactivate: 'このスペースを無効にしますか？ユーザーのアクセスや新規投稿に影響する場合があります。',
    },
  },
  errorLogs: {
    title: 'エラーログ', description: 'システムのエラーログを確認・管理します。', empty: 'エラーログはありません。',
    table: {
      errorCode: 'エラーコード', errorType: 'エラー種別', httpStatus: 'HTTPステータス', message: 'メッセージ', requestUri: 'リクエストURI',
      requestMethod: 'メソッド', userId: 'ユーザーID', ipAddress: 'IPアドレス', isResolved: '解決状況', createdAt: '発生日時',
    },
    status: { resolved: '解決済み', unresolved: '未解決' },
    filter: { all: 'すべて', errorType: 'エラー種別', httpStatus: 'HTTPステータス', isResolved: '解決状況', startDate: '開始日', endDate: '終了日', requestUri: 'リクエストURI' },
    detail: {
      title: 'エラーログ詳細', errorInfo: 'エラー情報', requestInfo: 'リクエスト情報', stackTrace: 'スタックトレース', resolveInfo: '解決情報',
      closeAria: '詳細ダイアログを閉じる', resolveCloseAria: '解決ダイアログを閉じる', resolvedBy: '解決者ID', resolvedAt: '解決日時', resolvedMemo: '解決メモ',
    },
    actions: { resolve: '解決済みにする', viewDetail: '詳細を表示', copy: 'コピー' },
    messages: {
      resolved: 'エラーログを解決済みにしました。', resolveFailed: 'エラーログを解決済みにできませんでした',
      stackTraceCopied: 'スタックトレースをコピーしました。', stackTraceCopyFailed: 'スタックトレースをコピーできませんでした。',
    },
    stats: { total: '合計', unresolved: '未解決', resolved: '解決済み' }, memoPlaceholder: '解決メモを入力（任意）',
  },
} satisfies Pick<AdminMessages, 'security' | 'settings' | 'commonCodes' | 'boards' | 'errorLogs'>
