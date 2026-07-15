import type { NotificationMessages } from './types'

export const notification: NotificationMessages = {
  title: '알림 목록',
  unreadNotifications: '읽지 않은 알림 {count}개',
  markAllRead: '모두 읽음으로 표시',
  markAllReadShort: '모두 읽음',
  empty: '새로운 알림이 없습니다.',
  emptyDescription: '새 댓글, 멘션, 쪽지가 생기면 여기에 모입니다.',
  groupedCount: '{count}개 알림이 묶였어요',
  actors: {
    system: '시스템',
    unknown: '알 수 없음',
  },
  comment: {
    created: '{0}님이 댓글을 남겼습니다.',
    liked: '{0}님이 회원님의 댓글을 좋아합니다.',
  },
  reply: { created: '{0}님이 답글을 남겼습니다.' },
  post: { liked: '{0}님이 회원님의 게시글을 좋아합니다.' },
  badge: { awarded: '{0} 뱃지를 획득했습니다.' },
  scheduled: {
    published: '예약 게시글 "{0}"이 게시되었습니다.',
    failed: '예약 게시글 "{0}" 게시에 실패했습니다.',
  },
  message: { received: '{0}님이 쪽지를 보냈습니다.' },
  mention: { created: '{0}님이 회원님을 멘션했습니다.' },
  keyword: { matched: '키워드 "{0}"이 포함된 게시글이 등록되었습니다.' },
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
    badge: '뱃지',
  },
}
