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

function getValueByPath(value: unknown, path: string): unknown {
  return path.split('.').reduce<unknown>((current, key) => {
    if (!isMessageRecord(current)) return undefined
    return current[key]
  }, value)
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
})
