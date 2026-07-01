import { describe, expect, it } from 'vitest'
import messages from '../index'
import type { SupportedLocale } from '../types'

type MessageRecord = Record<string, unknown>

const supportedLocales: SupportedLocale[] = ['ko', 'en']

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
})
