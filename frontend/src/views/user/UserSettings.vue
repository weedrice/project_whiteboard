<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Settings } from 'lucide-vue-next'
import { useConfirm } from '@/composables/useConfirm'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import GeneralSettingsSection from '@/features/user/settings/GeneralSettingsSection.vue'
import NotificationSettingsSection from '@/features/user/settings/NotificationSettingsSection.vue'
import SecuritySettingsSection from '@/features/user/settings/SecuritySettingsSection.vue'
import BaseSegmentedControl from '@/components/common/ui/BaseSegmentedControl.vue'
import PageHeader from '@/components/common/ui/PageHeader.vue'

type SettingsSection = 'general' | 'notifications' | 'security'
interface GuardState {
  dirty: boolean
  pending: boolean
}

const SETTINGS_SECTIONS: SettingsSection[] = ['general', 'notifications', 'security']
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { confirm } = useConfirm()
const activeSection = ref<SettingsSection>('general')
const guardStates = reactive<Record<'general' | 'notifications', GuardState>>({
  general: { dirty: false, pending: false },
  notifications: { dirty: false, pending: false },
})
const settingsSectionOptions = computed(() => [
  { value: 'general', label: t('user.settings.general'), id: 'settings-general-tab', controls: 'general' },
  { value: 'notifications', label: t('user.settings.notifications'), id: 'settings-notifications-tab', controls: 'notifications' },
  { value: 'security', label: t('user.settings.sessions.title'), id: 'settings-security-tab', controls: 'security' },
])
const hasUnsavedSettingsChanges = computed(() => (
  guardStates.general.dirty || guardStates.notifications.dirty
))
const isSettingsSavePending = computed(() => (
  guardStates.general.pending || guardStates.notifications.pending
))

watch(() => route.hash, (hash) => {
  const requested = hash.replace(/^#/, '') as SettingsSection
  activeSection.value = SETTINGS_SECTIONS.includes(requested) ? requested : 'general'
}, { immediate: true })

function selectSection(section: string) {
  if (!SETTINGS_SECTIONS.includes(section as SettingsSection)) return
  activeSection.value = section as SettingsSection
  void router.replace({ hash: `#${section}` })
}

function updateGuardState(section: 'general' | 'notifications', state: GuardState) {
  guardStates[section] = state
}

useUnsavedChangesGuard(
  hasUnsavedSettingsChanges,
  isSettingsSavePending,
  () => t('user.settings.leaveConfirm'),
  confirm,
)
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <div class="nv-surface nv-elevated-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <PageHeader :title="$t('common.settings')" size="compact" class="border-b nv-border px-4 py-5 sm:px-6">
        <template #icon>
          <Settings class="h-5 w-5 shrink-0 nv-text-subtle" aria-hidden="true" />
        </template>
      </PageHeader>
      <div class="space-y-6 px-4 py-5 sm:p-6">
        <BaseSegmentedControl
          :model-value="activeSection"
          :options="settingsSectionOptions"
          :label="$t('user.settings.sectionNavigation')"
          selection-mode="tab"
          variant="joined"
          @update:model-value="selectSection"
        />

        <GeneralSettingsSection
          v-show="activeSection === 'general'"
          @guard-state="updateGuardState('general', $event)"
        />
        <NotificationSettingsSection
          v-show="activeSection === 'notifications'"
          @guard-state="updateGuardState('notifications', $event)"
        />
        <SecuritySettingsSection v-show="activeSection === 'security'" />
      </div>
    </div>
  </div>
</template>
