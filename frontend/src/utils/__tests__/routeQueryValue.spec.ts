import { describe, expect, it } from 'vitest'
import {
  areQueriesEqual,
  firstQueryValue,
  getSingleQueryValue,
  parseCategoryIdFromQuery,
  parseConceptFromQuery,
  parsePageFromQuery
} from '@/utils/routeQueryValue'

describe('routeQueryValue', () => {
  it('returns a string value for search query fields', () => {
    expect(firstQueryValue(' search ')).toBe(' search ')
    expect(firstQueryValue(['first', 'second'])).toBe('first')
    expect(firstQueryValue([])).toBe('')
    expect(firstQueryValue(null)).toBe('')
    expect(firstQueryValue(undefined)).toBe('')
  })

  it('returns null for missing or empty single query values', () => {
    expect(getSingleQueryValue('token')).toBe('token')
    expect(getSingleQueryValue(['first', 'second'])).toBe('first')
    expect(getSingleQueryValue('')).toBeNull()
    expect(getSingleQueryValue([''])).toBeNull()
    expect(getSingleQueryValue(null)).toBeNull()
    expect(getSingleQueryValue(undefined)).toBeNull()
  })

  it('parses one-based page query values into zero-based pages', () => {
    expect(parsePageFromQuery('1')).toBe(0)
    expect(parsePageFromQuery('3')).toBe(2)
    expect(parsePageFromQuery('0')).toBe(0)
    expect(parsePageFromQuery('-1')).toBe(0)
    expect(parsePageFromQuery('invalid')).toBe(0)
    expect(parsePageFromQuery(undefined)).toBe(0)
  })

  it('parses positive category ids only', () => {
    expect(parseCategoryIdFromQuery('12')).toBe(12)
    expect(parseCategoryIdFromQuery(3)).toBe(3)
    expect(parseCategoryIdFromQuery('0')).toBeNull()
    expect(parseCategoryIdFromQuery('-1')).toBeNull()
    expect(parseCategoryIdFromQuery('invalid')).toBeNull()
  })

  it('parses concept query flags', () => {
    expect(parseConceptFromQuery('1')).toBe(true)
    expect(parseConceptFromQuery('true')).toBe(true)
    expect(parseConceptFromQuery(' TRUE ')).toBe(true)
    expect(parseConceptFromQuery('0')).toBe(false)
    expect(parseConceptFromQuery(undefined)).toBe(false)
  })

  it('compares query objects by normalized string values', () => {
    expect(areQueriesEqual({ page: 2, tags: ['a', 'b'] }, { tags: ['a', 'b'], page: '2' })).toBe(true)
    expect(areQueriesEqual({ tags: ['a', 'b'] }, { tags: ['b', 'a'] })).toBe(false)
    expect(areQueriesEqual({ page: 1 }, { page: 1, q: 'test' })).toBe(false)
  })
})
