export const feedQueryKeys = {
  all: ['feed'] as const,
  personal: (userId: string | number) => ['feed', 'personal', userId] as const,
}
