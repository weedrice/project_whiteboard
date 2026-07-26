import { afterEach, describe, expect, it, vi } from 'vitest'
import { normalizePageResponse } from '../pageResponse'
import logger from '@/utils/logger'

describe('normalizePageResponse', () => {
  it('prefers number over page and falls back to page when number is missing', () => {
    expect(normalizePageResponse({
      content: ['a'],
      number: 3,
      page: 7,
    }).number).toBe(3)

    expect(normalizePageResponse({
      content: ['a'],
      page: 7,
    }).number).toBe(7)
  })

  it('derives last from hasNext when last is missing', () => {
    expect(normalizePageResponse({
      content: ['a'],
      number: 0,
      totalPages: 10,
      hasNext: true,
    }).last).toBe(false)

    expect(normalizePageResponse({
      content: ['a'],
      number: 0,
      totalPages: 10,
      hasNext: false,
    }).last).toBe(true)
  })

  it('uses stable fallbacks when fields are missing', () => {
    expect(normalizePageResponse(null, {
      fallbackNumber: 4,
      fallbackTotalPages: ({ totalElements }) => totalElements + 1,
    })).toEqual({
      content: [],
      totalElements: 0,
      totalPages: 1,
      size: 0,
      number: 4,
      first: false,
      last: true,
      empty: true,
    })

    expect(normalizePageResponse({
      content: ['a', 'b'],
    }, {
      fallbackTotalPages: 9,
    })).toEqual({
      content: ['a', 'b'],
      totalElements: 2,
      totalPages: 9,
      size: 2,
      number: 0,
      first: true,
      last: false,
      empty: false,
    })
  })
})

describe('페이지 번호 누락 경고', () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('page와 number가 모두 없으면 개발 모드에서 경고한다', () => {
        const warn = vi.spyOn(logger, 'warn').mockImplementation(() => {})

        normalizePageResponse({ content: [1], size: 10, totalElements: 1 })

        expect(warn).toHaveBeenCalledTimes(1)
        expect(String(warn.mock.calls[0][0])).toContain('page/number')
    })

    it('page만 있어도 경고하지 않는다', () => {
        const warn = vi.spyOn(logger, 'warn').mockImplementation(() => {})

        normalizePageResponse({ content: [1], page: 0, size: 10, totalElements: 1 })

        expect(warn).not.toHaveBeenCalled()
    })

    it('응답이 없으면 경고 대상이 아니다', () => {
        const warn = vi.spyOn(logger, 'warn').mockImplementation(() => {})

        normalizePageResponse(null)
        normalizePageResponse(undefined)

        expect(warn).not.toHaveBeenCalled()
    })

    it('빈 목록 응답은 page가 없어도 경고 대상이 아니다', () => {
        const warn = vi.spyOn(logger, 'warn').mockImplementation(() => {})

        // 이전 테스트는 null/undefined만 넣어 이 분기를 한 번도 실행하지 않았다.
        // 실제로 확인해야 하는 것은 content가 빈 배열인 경우다.
        normalizePageResponse({ content: [], size: 10, totalElements: 0 })

        expect(warn).not.toHaveBeenCalled()
    })
})
