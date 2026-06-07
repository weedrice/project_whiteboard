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

export function parsePageFromQuery(value: unknown): number {
  const parsed = Number.parseInt(String(value ?? '1'), 10)
  if (Number.isNaN(parsed) || parsed < 1) {
    return 0
  }
  return parsed - 1
}

export function parseCategoryIdFromQuery(value: unknown): number | null {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (Number.isNaN(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

export function parseConceptFromQuery(value: unknown): boolean {
  const raw = String(value ?? '').trim().toLowerCase()
  return raw === '1' || raw === 'true'
}

function areQueryValuesEqual(left: unknown, right: unknown): boolean {
  if (Array.isArray(left) || Array.isArray(right)) {
    const leftValues = Array.isArray(left) ? left.map(String) : [String(left ?? '')]
    const rightValues = Array.isArray(right) ? right.map(String) : [String(right ?? '')]
    return leftValues.length === rightValues.length
      && leftValues.every((value, index) => value === rightValues[index])
  }

  return String(left ?? '') === String(right ?? '')
}

export function areQueriesEqual(
  left: Record<string, unknown>,
  right: Record<string, unknown>
): boolean {
  const leftKeys = Object.keys(left).sort()
  const rightKeys = Object.keys(right).sort()

  if (leftKeys.length !== rightKeys.length) {
    return false
  }

  return leftKeys.every((key, index) => (
    key === rightKeys[index]
    && areQueryValuesEqual(left[key], right[key])
  ))
}
