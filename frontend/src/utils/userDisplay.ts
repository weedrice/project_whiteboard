const DELETED_USER_LABEL = '탈퇴한 사용자'

export function formatUserDisplayName(displayName?: string | null, maxLength?: number): string {
  const normalizedName = displayName?.trim() || DELETED_USER_LABEL

  if (!maxLength || maxLength <= 0) {
    return normalizedName
  }

  const characters = Array.from(normalizedName)
  if (characters.length <= maxLength) {
    return normalizedName
  }

  return `${characters.slice(0, maxLength).join('')}...`
}
