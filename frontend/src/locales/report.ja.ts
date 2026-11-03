import type { ReportMessages } from './types'

export const reportJa: ReportMessages = {
  title: 'ユーザーを通報',
  target: '通報対象',
  reason: '通報理由',
  reasonType: '通報の種類',
  inputReason: '通報理由を入力してください。',
  reasonTooLong: '通報理由は{max}文字以内で入力してください。',
  reportSuccess: '通報を送信しました。',
  reportFailed: '通報の送信に失敗しました。',
  reasonTypes: { SPAM: 'スパム', ABUSE: '暴言・嫌がらせ', ADULT: '成人向けコンテンツ', ETC: 'その他' },
  types: { post: '投稿', comment: 'コメント', user: 'ユーザー' },
}
