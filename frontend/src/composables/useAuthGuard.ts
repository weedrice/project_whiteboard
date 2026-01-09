import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { computed } from 'vue'

/**
 * 인증이 필요한 작업을 보호하는 Composable
 * 
 * @example
 * ```typescript
 * const { requireAuth, isAuthenticated } = useAuthGuard()
 * 
 * async function handleAction() {
 *   if (!requireAuth()) return
 *   // 인증된 사용자만 실행되는 코드
 * }
 * ```
 */
export function useAuthGuard() {
    const router = useRouter()
    const authStore = useAuthStore()
    
    const isAuthenticated = computed(() => authStore.isAuthenticated)
    
    /**
     * 인증이 필요한 경우 로그인 페이지로 리다이렉트
     * @param redirectPath 리다이렉트할 경로 (기본값: 현재 경로)
     * @returns 인증 여부
     */
    const requireAuth = (redirectPath?: string): boolean => {
        if (!authStore.isAuthenticated) {
            const path = redirectPath || router.currentRoute.value.fullPath
            router.push({ name: 'login', query: { redirect: path } })
            return false
        }
        return true
    }
    
    return {
        isAuthenticated,
        requireAuth
    }
}
