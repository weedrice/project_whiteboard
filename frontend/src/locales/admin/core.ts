import type { AdminMessages } from '../types'

export const adminCoreMessages = {
  common: {
    search: '검색',
    reset: '초기화',
    all: '전체',
    pageSize: '페이지 당',
    detail: '상세',
    rowDetailAria: '{name} 상세 보기',
    footerDoubleClickHint: '사용자 행을 더블클릭하면 상세 팝업이 열립니다.',
  },
  layout: {
    title: '노비스 관리자',
  },
  menu: {
    dashboard: '대시보드',
    users: '사용자 관리',
    admins: '관리자 관리',
    boards: '스페이스 관리',
    inquiries: '문의 조회',
    reports: '신고 관리',
    security: '보안 설정',
    settings: '전역 설정',
    errorLogs: '에러 로그',
  },
  dashboard: {
    title: '대시보드',
    totalUsers: '전체 사용자',
    pendingReports: '대기 중인 신고',
    blockedIps: '차단된 IP',
    activeUsers24h: '24시간 활성 사용자',
    viewDetail: '상세 보기',
    recentActivity: '최근 활동',
    noActivity: '최근 활동이 없습니다.',
  },
} satisfies Pick<AdminMessages, 'common' | 'layout' | 'menu' | 'dashboard'>
