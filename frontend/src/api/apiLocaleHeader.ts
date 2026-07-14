import type { InternalAxiosRequestConfig } from 'axios'
import type { SupportedLocale } from '@/locales/types'

type ApiLocaleResolver = () => string | null | undefined

let localeResolver: ApiLocaleResolver = () => 'ko'

export function configureApiLocaleResolver(resolver: ApiLocaleResolver): void {
  localeResolver = resolver
}

export function getApiLocale(): SupportedLocale {
  const locale = localeResolver()?.trim().toLowerCase()
  return locale === 'en' ? 'en' : 'ko'
}

export function applyApiLocaleHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  config.headers['Accept-Language'] = getApiLocale()
  return config
}
