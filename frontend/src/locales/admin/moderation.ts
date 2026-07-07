import type { AdminMessages } from '../types'

export const adminModerationMessages = {
  admins: {
    title: '관리자 관리',
    description: '관리자 권한을 부여하거나 관리합니다.',
    addSuperAdmin: '최고 관리자 추가',
    addSuperAdminDesc: '사용자에게 최고 관리자 권한을 부여합니다.',
    addBoardAdmin: '스페이스 관리자 추가',
    addBoardAdminDesc: '특정 스페이스의 관리자로 사용자를 지정합니다.',
    boardId: '스페이스 ID',
    superAdmins: '최고 관리자 목록',
    boardAdmins: '스페이스 관리자 목록',
    loginIdPlaceholder: '사용자 로그인 ID',
    table: {
      loginId: '로그인 ID',
    },
    messages: {
      added: '관리자가 추가되었습니다.',
      addFailed: '관리자 추가 실패',
      statusChanged: '상태가 변경되었습니다.',
      statusChangeFailed: '상태 변경 실패',
      inputLoginId: '로그인 ID를 입력해주세요.',
    },
  },
  reports: {
    title: '신고 관리',
    description: '접수된 신고 내역을 확인하고 처리합니다.',
    targetType: '대상 유형',
    reasonType: '신고 유형',
    targetContentId: '대상 콘텐츠 ID',
    remark: '사유',
    detail: {
      title: '신고 상세',
      reportInfo: '신고 정보',
    },
    table: {
      reporter: '신고자',
      createdAt: '접수일',
      processor: '처리자',
    },
    status: {
      PENDING: '대기 중',
      RESOLVED: '처리됨',
      REJECTED: '반려됨',
    },
    actions: {
      resolve: '승인',
      reject: '반려',
      sanction: '제재',
    },
    messages: {
      confirmResolve: '신고를 처리(승인)하시겠습니까?',
      resolved: '신고가 처리되었습니다.',
      resolveFailed: '처리 실패',
      sanctionResolved: '제재 생성 후 신고가 처리되었습니다.',
      sanctionResolveFailed: '제재는 생성되었지만 신고 처리 상태 갱신에 실패했습니다.',
      confirmReject: '신고를 반려하시겠습니까?',
      rejected: '신고가 반려되었습니다.',
      rejectFailed: '반려 실패',
    },
  },
  inquiries: {
    title: '문의 게시글 조회',
    description: '문의 스페이스에 등록된 글을 확인하고 댓글로 답변할 수 있습니다.',
    empty: '등록된 문의가 없습니다.',
    total: '총 {count}건',
    refreshing: '갱신 중...',
    sort: {
      label: '정렬',
      latest: '최신순',
      oldest: '오래된순',
    },
    table: {
      summary: '내용',
    },
    status: {
      answered: '답변 완료',
      pending: '답변 대기',
    },
    detail: {
      title: '문의 상세',
    },
  },
} satisfies Pick<AdminMessages, 'admins' | 'reports' | 'inquiries'>
