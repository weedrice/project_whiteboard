import logger from '@/utils/logger'

const REQUIRED_ENV_VARS: string[] = []

export function validateEnv(): void {
    const missing = REQUIRED_ENV_VARS.filter((key) => !import.meta.env[key])

    if (missing.length > 0) {
        logger.warn('Missing required environment variables:', missing)
        if (import.meta.env.PROD) {
            logger.error('Missing required environment variables:', missing)
        }
    }
}
