import { afterEach, describe, expect, it } from 'vitest'
import i18n from './i18n'

const locale = i18n.global.locale as { value: string }

describe('i18n locale behavior', () => {
  afterEach(() => {
    locale.value = 'ko'
  })

  it('switches all translated domains between Korean and English', () => {
    locale.value = 'en'

    expect(i18n.global.t('search.doSearch')).toBe('Search')
    expect(i18n.global.t('user.settings.language')).toBe('Language')
    expect(i18n.global.t('admin.menu.users')).toBe('User management')
    expect(i18n.global.t('board.list.all')).toBe('All spaces')
    expect(i18n.global.t('comment.title')).toBe('Comments')
    expect(i18n.global.t('common.terms.title')).toBe('Terms of Service')

    locale.value = 'ko'

    expect(i18n.global.t('search.doSearch')).toBe('검색')
    expect(i18n.global.t('user.settings.language')).toBe('언어')
    expect(i18n.global.t('admin.menu.users')).toBe('사용자 관리')
    expect(i18n.global.t('common.terms.title')).toBe('서비스 이용약관')
  })

  it('falls back to Korean for an unsupported locale', () => {
    locale.value = 'fr'

    expect(i18n.global.t('search.doSearch')).toBe('검색')
    expect(i18n.global.t('user.settings.language')).toBe('언어')
    expect(i18n.global.t('admin.menu.users')).toBe('사용자 관리')
  })
})
