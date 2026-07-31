<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUser } from '@/features/user/useUser'
import { useUserSettingsForm } from '@/features/user/settings/useUserSettingsForm'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { setAppLocale } from '@/i18n'
import {
  AUTO_TIME_ZONE,
  SERVER_TIME_ZONE,
  detectBrowserTimeZone,
} from '@/utils/displayTimeZone'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const emit = defineEmits<{
  guardState: [state: { dirty: boolean; pending: boolean }]
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const {
  useUserSettings,
  useUpdateUserSettings,
} = useUser()
const {
  data: settingsData,
  isLoading,
  isError,
  refetch,
} = useUserSettings()
const {
  mutateAsync: updateSettings,
  isPending: isUpdatingSettings,
} = useUpdateUserSettings()

const {
  canSave,
  form,
  isDirty,
  isError: saveIsError,
  message,
  save,
} = useUserSettingsForm({
  settingsData,
  isSaving: isUpdatingSettings,
  themeIsDark: () => themeStore.isDark,
  updateSettings,
  setTheme: themeStore.setTheme,
  setLocale: setAppLocale,
  getSessionGeneration: () => authStore.sessionGeneration,
  reloadSettings: async () => { await refetch() },
  t,
})

const selectableTimeZones = computed(() => {
  const zones = new Set<string>(supportedTimeZones())
  zones.add(SERVER_TIME_ZONE)
  const detected = detectBrowserTimeZone()
  if (detected) zones.add(detected)
  if (form.timezone && form.timezone !== AUTO_TIME_ZONE) zones.add(form.timezone)
  return [...zones].sort()
})

function supportedTimeZones(): string[] {
  try {
    return Intl.supportedValuesOf?.('timeZone') ?? []
  } catch {
    return []
  }
}

watch(
  [isDirty, isUpdatingSettings],
  ([dirty, pending]) => emit('guardState', { dirty, pending }),
  { immediate: true },
)
</script>

<template>
  <section id="general" role="tabpanel" aria-labelledby="settings-general-tab">
    <div v-if="isLoading" class="py-10 text-center">
      <BaseSpinner />
    </div>
    <ErrorState
      v-else-if="isError"
      title-tag="h2"
      :message="$t('common.messages.loadFailed')"
      show-retry
      @retry="refetch()"
    />
    <template v-else>
      <h2 id="settings-general-heading" class="text-lg font-medium leading-6 nv-title">
        {{ $t('user.settings.general') }}
      </h2>
      <div class="mt-4 space-y-4">
        <BaseSelect v-model="form.theme" :label="$t('user.settings.theme')">
          <option value="LIGHT">{{ $t('user.settings.light') }}</option>
          <option value="DARK">{{ $t('user.settings.dark') }}</option>
        </BaseSelect>

        <BaseSelect v-model="form.language" :label="$t('user.settings.language')">
          <option value="ko">{{ $t('common.languages.ko') }}</option>
          <option value="en">{{ $t('common.languages.en') }}</option>
        </BaseSelect>

        <div>
          <BaseSelect
            v-model="form.timezone"
            :label="$t('user.settings.timezone')"
            aria-describedby="settings-timezone-help"
          >
            <option :value="AUTO_TIME_ZONE">{{ $t('user.settings.timezoneAuto') }}</option>
            <option v-for="zone in selectableTimeZones" :key="zone" :value="zone">{{ zone }}</option>
          </BaseSelect>
          <p id="settings-timezone-help" class="mt-1 text-sm nv-muted">
            {{ $t('user.settings.timezoneHelp') }}
          </p>
        </div>
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
        <BaseButton @click="save" :loading="isUpdatingSettings" :disabled="!canSave">
          {{ isUpdatingSettings ? $t('user.settings.saving') : $t('user.settings.save') }}
        </BaseButton>
      </div>
    </template>
  </section>
</template>
