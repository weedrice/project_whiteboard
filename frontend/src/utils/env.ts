import logger from '@/utils/logger'

/**
 * 환경 변수 검증 및 설정
 */

interface EnvConfig {
    VITE_API_URL?: string
    VITE_API_BASE_URL?: string
    [key: string]: string | undefined
}

/**
 * 필수 환경 변수 목록
 */
const REQUIRED_ENV_VARS: string[] = [
    // 현재는 필수 환경 변수가 없음 (모두 기본값이 있음)
    // 필요시 여기에 추가
]

/**
 * 환경 변수를 검증합니다.
 * 필수 환경 변수가 없으면 경고를 표시합니다.
 */
export function validateEnv(): void {
    const missing: string[] = []

    REQUIRED_ENV_VARS.forEach((key) => {
        if (!import.meta.env[key]) {
            missing.push(key)
        }
    })

    if (missing.length > 0) {
        logger.warn('Missing required environment variables:', missing)
        if (import.meta.env.PROD) {
            console.error('Missing required environment variables:', missing)
        }
    }
}

/**
 * 환경 변수를 안전하게 가져옵니다.
 * 
 * @param key 환경 변수 키
 * @param defaultValue 기본값
 * @returns 환경 변수 값 또는 기본값
 */
export function getEnv(key: string, defaultValue: string = ''): string {
    return import.meta.env[key] || defaultValue
}

/**
 * 모든 환경 변수를 가져옵니다.
 * 
 * @returns 환경 변수 객체
 */
export function getAllEnv(): EnvConfig {
    return import.meta.env as EnvConfig
}
