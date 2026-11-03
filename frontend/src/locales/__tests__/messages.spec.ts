import { describe, expect, it, vi } from 'vitest'
import { createI18n, type VueMessageType } from 'vue-i18n'
import type { LocaleMessage } from '@intlify/core-base'
import messages from '../index'
import englishMessages from '../en'
import japaneseMessages from '../ja'
import type { SupportedLocale } from '../types'

type MessageRecord = Record<string, unknown>

const supportedLocales: SupportedLocale[] = ['ko', 'en', 'ja']
const allMessages = { ko: messages.ko, en: englishMessages, ja: japaneseMessages }
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
  return Boolean(value) && typeof value === 'object'
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
  it('keeps only Korean in the initial message bundle', () => {
    expect(Object.keys(messages)).toEqual(['ko'])
  })

  it('compiles every Japanese message including array entries and interpolation', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    try {
      const translator = createI18n({
        legacy: false, locale: 'ja', fallbackLocale: false,
        messages: { ja: japaneseMessages as unknown as LocaleMessage<VueMessageType> }, warnHtmlMessage: false,
      })
      collectLeafKeys(japaneseMessages).forEach((key) => {
        const message = getValueByPath(japaneseMessages, key)
        if (typeof message !== 'string') return
        const params = Object.fromEntries(collectInterpolationKeys(message).map((name) => [name, 3]))
        expect(translator.global.t(key, params)).not.toBe(key)
      })
      expect(error).not.toHaveBeenCalled()
    } finally {
      error.mockRestore()
    }
  })

  it('keeps locale message leaf keys in sync', () => {
    const koKeys = collectLeafKeys(messages.ko)

    supportedLocales
      .filter((locale) => locale !== 'ko')
      .forEach((locale) => {
        expect(collectLeafKeys(allMessages[locale])).toEqual(koKeys)
      })
  })

  it('keeps interpolation parameter names in sync', () => {
    const koKeys = collectLeafKeys(messages.ko)

    supportedLocales
      .filter((locale) => locale !== 'ko')
      .forEach((locale) => {
        koKeys.forEach((key) => {
          expect(collectInterpolationKeys(getValueByPath(allMessages[locale], key))).toEqual(
            collectInterpolationKeys(getValueByPath(messages.ko, key)),
          )
        })
      })
  })

  it.each(Object.keys(japaneseMessages) as (keyof typeof japaneseMessages)[])(
    'contains dedicated Japanese text for the %s domain', (domain) => {
      expect(allMessages.ja[domain]).not.toBe(messages.ko[domain])
      expect(allMessages.ja[domain]).not.toBe(englishMessages[domain])
      const strings = collectLeafValues(allMessages.ja[domain]).filter(
        (value): value is string => typeof value === 'string',
      )
      expect(strings.some((value) => /[ぁ-んァ-ヶ]/.test(value))).toBe(true)
      expect(strings.some((value) => koreanTextPattern.test(value))).toBe(false)
    },
  )

  it.each(translatedDomains)('uses a dedicated English object for the %s domain', (domain) => {
    expect(allMessages.en[domain]).not.toBe(messages.ko[domain])
  })

  it.each(verifiedEnglishDomains)('keeps %s English keys aligned with Korean keys', (domain) => {
    expect(collectLeafKeys(allMessages.en[domain])).toEqual(collectLeafKeys(messages.ko[domain]))
  })

  it.each(verifiedEnglishDomains)('keeps %s interpolation parameters aligned', (domain) => {
    collectLeafKeys(messages.ko[domain]).forEach((key) => {
      expect(collectInterpolationKeys(getValueByPath(allMessages.en[domain], key))).toEqual(
        collectInterpolationKeys(getValueByPath(messages.ko[domain], key)),
      )
    })
  })

  it.each(verifiedEnglishDomains)('contains translated English text for the %s domain', (domain) => {
    const englishStrings = collectLeafValues(allMessages.en[domain]).filter(
      (value): value is string => typeof value === 'string',
    )
    expect(englishStrings).not.toHaveLength(0)
    expect(englishStrings.some((value) => koreanTextPattern.test(value))).toBe(false)
  })
})
