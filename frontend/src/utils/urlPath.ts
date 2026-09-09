export function encodePathSegment(value: string | number): string {
  return encodeURIComponent(String(value))
}

export function buildPostDetailPath(
  boardUrl: string | number,
  postId: string | number,
  hash = '',
): string {
  return `/board/${encodePathSegment(boardUrl)}/post/${encodePathSegment(postId)}/${hash}`
}

const postDetailPathPattern = /^(\/board\/[^/?#]+\/post\/[^/?#]+)\/?([?#].*)?$/

export function normalizePostDetailPath(path: string): string {
  const match = postDetailPathPattern.exec(path)
  if (!match) return path

  return `${match[1]}/${match[2] ?? ''}`
}
