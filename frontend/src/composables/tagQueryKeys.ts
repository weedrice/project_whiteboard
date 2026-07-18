export const tagQueryKeys = {
  all: ['tags'] as const,
  popular: ['tags', 'popular'] as const,
  posts: (tagName: string, params: Record<string, unknown>) => (
    ['tags', tagName, 'posts', params] as const
  ),
}
