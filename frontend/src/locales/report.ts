import type { ReportMessages } from './types'

export const report: ReportMessages = {
  reasonType: '신고 유형',
  reasonTypes: {
    SPAM: '스팸',
    ABUSE: '욕설 및 괴롭힘',
    ADULT: '성인 콘텐츠',
    ETC: '기타',
  },
  title: '사용자 신고',
  target: '신고 대상',
  reason: '신고 사유',
  inputReason: '신고 사유를 입력해주세요.',
  reasonTooLong: '신고 사유는 {max}자 이하로 입력해주세요.',
  reportSuccess: '신고가 접수되었습니다.',
  reportFailed: '신고 접수에 실패했습니다.',
  types: {
    post: '게시글',
    comment: '댓글',
    user: '사용자',
  },
}
