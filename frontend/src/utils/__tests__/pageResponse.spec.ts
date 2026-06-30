import { describe, expect, it } from 'vitest'
import { normalizePageResponse } from '../pageResponse'

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
