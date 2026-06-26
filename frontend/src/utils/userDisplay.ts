export function formatUserDisplayName(
  displayName?: string | null,
  maxLength?: number,
  fallbackLabel = '',
): string {
  const normalizedName = displayName?.trim() || fallbackLabel

  if (!maxLength || maxLength <= 0) {
    return normalizedName
  }

  const characters = Array.from(normalizedName)
  if (characters.length <= maxLength) {
    return normalizedName
  }

  return `${characters.slice(0, maxLength).join('')}...`
}
