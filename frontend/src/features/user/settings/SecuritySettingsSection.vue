<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Monitor } from 'lucide-vue-next'
import { useUser } from '@/features/user/useUser'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { formatDateTimeOrDash } from '@/utils/date'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const {
  useMySessions,
  useMyLoginHistory,
  useRevokeMySession,
  useRevokeOtherSessions,
} = useUser()
const loginHistoryParams = ref({ page: 0, size: 10 })
const showLoginHistory = ref(false)
const {
  data: sessionsData,
  isLoading: isSessionsLoading,
  isError: isSessionsError,
  refetch: refetchSessions,
} = useMySessions()
const {
  data: loginHistoryData,
  isLoading: isLoginHistoryLoading,
  isError: isLoginHistoryError,
  refetch: refetchLoginHistory,
} = useMyLoginHistory(loginHistoryParams)
const {
  mutateAsync: revokeSession,
  isPending: isRevokingSession,
} = useRevokeMySession()
const {
  mutateAsync: revokeOtherSessions,
  isPending: isRevokingOtherSessions,
} = useRevokeOtherSessions()

const sessions = computed(() => sessionsData.value ?? [])
const loginHistory = computed(() => loginHistoryData.value?.content ?? [])
const currentLocale = computed(() => locale.value as 'ko' | 'en')

async function handleRevokeSession(session: { sessionId: number; current: boolean }) {
  const intent = captureAuthSessionIntent(authStore)
  const ok = await confirm(
    session.current ? t('user.settings.sessions.confirmCurrent') : t('user.settings.sessions.confirmOne'),
    t('user.settings.sessions.confirmTitle'),
  )
  if (!ok || !isAuthSessionIntentCurrent(authStore, intent)) return

  try {
    await revokeSession(session.sessionId)
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('user.settings.sessions.revoked'), 'success')
    if (session.current) authStore.clearSessionState()
  } catch {
    // QueryClient owns the error toast; consume the event-handler rejection.
  }
}

async function handleRevokeOtherSessions() {
  const intent = captureAuthSessionIntent(authStore)
  const ok = await confirm(
    t('user.settings.sessions.confirmOthers'),
    t('user.settings.sessions.confirmTitle'),
  )
  if (!ok || !isAuthSessionIntentCurrent(authStore, intent)) return

  try {
    await revokeOtherSessions()
    if (!isAuthSessionIntentCurrent(authStore, intent)) return
    toastStore.addToast(t('user.settings.sessions.revokedOthers'), 'success')
  } catch {
    // QueryClient owns the error toast; consume the event-handler rejection.
  }
}
</script>

<template>
  <section id="security" role="tabpanel" aria-labelledby="settings-security-tab">
    <div v-if="isSessionsLoading" class="py-10 text-center">
      <BaseSpinner />
    </div>
    <ErrorState
      v-else-if="isSessionsError"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetchSessions()"
    />
    <div v-else>
      <div class="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 id="settings-security-heading" class="text-lg font-semibold nv-title">
            {{ $t('user.settings.sessions.title') }}
          </h2>
          <p class="text-sm nv-text-subtle">{{ $t('user.settings.sessions.description') }}</p>
        </div>
        <BaseButton
          class="w-full sm:w-auto"
          size="sm"
          variant="secondary"
          :loading="isRevokingOtherSessions"
          :disabled="sessions.length <= 1 || isRevokingSession"
          @click="handleRevokeOtherSessions"
        >
          {{ $t('user.settings.sessions.logoutOthers') }}
        </BaseButton>
      </div>
      <div class="mt-4 space-y-3">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          class="rounded-[var(--nv-radius-sm)] border nv-border p-3"
        >
          <div class="flex flex-col items-start gap-3 sm:flex-row sm:justify-between">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <Monitor class="h-4 w-4 nv-text-subtle" />
                <p class="font-medium nv-title">{{ session.deviceSummary }}</p>
                <span
                  v-if="session.current"
                  class="rounded-full bg-[var(--nv-accent-bg)] px-2 py-0.5 text-xs nv-accent-text"
                >
                  {{ $t('user.settings.sessions.current') }}
                </span>
              </div>
              <p class="mt-1 text-sm nv-text-subtle">
                {{ session.ipAddress }} · {{ formatDateTimeOrDash(session.lastUsedAt, currentLocale) }}
              </p>
            </div>
            <BaseButton
              size="sm"
              variant="danger"
              :loading="isRevokingSession"
              @click="handleRevokeSession(session)"
            >
              {{ $t('common.logout') }}
            </BaseButton>
          </div>
        </div>
        <p v-if="sessions.length === 0" class="text-sm nv-text-subtle">
          {{ $t('user.settings.sessions.empty') }}
        </p>
      </div>

      <button
        type="button"
        class="nv-touch-target nv-focus-ring mt-4 inline-flex items-center rounded-md px-2 text-sm font-medium nv-accent-text"
        aria-controls="settings-login-history"
        :aria-expanded="showLoginHistory"
        @click="showLoginHistory = !showLoginHistory"
      >
        {{ showLoginHistory ? $t('user.settings.sessions.hideHistory') : $t('user.settings.sessions.showHistory') }}
      </button>
      <div v-if="showLoginHistory" id="settings-login-history" class="mt-3 space-y-2">
        <BaseSpinner v-if="isLoginHistoryLoading" />
        <ErrorState
          v-else-if="isLoginHistoryError"
          title-tag="h4"
          :message="$t('common.messages.loadFailed')"
          show-retry
          @retry="refetchLoginHistory()"
        />
        <template v-else>
          <div
            v-for="history in loginHistory"
            :key="history.historyId"
            class="flex flex-col gap-1 rounded-[var(--nv-radius-sm)] border nv-border px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between"
          >
            <span class="truncate nv-title">{{ history.deviceSummary }}</span>
            <span class="shrink-0 nv-text-subtle">
              {{ history.ipAddress }} · {{ formatDateTimeOrDash(history.createdAt, currentLocale) }}
            </span>
          </div>
          <p v-if="loginHistory.length === 0" class="text-sm nv-text-subtle">
            {{ $t('user.settings.sessions.historyEmpty') }}
          </p>
        </template>
      </div>
    </div>
  </section>
</template>
