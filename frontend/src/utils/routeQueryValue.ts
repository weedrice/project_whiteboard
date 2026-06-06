export function firstQueryValue(value: unknown): string {
  if (Array.isArray(value)) {
    return String(value[0] ?? '')
  }
  return String(value ?? '')
}

export function getSingleQueryValue(value: unknown): string | null {
  if (typeof value === 'string' && value.length > 0) return value
  if (Array.isArray(value) && typeof value[0] === 'string' && value[0].length > 0) return value[0]
  return null
}
