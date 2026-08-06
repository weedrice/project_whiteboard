import type { BoardCreateData, BoardUpdateData } from '@/types'

export function trimText(value: string | number | null | undefined): string {
  return String(value ?? '').trim()
}

export function optionalTrimmedText(value: string | number | null | undefined): string | undefined {
  const trimmed = trimText(value)
  return trimmed || undefined
}

type BoardWritePayload = BoardCreateData | BoardUpdateData
type BoardWriteTextField = 'boardName' | 'boardUrl' | 'description' | 'iconUrl' | 'guidePrompt'

const BOARD_WRITE_TEXT_FIELDS: BoardWriteTextField[] = [
  'boardName',
  'boardUrl',
  'description',
  'iconUrl',
  'guidePrompt',
]

export function normalizeBoardWritePayload<T extends BoardWritePayload>(
  payload: T,
  options: { isPublic?: boolean } = {},
): T {
  const next: Record<string, unknown> = { ...payload }

  BOARD_WRITE_TEXT_FIELDS.forEach((field) => {
    if (Object.prototype.hasOwnProperty.call(next, field)) {
      next[field] = trimText(next[field] as string | number | null | undefined)
    }
  })

  const isPublic = options.isPublic ?? (typeof next.isPublic === 'boolean' ? next.isPublic : undefined)
  if (isPublic === false) {
    next.isListed = false
    next.agentUseYn = false
  }

  return next as T
}

export interface ConfigWriteInput {
  key?: string | null
  value?: string | null
  description?: string | null
}

export interface ConfigWritePayload {
  key?: string
  value: string
  description?: string
}

export function normalizeConfigWritePayload(
  input: ConfigWriteInput,
  options: { requireKey?: boolean } = {},
): ConfigWritePayload | null {
  const value = trimText(input.value)
  if (!value) return null

  const payload: ConfigWritePayload = { value }

  if (Object.prototype.hasOwnProperty.call(input, 'key')) {
    const key = trimText(input.key)
    if (options.requireKey && !key) return null
    payload.key = key
  } else if (options.requireKey) {
    return null
  }

  if (Object.prototype.hasOwnProperty.call(input, 'description')) {
    payload.description = input.description === undefined ? undefined : trimText(input.description)
  }

  return payload
}
