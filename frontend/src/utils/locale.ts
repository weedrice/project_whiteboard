import type { SupportedLocale } from '@/locales/types'

const intlLocales: Record<SupportedLocale, string> = {
  ko: 'ko-KR',
  en: 'en-US',
  ja: 'ja-JP',
}

export function getIntlLocale(locale: string): string {
  return intlLocales[locale as SupportedLocale] ?? intlLocales.ko
}
