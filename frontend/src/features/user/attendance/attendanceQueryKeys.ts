export const attendanceQueryKeys = {
    all: ['attendance'] as const,
    meRoot: ['attendance', 'me'] as const,
    me: (month: string | undefined, today: string) => ['attendance', 'me', month ?? 'current', today] as const,
}
