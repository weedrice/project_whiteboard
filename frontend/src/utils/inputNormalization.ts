export function trimText(value: string | number | null | undefined): string {
  return String(value ?? '').trim()
}

export function optionalTrimmedText(value: string | number | null | undefined): string | undefined {
  const trimmed = trimText(value)
  return trimmed || undefined
}
