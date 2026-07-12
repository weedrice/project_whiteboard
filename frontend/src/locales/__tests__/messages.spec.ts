import { describe, expect, it } from 'vitest'
import messages from '../index'
import type { SupportedLocale } from '../types'

type MessageRecord = Record<string, unknown>

const supportedLocales: SupportedLocale[] = ['ko', 'en']
const translatedDomains = ['search', 'user', 'admin'] as const
const verifiedEnglishDomains = [
  'common',
  'search',
  'home',
  'layout',
  'auth',
  'board',
  'comment',
  'notification',
  'user',
  'report',
  'emoticon',
  'admin',
  'onboarding',
  'privacy',
] as const
const koreanTextPattern = /[가-힣]/

function isMessageRecord(value: unknown): value is MessageRecord {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function collectLeafKeys(value: unknown, prefix = ''): string[] {
  if (!isMessageRecord(value)) {
    return [prefix]
  }

  return Object.keys(value)
    .sort()
    .flatMap((key) => collectLeafKeys(value[key], prefix ? `${prefix}.${key}` : key))
}

function getValueByPath(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, key) => {
    if (!isMessageRecord(current)) return undefined
    return current[key]
  }, value)
}

function collectLeafValues(value: unknown): unknown[] {
  if (!isMessageRecord(value)) return [value]
  return Object.values(value).flatMap(collectLeafValues)
}

function collectInterpolationKeys(message: unknown): string[] {
  if (typeof message !== 'string') return []
  return Array.from(message.matchAll(/\{([A-Za-z0-9_]+)\}/g), ([, key]) => key).sort()
}

describe('locale messages', () => {
  it('registers every supported locale', () => {
    expect(Object.keys(messages).sort()).toEqual([...supportedLocales].sort())
  })

  it('keeps locale message leaf keys in sync', () => {
    const koKeys = collectLeafKeys(messages.ko)

    supportedLocales
      .filter((locale) => locale !== 'ko')
      .forEach((locale) => {
        expect(collectLeafKeys(messages[locale])).toEqual(koKeys)
      })
  })

  it('keeps interpolation parameter names in sync', () => {
    const koKeys = collectLeafKeys(messages.ko)

    supportedLocales
      .filter((locale) => locale !== 'ko')
      .forEach((locale) => {
        koKeys.forEach((key) => {
          expect(collectInterpolationKeys(getValueByPath(messages[locale], key))).toEqual(
            collectInterpolationKeys(getValueByPath(messages.ko, key)),
          )
        })
      })
  })

  it.each(translatedDomains)('uses a dedicated English object for the %s domain', (domain) => {
    expect(messages.en[domain]).not.toBe(messages.ko[domain])
  })

  it.each(verifiedEnglishDomains)('keeps %s English keys aligned with Korean keys', (domain) => {
    expect(collectLeafKeys(messages.en[domain])).toEqual(collectLeafKeys(messages.ko[domain]))
  })

  it.each(verifiedEnglishDomains)('keeps %s interpolation parameters aligned', (domain) => {
    collectLeafKeys(messages.ko[domain]).forEach((key) => {
      expect(collectInterpolationKeys(getValueByPath(messages.en[domain], key))).toEqual(
        collectInterpolationKeys(getValueByPath(messages.ko[domain], key)),
      )
    })
  })

  it.each(verifiedEnglishDomains)('contains translated English text for the %s domain', (domain) => {
    const englishStrings = collectLeafValues(messages.en[domain]).filter(
      (value): value is string => typeof value === 'string',
    )
    expect(englishStrings).not.toHaveLength(0)
    expect(englishStrings.some((value) => koreanTextPattern.test(value))).toBe(false)
  })
})
