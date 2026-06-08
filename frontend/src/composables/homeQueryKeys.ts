import type { HomeLandingPeriod } from '@/types'

export const homeQueryKeys = {
    all: ['home'] as const,
    landingRoot: ['home', 'landing'] as const,
    landing: (period: HomeLandingPeriod, authKey: string | number) =>
        ['home', 'landing', period, authKey] as const,
}
