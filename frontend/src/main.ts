import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createHead } from '@unhead/vue/client'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './style.css'
import './styles/foundation.css'
import './styles/components.css'

import { configureApiLocaleResolver, configureApiStoreResolvers } from '@/api'
import { queryClient, configureQueryClientStoreResolvers } from '@/queryClient'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { useToastStore } from '@/stores/toast'
import { validateEnv } from '@/utils/env'
import { installClientErrorReporting } from '@/utils/clientErrorReporter'
import { registerPwaAutoUpdate } from '@/pwa'
import { applyStandaloneDisplayModeClass } from '@/pwaDisplayMode'
import { clearAuthScopedQueries, configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { resetNotificationStreamSessionState } from '@/features/notifications/stream/notificationStreamController'
import { clearUserTimeZone } from '@/utils/displayTimeZone'
import {
    clearStoredDraftSnapshotsForUser,
    countUnsyncedStoredDraftSnapshotsForUser,
} from '@/features/board/posts/draft/postDraftLifecycle'
import { clearDraftTombstonesForUser } from '@/features/board/posts/draft/postDraftTombstone'

validateEnv()
applyStandaloneDisplayModeClass()

const app = createApp(App)
const head = createHead()
const pinia = createPinia()

app.use(pinia)
app.use(head)

configureApiStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
    resolveAuthStore: () => useAuthStore(pinia),
})

configureApiLocaleResolver(() => i18n.global.locale.value)

configureQueryClientStoreResolvers({
    resolveToastStore: () => useToastStore(pinia),
})

configureAuthSessionEffects({
    syncThemeFromUser: (userData) => {
        if (userData?.theme) {
            useThemeStore(pinia).setTheme(userData.theme)
        }
    },
    onPrincipalChange: () => {
        // 저장된 표시 시간대는 계정에 딸린 설정이다. 공용 기기에서 다음 사용자가
        // 앞 사용자의 지역으로 시각을 보지 않도록 주체가 바뀔 때 비운다.
        //
        // 세션 경계(onSessionBoundary)가 아니라 주체 변경에 걸어야 한다. 경계는 같은
        // 사용자의 부트스트랩·토큰 갱신에도 발생하므로, 거기에 걸면 새로고침마다
        // 지워진 뒤 설정 응답이 오기 전까지 브라우저 지역으로 그려진다.
        clearUserTimeZone()
    },
    onExplicitLogout: (userId) => {
        if (userId == null) return
        const unsyncedDraftCount = countUnsyncedStoredDraftSnapshotsForUser(userId)
        const confirmation = i18n.global.t('board.writePost.draftStatus.confirmLogoutWithUnsynced', {
            count: unsyncedDraftCount,
        })
        if (unsyncedDraftCount > 0 && !window.confirm(confirmation)) {
            return false
        }
        clearStoredDraftSnapshotsForUser(userId)
        clearDraftTombstonesForUser(userId)
        return true
    },
    onSessionBoundary: (generation) => {
        notifyAuthSessionBoundary(generation)
        resetNotificationStreamSessionState()
        clearAuthScopedQueries(queryClient)
    },
})

configureAuthQueryScope(() => useAuthStore(pinia).sessionGeneration)

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.use(i18n)

installClientErrorReporting(app, {
    onVueError: () => {
        useToastStore(pinia).addToast(i18n.global.t('common.error.unknown'), 'error')
    },
})

app.mount('#app')
registerPwaAutoUpdate(pinia, i18n.global.t)

if (import.meta.env.PROD) {
    import('@/utils/performance').then(({ reportWebVitals, logMetric }) => {
        reportWebVitals(logMetric)
    }).catch(() => {
        // Ignore optional web-vitals loading failures.
    })
}
