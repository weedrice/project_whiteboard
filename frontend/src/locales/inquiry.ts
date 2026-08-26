import type { InquiryMessages } from './types'

export const inquiry: InquiryMessages = {
  common: {
    all: '전체', loading: '불러오는 중…', loadFailed: '불러오지 못했습니다.', notFound: '문의를 찾을 수 없거나 접근할 수 없습니다.', empty: '등록한 문의가 없습니다.',
    status: '상태', category: '카테고리', priority: '우선순위', fromDate: '시작일', toDate: '종료일', search: '검색', searchPlaceholder: '제목 검색', query: '조회', close: '닫기', remove: '제거', image: '이미지',
  },
  category: { ACCOUNT: '계정', SERVICE_USE: '서비스 이용', TECHNICAL: '기술 문제', CONTENT_OPERATION: '콘텐츠 운영', SUGGESTION: '제안', OTHER: '기타' },
  status: { NEW: '접수', IN_PROGRESS: '처리 중', RESOLVED: '답변 완료', CLOSED: '종료' },
  priority: { NORMAL: '보통', HIGH: '높음', URGENT: '긴급' },
  closureReason: { WITHDRAWN: '사용자 철회', USER_CONFIRMED: '사용자 해결 확인', ADMIN_CLOSED: '관리자 종료', AUTO_CLOSED: '자동 종료' },
  list: { title: '내 문의', description: '게시글과 분리된 문의 내역을 확인합니다.', create: '새 문의', modifiedAt: '최근 변경 {date}' },
  form: {
    title: '새 문의', description: '일반 텍스트로 작성되며 운영자에게만 전달됩니다.', category: '카테고리', subject: '제목', content: '문의 내용', count: '{count}/10,000', cancel: '취소', submit: '등록', validation: '제목은 1~200자, 내용은 1~10,000자로 입력해 주세요.', failed: '문의 등록에 실패했습니다.', createdNavigationFailed: '문의는 등록되었지만 상세 화면으로 이동하지 못했습니다.', openCreated: '등록된 문의 열기',
  },
  detail: {
    title: '문의 상세', description: '문의 진행 상태와 공개 대화를 확인합니다.', list: '목록', addMessage: '추가 답변', submitMessage: '답변 추가', messageValidation: '내용은 1~10,000자로 입력해 주세요.', messageFailed: '메시지 등록에 실패했습니다.', actionFailed: '요청을 처리하지 못했습니다.', withdraw: '문의 철회', close: '문의 종료', withdrawConfirm: '아직 답변되지 않은 문의를 철회할까요?', closeConfirm: '해결된 문의를 종료할까요?',
  },
  timeline: { label: '문의 대화', USER_MESSAGE: '사용자 메시지', STAFF_REPLY: '운영자 답변', INTERNAL_NOTE: '내부 메모' },
  upload: {
    choose: '이미지 첨부 ({count}/5)', uploading: '업로드 중…', remove: '제거', fallbackName: '파일 #{id}', max: '이미지는 메시지당 최대 5개까지 첨부할 수 있습니다.', invalid: 'JPEG, PNG, GIF, WebP 이미지만 파일당 10MiB까지 첨부할 수 있습니다.', failed: '이미지 업로드에 실패했습니다.',
  },
  admin: {
    title: '문의 관리', description: '신규 문의 처리와 기존 게시판 문의 아카이브를 분리해 관리합니다.', newTab: '신규 문의', legacyTab: '기존 문의', loadFailed: '신규 문의를 불러오지 못했습니다.', empty: '조건에 맞는 신규 문의가 없습니다.', author: '작성자', waitingSince: '대기 시작', archiveNotice: '기존 게시판 문의는 안정화 기간 동안 읽기 전용으로 제공합니다.', legacyEmpty: '기존 문의가 없습니다.', total: '총 {count}건', detail: '신규 문의 상세', start: '처리 시작', reopen: '재개', close: '관리자 종료', publicReply: '공개 답변', note: '내부 메모', notePlaceholder: '사용자에게 보이지 않는 메모', replyPlaceholder: '사용자에게 보낼 답변', addNote: '메모 추가', addReply: '답변 등록', closureReason: '종료 사유: {reason}', closePrompt: '관리자 종료 사유를 입력하세요.', closeReasonRequired: '종료 사유가 필요합니다.', actionFailed: '문의 작업을 처리하지 못했습니다.', contentValidation: '내용은 1~10,000자로 입력해 주세요.', legacyTitle: '제목', legacyContent: '내용', createdAt: '작성일',
  },
}
