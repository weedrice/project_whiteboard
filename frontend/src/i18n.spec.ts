import { afterEach, describe, expect, it, vi } from 'vitest'
import i18n, { ensureLocaleMessages, setAppLocale } from './i18n'

const locale = i18n.global.locale as { value: string }

describe('i18n locale behavior', () => {
  afterEach(async () => {
    await setAppLocale('ko')
    vi.restoreAllMocks()
  })

  it('loads English messages on demand and switches every translated domain', async () => {
    await ensureLocaleMessages('en')
    await expect(setAppLocale('en')).resolves.toBe(true)

    expect(i18n.global.t('search.doSearch')).toBe('Search')
    expect(i18n.global.t('user.settings.language')).toBe('Language')
    expect(i18n.global.t('admin.menu.users')).toBe('User management')
    expect(i18n.global.t('board.list.all')).toBe('All spaces')
    expect(i18n.global.t('comment.title')).toBe('Comments')
    expect(i18n.global.t('common.terms.title')).toBe('Terms of Service')

    await expect(setAppLocale('ko')).resolves.toBe(true)
    expect(i18n.global.t('search.doSearch')).toBe('검색')
    expect(i18n.global.t('user.settings.language')).toBe('언어')
    expect(i18n.global.t('admin.menu.users')).toBe('사용자 관리')
    expect(i18n.global.t('common.terms.title')).toBe('서비스 이용약관')
  })

  it('falls back to Korean for an unsupported locale assigned by a third party', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    locale.value = 'fr'

    expect(i18n.global.t('search.doSearch')).toBe('검색')
    expect(i18n.global.t('user.settings.language')).toBe('언어')
    expect(i18n.global.t('admin.menu.users')).toBe('사용자 관리')
  })

  it('loads locale messages without committing after the session guard becomes stale', async () => {
    locale.value = 'ko'

    await expect(setAppLocale('en', () => false)).resolves.toBe(true)

    expect(locale.value).toBe('ko')
    expect(document.documentElement.lang).not.toBe('en')
  })
})
