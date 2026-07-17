import i18n from '@/i18n'
import router from '@/router'
import { API_PATHS } from '@/api/apiPaths'
import { isLoginPathname } from '@/api/apiAuthHeader'
import { resolveToastStore } from '@/api/apiStoreResolvers'
import {
  clearExpiredAuthSession,
  notifySessionExpired,
  type AuthStoreLike,
} from '@/api/authRefreshSession'

/**
 * Applies the one terminal refresh-failure policy shared by Axios and SSE.
 * Transport failures and server errors are retryable and must preserve the session.
 */
export async function handleTerminalAuthFailure(
  status: number | null,
  authStore: AuthStoreLike | null,
  expected?: { generation: number, accessToken: string | null },
): Promise<boolean> {
  if (status !== 401 && status !== 403) return false
  if (authStore && expected && (
    authStore.sessionGeneration !== expected.generation
    || authStore.accessToken !== expected.accessToken
  )) return false

  const redirect = router.currentRoute.value.fullPath
  const shouldRedirect = !isLoginPathname() && Boolean(router.currentRoute.value.meta.requiresAuth)
  clearExpiredAuthSession(authStore)

  if (shouldRedirect) {
    const toastStore = await resolveToastStore()
    notifySessionExpired(toastStore, i18n.global.t('common.messages.sessionExpired'))
    await router.replace({
      path: API_PATHS.LOGIN,
      query: { redirect },
    })
  }
  return true
}
