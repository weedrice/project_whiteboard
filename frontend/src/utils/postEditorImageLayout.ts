const SAFE_POST_EDITOR_IMAGE_WIDTH_PATTERN = /^(\d+(?:\.\d+)?)(px|rem|em|%|ch|vw|vh)$/i
const LEGACY_POST_EDITOR_IMAGE_PERCENT_PATTERN = /^(\d+(?:\.\d+)?)%$/
const LEGACY_POST_EDITOR_IMAGE_PIXEL_PREFIX_PATTERN = /^(\d+(?:\.\d+)?)/

export function isSafePostEditorImageWidth(value: unknown): value is string {
  if (typeof value !== 'string') return false
  const match = SAFE_POST_EDITOR_IMAGE_WIDTH_PATTERN.exec(value)
  if (!match) return false

  const numericValue = Number.parseFloat(match[1])
  const unit = match[2].toLowerCase()
  return numericValue > 0 && (unit !== '%' || numericValue <= 100)
}

export function resolveLegacyPostEditorImageCssWidth(value: unknown): string | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? `${value}px` : null
  }
  if (typeof value !== 'string') return null

  const normalized = value.trim()
  const percentageMatch = LEGACY_POST_EDITOR_IMAGE_PERCENT_PATTERN.exec(normalized)
  if (percentageMatch) {
    const percentage = Number.parseFloat(percentageMatch[1])
    return percentage > 0 ? `${percentageMatch[1]}%` : null
  }

  const pixelMatch = LEGACY_POST_EDITOR_IMAGE_PIXEL_PREFIX_PATTERN.exec(normalized)
  if (!pixelMatch) return null

  const numericValue = Number.parseFloat(pixelMatch[1])
  return numericValue > 0 ? `${pixelMatch[1]}px` : null
}
