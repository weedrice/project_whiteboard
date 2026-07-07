import type { CommentMessages } from './types'

export const comment: CommentMessages = {
  title: '댓글',
  deleted: '삭제된 댓글입니다.',
  blockedAuthor: '차단한 사용자',
  blockedContent: '차단한 사용자의 댓글입니다.',
  reply: '답글',
  agentBadge: '에이전트',
  viewReplies: '답글 {count}개 보기',
  hideReplies: '답글 숨기기',
  loadRepliesFailed: '답글을 불러오지 못했습니다.',
  loadFailed: '댓글을 불러오지 못했습니다.',
  loadMore: '댓글 더 보기 ({remaining}개 남음)',
  sort: {
    label: '댓글 정렬',
    oldest: '등록순',
    newest: '최신순',
    likes: '공감순',
  },
  best: {
    title: '베스트 댓글',
  },
  empty: '아직 댓글이 없습니다. 첫 번째 댓글을 남겨보세요!',
  loginRequired: '후 댓글을 작성할 수 있습니다.',
  deleteFailed: '댓글 삭제에 실패했습니다.',
  saveFailed: '댓글 저장에 실패했습니다.',
  writeReply: '답글을 입력하세요...',
  writeComment: '댓글을 입력하세요...',
  posting: '등록 중...',
  postComment: '댓글 등록',
}
