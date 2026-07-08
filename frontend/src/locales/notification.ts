import type { NotificationMessages } from './types'

export const notification: NotificationMessages = {
  title: '알림 목록',
  markAllRead: '모두 읽음으로 표시',
  markAllReadShort: '모두 읽음',
  empty: '새로운 알림이 없습니다.',
  emptyDescription: '새 댓글, 멘션, 쪽지가 생기면 여기에 모입니다.',
  groupedCount: '{count}개 알림이 묶였어요',
  sourceTypes: {
    post: '게시글',
    comment: '댓글',
    message: '쪽지',
  },
  types: {
    default: '알림',
    like: '좋아요',
    comment: '댓글',
    reply: '답글',
    mention: '멘션',
    message: '쪽지',
    system: '시스템',
    sanction: '제재',
    keyword: '키워드',
  },
}
