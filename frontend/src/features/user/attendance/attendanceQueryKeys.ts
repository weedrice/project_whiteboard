export const attendanceQueryKeys = {
    all: ['attendance'] as const,
    meRoot: ['attendance', 'me'] as const,
    me: (month?: string) => ['attendance', 'me', month ?? 'current'] as const,
}
