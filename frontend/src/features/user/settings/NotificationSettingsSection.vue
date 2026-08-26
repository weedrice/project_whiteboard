<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { X } from 'lucide-vue-next'
import { useUser } from '@/features/user/useUser'
import {
  useNotificationSettingsForm,
} from '@/features/user/settings/useUserSettingsForm'
import { usePushNotifications } from '@/features/notifications/usePushNotifications'
import { useAuthStore } from '@/stores/auth'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { isCancellationError } from '@/utils/cancellationError'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const emit = defineEmits<{
  guardState: [state: { dirty: boolean; pending: boolean }]
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const {
  useUserSettings,
  useNotificationSettings,
  useUpdateNotificationSettings,
  useKeywordSubscriptions,
  useCreateKeywordSubscription,
  useDeleteKeywordSubscription,
} = useUser()
const {
  data: settingsData,
  isLoading: isPushSettingLoading,
  isError: isPushSettingError,
  refetch: refetchPushSettings,
} = useUserSettings()
const {
  data: notificationData,
  isLoading: isNotificationLoading,
  isError: isNotificationError,
  refetch: refetchNotifications,
} = useNotificationSettings()
const {
  data: keywordData,
  isLoading: isKeywordLoading,
  isError: isKeywordError,
  refetch: refetchKeywords,
} = useKeywordSubscriptions()
const {
  mutateAsync: updateNotificationSettings,
  isPending: isUpdatingNotifications,
} = useUpdateNotificationSettings()
const {
  mutateAsync: createKeywordSubscription,
  isPending: isCreatingKeyword,
} = useCreateKeywordSubscription()
const {
  mutateAsync: deleteKeywordSubscription,
  isPending: isDeletingKeyword,
} = useDeleteKeywordSubscription()

const {
  availableTypes,
  canSave,
  isDirty,
  isError: saveIsError,
  message,
  save,
  settings,
} = useNotificationSettingsForm({
  notificationData,
  isSaving: isUpdatingNotifications,
  updateNotificationSettings,
  getSessionGeneration: () => authStore.sessionGeneration,
  t,
})

const notificationOptions = computed(() => availableTypes.value.map((type) => ({
  type,
  label: t(`user.settings.notificationTypes.${type}.label`),
  description: t(`user.settings.notificationTypes.${type}.description`),
})))
const keywordInput = ref('')
const keywordMessage = ref('')
const keywordIsError = ref(false)
let keywordOperationRevision = 0
const keywordSubscriptions = computed(() => keywordData.value ?? [])
const normalizedKeyword = computed(() => keywordInput.value.trim())
const keywordNotificationEnabled = computed(() => settings.KEYWORD !== false)
const keywordPending = computed(() => (
  isKeywordLoading.value || isCreatingKeyword.value || isDeletingKeyword.value
))
const keywordValidation = useFieldValidation<'keyword'>({
  validators: {
    keyword: (values) => {
      const value = String(values.keyword ?? '').trim()
      if (!value) return t('user.settings.keywordRequired')
      if (value.length > 50) return t('user.settings.keywordTooLong')
      if (keywordSubscriptions.value.some((subscription) => subscription.keyword === value)) {
        return t('user.settings.keywordDuplicate')
      }
      return ''
    },
  },
  fieldIds: { keyword: 'keyword-subscription-input' },
})
const keywordValidationValues = computed(() => ({ keyword: keywordInput.value }))
const canAddKeyword = computed(() => {
  const keyword = normalizedKeyword.value
  return keyword.length > 0
    && keyword.length <= 50
    && !keywordSubscriptions.value.some((subscription) => subscription.keyword === keyword)
    && !keywordPending.value
})

const pushMessage = ref('')
const pushIsError = ref(false)
const pushNotifications = usePushNotifications(() => Boolean(settingsData.value?.pushEnabled))
const pushPending = computed(() => (
  pushNotifications.isEnabling.value || pushNotifications.isDisabling.value
))
const pushStatusKey = computed(() => {
  if (!pushNotifications.supported.value) return 'user.settings.pushUnsupported'
  if (!pushNotifications.enabled.value) return 'user.settings.pushUnavailable'
  if (pushNotifications.permission.value === 'denied') return 'user.settings.pushDenied'
  if (pushNotifications.syncState.value === 'enabled') return 'user.settings.pushEnabled'
  if (pushNotifications.syncState.value === 'server-only') return 'user.settings.pushServerOnly'
  if (pushNotifications.syncState.value === 'browser-only') return 'user.settings.pushBrowserOnly'
  return 'user.settings.pushDisabled'
})

watch(() => authStore.sessionGeneration, () => {
  keywordOperationRevision += 1
  keywordInput.value = ''
  keywordMessage.value = ''
  keywordIsError.value = false
  keywordValidation.clearValidation()
})

watch(
  [
    isDirty,
    () => keywordInput.value.trim().length > 0,
    isUpdatingNotifications,
    isCreatingKeyword,
  ],
  ([notificationDirty, keywordDirty, updating, creating]) => {
    emit('guardState', {
      dirty: Boolean(notificationDirty || keywordDirty),
      pending: Boolean(updating || creating),
    })
  },
  { immediate: true },
)

function setKeywordMessage(messageKey: string, isError = false) {
  keywordMessage.value = t(messageKey)
  keywordIsError.value = isError
}

async function addKeyword() {
  if (!keywordValidation.validateAll(keywordValidationValues.value)) return
  const keyword = normalizedKeyword.value
  const intent = captureAuthSessionIntent(authStore)
  const operationRevision = ++keywordOperationRevision
  try {
    await createKeywordSubscription({ keyword })
    if (operationRevision !== keywordOperationRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    keywordInput.value = ''
    keywordValidation.clearValidation()
    setKeywordMessage('user.settings.keywordAdded')
  } catch {
    if (operationRevision !== keywordOperationRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    setKeywordMessage('user.settings.keywordAddFailed', true)
  }
}

async function removeKeyword(keyword: string) {
  const intent = captureAuthSessionIntent(authStore)
  const operationRevision = ++keywordOperationRevision
  try {
    await deleteKeywordSubscription({ keyword })
    if (operationRevision !== keywordOperationRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    setKeywordMessage('user.settings.keywordRemoved')
  } catch {
    if (operationRevision !== keywordOperationRevision || !isAuthSessionIntentCurrent(authStore, intent)) return
    setKeywordMessage('user.settings.keywordRemoveFailed', true)
  }
}

function setPushMessage(messageKey: string, isError = false) {
  pushMessage.value = t(messageKey)
  pushIsError.value = isError
}

async function enableBrowserPush() {
  try {
    await pushNotifications.enablePush()
    setPushMessage('user.settings.pushEnableSuccess')
  } catch (error) {
    if (isCancellationError(error)) return
    setPushMessage('user.settings.pushEnableFailed', true)
  }
}

async function disableBrowserPush() {
  try {
    await pushNotifications.disablePush()
    setPushMessage('user.settings.pushDisableSuccess')
  } catch (error) {
    if (isCancellationError(error)) return
    setPushMessage('user.settings.pushDisableFailed', true)
  }
}
</script>

<template>
  <section id="notifications" role="tabpanel" aria-labelledby="settings-notifications-tab">
    <div v-if="isNotificationLoading" class="py-10 text-center">
      <BaseSpinner />
    </div>
    <ErrorState
      v-else-if="isNotificationError"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetchNotifications()"
    />
    <template v-else>
      <h2 id="settings-notifications-heading" class="text-lg font-medium leading-6 nv-title">
        {{ $t('user.settings.notifications') }}
      </h2>
      <div class="mt-4 space-y-4">
        <BaseCheckbox
          v-for="option in notificationOptions"
          :id="`notification-${option.type.toLowerCase()}`"
          :key="option.type"
          v-model="settings[option.type]"
          :label="option.label"
          :description="option.description"
        />
      </div>
      <div class="mt-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-end">
        <p
          v-if="message"
          class="flex items-center text-sm"
          :role="saveIsError ? 'alert' : 'status'"
          :aria-live="saveIsError ? undefined : 'polite'"
          :class="saveIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
        >
          {{ message }}
        </p>
        <BaseButton @click="save" :loading="isUpdatingNotifications" :disabled="!canSave">
          {{ isUpdatingNotifications ? $t('user.settings.saving') : $t('user.settings.save') }}
        </BaseButton>
      </div>

      <div class="mt-8 border-t nv-border pt-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-base font-semibold nv-title">{{ $t('user.settings.keywordSubscriptions') }}</h3>
          <p class="text-sm nv-text-subtle">{{ $t('user.settings.keywordSubscriptionsDesc') }}</p>
          <p
            v-if="!keywordNotificationEnabled"
            class="text-sm text-[var(--nv-warning-text)]"
            role="status"
            aria-live="polite"
          >
            {{ $t('user.settings.keywordNotificationsDisabled') }}
          </p>
        </div>

        <form class="mt-4 flex flex-col gap-3 sm:flex-row" @submit.prevent="addKeyword">
          <BaseInput
            id="keyword-subscription-input"
            v-model="keywordInput"
            class="flex-1"
            :label="$t('user.settings.keywordInput')"
            :placeholder="$t('user.settings.keywordPlaceholder')"
            :disabled="keywordPending"
            maxlength="50"
            :error="keywordValidation.visibleError('keyword')"
            @blur="keywordValidation.touchField('keyword', keywordValidationValues)"
          />
          <BaseButton type="submit" :loading="isCreatingKeyword" :disabled="!canAddKeyword">
            {{ $t('user.settings.keywordAdd') }}
          </BaseButton>
        </form>

        <p
          v-if="keywordMessage"
          class="mt-3 text-sm"
          :role="keywordIsError ? 'alert' : 'status'"
          :aria-live="keywordIsError ? undefined : 'polite'"
          :class="keywordIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
        >
          {{ keywordMessage }}
        </p>

        <div v-if="isKeywordLoading" class="mt-4">
          <BaseSpinner />
        </div>
        <ErrorState
          v-else-if="isKeywordError"
          title-tag="h3"
          :message="$t('common.messages.loadFailed')"
          show-retry
          @retry="refetchKeywords()"
        />
        <p v-else-if="keywordSubscriptions.length === 0" class="mt-4 text-sm nv-text-subtle">
          {{ $t('user.settings.keywordEmpty') }}
        </p>
        <ul v-else class="mt-4 flex flex-wrap gap-2" :aria-label="$t('user.settings.keywordList')">
          <li
            v-for="subscription in keywordSubscriptions"
            :key="subscription.subscriptionId"
            class="inline-flex items-center gap-2 rounded-full border nv-border px-3 py-1.5 text-sm nv-text"
          >
            <span>{{ subscription.keyword }}</span>
            <button
              type="button"
              class="nv-touch-target-square nv-focus-ring -my-2 -mr-2 inline-flex shrink-0 items-center justify-center rounded-full nv-text-subtle hover:text-[var(--nv-danger-text)]"
              :aria-label="$t('user.settings.keywordRemove', { keyword: subscription.keyword })"
              :disabled="isDeletingKeyword"
              @click="removeKeyword(subscription.keyword)"
            >
              <X class="h-4 w-4" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </div>

      <div class="mt-8 border-t nv-border pt-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-base font-semibold nv-title">{{ $t('user.settings.browserPush') }}</h3>
          <p class="text-sm nv-text-subtle">{{ $t('user.settings.browserPushDesc') }}</p>
          <p
            v-if="!isPushSettingLoading && !isPushSettingError && !pushNotifications.isLoading.value && !pushNotifications.isError.value"
            class="text-sm nv-text-subtle"
            role="status"
            aria-live="polite"
          >
            {{ $t(pushStatusKey) }}
          </p>
        </div>
        <div
          v-if="isPushSettingLoading || pushNotifications.isLoading.value"
          class="mt-4"
          role="status"
          aria-live="polite"
          aria-busy="true"
        >
          <BaseSpinner />
        </div>
        <ErrorState
          v-else-if="isPushSettingError || pushNotifications.isError.value"
          title-tag="h4"
          :message="$t('user.settings.pushLoadFailed')"
          :show-icon="false"
          auto-focus
          show-retry
          @retry="isPushSettingError ? refetchPushSettings() : pushNotifications.refetch()"
        />
        <div v-else class="mt-4 flex flex-wrap gap-2">
          <BaseButton
            type="button"
            :loading="pushNotifications.isEnabling.value"
            :disabled="pushPending || pushNotifications.syncState.value === 'enabled' || !pushNotifications.supported.value || !pushNotifications.enabled.value"
            @click="enableBrowserPush"
          >
            {{ pushNotifications.syncState.value === 'disabled'
              ? $t('user.settings.pushEnable')
              : $t('user.settings.pushRepair') }}
          </BaseButton>
          <BaseButton
            type="button"
            variant="secondary"
            :loading="pushNotifications.isDisabling.value"
            :disabled="pushPending || pushNotifications.syncState.value === 'disabled'"
            @click="disableBrowserPush"
          >
            {{ $t('user.settings.pushDisable') }}
          </BaseButton>
        </div>
        <p
          v-if="pushMessage"
          class="mt-3 text-sm"
          :role="pushIsError ? 'alert' : 'status'"
          :aria-live="pushIsError ? undefined : 'polite'"
          :class="pushIsError ? 'nv-form-error' : 'text-[var(--nv-success-text)]'"
        >
          {{ pushMessage }}
        </p>
      </div>
    </template>
  </section>
</template>
