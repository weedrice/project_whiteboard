const integerFormatter = new Intl.NumberFormat()

export function formatInteger(value: number | null | undefined) {
  return integerFormatter.format(value ?? 0)
}
