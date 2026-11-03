import { afterEach, describe, expect, it } from 'vitest'
import { AxiosHeaders } from 'axios'
import { applyApiLocaleHeader, configureApiLocaleResolver, getApiLocale } from '../apiLocaleHeader'

describe('API locale header', () => {
  afterEach(() => configureApiLocaleResolver(() => 'ko'))

  it.each(['ko', 'en', 'ja'] as const)('sends %s as Accept-Language', (locale) => {
    configureApiLocaleResolver(() => locale)
    const config = { headers: new AxiosHeaders() }
    applyApiLocaleHeader(config)
    expect(config.headers.get('Accept-Language')).toBe(locale)
  })

  it('normalizes Japanese and preserves Korean fallback for unsupported values', () => {
    configureApiLocaleResolver(() => ' JA ')
    expect(getApiLocale()).toBe('ja')
    configureApiLocaleResolver(() => 'fr')
    expect(getApiLocale()).toBe('ko')
    configureApiLocaleResolver(() => undefined)
    expect(getApiLocale()).toBe('ko')
  })
})
