import type { LocaleMessages } from './types'
import { common } from './common'
import { search } from './search'
import { layout } from './layout'
import { auth } from './auth'
import { board } from './board'
import { comment } from './comment'
import { notification } from './notification'
import { user } from './user'
import { report } from './report'
import { admin } from './admin'

/**
 * 통합된 번역 메시지 객체
 * 
 * Phase 1 개선사항:
 * - 중복 키 제거 (networkRetry)
 * - 누락된 키 추가 (skipToContent, noResultsFor)
 * - 네이밍 통일 (failCreate → createFailed, failUpdate → updateFailed, failDelete → deleteFailed)
 * - 하드코딩 값 제거 (포인트 비용을 {cost} 변수로 변경)
 * 
 * Phase 2 개선사항:
 * - TypeScript 전환으로 타입 안정성 확보
 * - 파일 모듈화로 유지보수성 향상
 * - 타입 정의 추가로 자동완성 및 오류 검출 지원
 */
export const messages: LocaleMessages = {
  ko: {
    common,
    search,
    layout,
    auth,
    board,
    comment,
    notification,
    user,
    report,
    admin,
  },
}

export default messages
