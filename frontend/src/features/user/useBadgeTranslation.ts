import { useI18n } from 'vue-i18n'
import type { BadgeCompact } from '@/types'

type BadgeTranslationSource = Pick<BadgeCompact, 'badgeCode' | 'name'> & {
  description?: string | null
}

export function useBadgeTranslation() {
  const { t, te } = useI18n()

  const translatedField = (
    badge: BadgeTranslationSource,
    field: 'name' | 'description',
    fallback: string,
  ) => {
    const key = `user.badges.catalog.${badge.badgeCode}.${field}`
    return typeof te === 'function' && te(key) ? t(key) : fallback
  }

  const badgeName = (badge: BadgeTranslationSource) => (
    translatedField(badge, 'name', badge.name || badge.badgeCode)
  )

  const badgeDescription = (badge: BadgeTranslationSource) => (
    translatedField(badge, 'description', badge.description || '')
  )

  return {
    badgeName,
    badgeDescription,
  }
}
