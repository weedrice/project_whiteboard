export const POST_DRAFT_BOARD_URL_MAX_LENGTH = 100
export const POST_DRAFT_CLIENT_IDENTIFIER_PATTERN = /^[A-Za-z0-9_-]{8,64}$/
const POST_DRAFT_BOARD_URL_PATTERN = /^[a-z0-9_-]+$/

export function isValidDraftBoardUrl(value: string): boolean {
  return value.length <= POST_DRAFT_BOARD_URL_MAX_LENGTH
    && POST_DRAFT_BOARD_URL_PATTERN.test(value)
}

export function isValidDraftClientIdentifier(value: string): boolean {
  return POST_DRAFT_CLIENT_IDENTIFIER_PATTERN.test(value)
}

export function normalizeDraftClientIdentifier(value: unknown): string | undefined {
  return typeof value === 'string' && isValidDraftClientIdentifier(value) ? value : undefined
}
