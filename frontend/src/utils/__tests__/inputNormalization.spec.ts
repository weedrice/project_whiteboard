import { describe, expect, it } from 'vitest'
import {
  normalizeBoardWritePayload,
  normalizeConfigWritePayload,
  optionalTrimmedText,
  trimText,
} from '../inputNormalization'

describe('inputNormalization', () => {
  it('trims nullable text-like values to a string', () => {
    expect(trimText('  value  ')).toBe('value')
    expect(trimText(123)).toBe('123')
    expect(trimText(null)).toBe('')
    expect(trimText(undefined)).toBe('')
  })

  it('returns undefined for blank optional text', () => {
    expect(optionalTrimmedText('  value  ')).toBe('value')
    expect(optionalTrimmedText('   ')).toBeUndefined()
    expect(optionalTrimmedText(null)).toBeUndefined()
  })

  it('normalizes board write payload text fields and private agent usage', () => {
    expect(normalizeBoardWritePayload({
      boardName: '  Board  ',
      boardUrl: '  board  ',
      description: '  description  ',
      iconUrl: '  icon  ',
      isPublic: false,
      agentUseYn: true,
      guidePrompt: '  guide  ',
    })).toEqual({
      boardName: 'Board',
      boardUrl: 'board',
      description: 'description',
      iconUrl: 'icon',
      isPublic: false,
      agentUseYn: false,
      guidePrompt: 'guide',
    })
  })

  it('normalizes config write payloads and rejects missing required values', () => {
    expect(normalizeConfigWritePayload({
      key: '  site.name  ',
      value: '  Noviis  ',
      description: '  Service  ',
    }, { requireKey: true })).toEqual({
      key: 'site.name',
      value: 'Noviis',
      description: 'Service',
    })
    expect(normalizeConfigWritePayload({ key: 'site.name', value: '   ' }, { requireKey: true })).toBeNull()
    expect(normalizeConfigWritePayload({ key: '   ', value: 'Noviis' }, { requireKey: true })).toBeNull()
  })
})
