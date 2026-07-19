import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/features/admin/useAdmin'
import { useConfigEditor } from '@/features/admin/settings/useConfigEditor'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { normalizeConfigWritePayload } from '@/utils/inputNormalization'
import {
  EMOTICON_IMAGE_MAX_COUNT_KEY,
  useEmoticonImagePolicy,
} from '@/features/emoticon/form/useEmoticonImagePolicy'

function createEmptyConfigForm() {
  return {
    key: '',
    value: '',
    description: '',
  }
}

export function useGlobalSettingsManager() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const authStore = useAuthStore()
  const { confirm } = useConfirm()
  const { refresh: refreshEmoticonImagePolicy } = useEmoticonImagePolicy()
  const { useConfigs, useUpdateConfig, useCreateConfig, useDeleteConfig } = useAdmin()

  const isModalOpen = ref(false)
  const isCreatingConfig = ref(false)
  const newConfig = reactive(createEmptyConfigForm())
  let modalRevision = 0

  const { data: configsData, isLoading } = useConfigs()
  const { mutateAsync: updateConfig } = useUpdateConfig()
  const { mutateAsync: createConfig } = useCreateConfig()
  const { mutateAsync: deleteConfig } = useDeleteConfig()
  const { configs, hasUnsavedChanges: hasUnsavedConfigChanges, updateDraft, getDraft } = useConfigEditor(configsData)
  const hasUnsavedChanges = computed(() => hasUnsavedConfigChanges.value || (
    isModalOpen.value
    && Object.values(newConfig).some((value) => value.trim().length > 0)
  ))

  function openCreateModal() {
    modalRevision += 1
    isCreatingConfig.value = false
    resetNewConfig()
    isModalOpen.value = true
  }

  function closeCreateModal() {
    modalRevision += 1
    isCreatingConfig.value = false
    isModalOpen.value = false
    resetNewConfig()
  }

  function resetNewConfig() {
    Object.assign(newConfig, createEmptyConfigForm())
  }

  watch(() => authStore.sessionGeneration, closeCreateModal, { flush: 'sync' })

  async function handleSave(key: string) {
    const config = getDraft(key)
    if (!config) return

    const payload = normalizeConfigWritePayload(config)
    if (!payload) return
    const intent = captureAuthSessionIntent(authStore)

    try {
      await updateConfig({ key: config.key, value: payload.value, description: payload.description })
      if (!isAuthSessionIntentCurrent(authStore, intent)) return
      if (config.key === EMOTICON_IMAGE_MAX_COUNT_KEY) {
        await refreshEmoticonImagePolicy()
        if (!isAuthSessionIntentCurrent(authStore, intent)) return
      }
      toastStore.addToast(t('admin.settings.messages.saved'), 'success')
    } catch {
      // Error handled globally
    }
  }

  async function handleCreateConfig() {
    if (isCreatingConfig.value || !isModalOpen.value) return
    const payload = normalizeConfigWritePayload(newConfig, { requireKey: true })
    if (!payload?.key) return

    const submittedRevision = modalRevision
    const sessionGeneration = authStore.sessionGeneration
    isCreatingConfig.value = true
    try {
      await createConfig({
        key: payload.key,
        value: payload.value,
        description: payload.description,
      })
      if (
        submittedRevision !== modalRevision
        || sessionGeneration !== authStore.sessionGeneration
        || !isModalOpen.value
      ) return
      if (payload.key === EMOTICON_IMAGE_MAX_COUNT_KEY) {
        await refreshEmoticonImagePolicy()
      }

      if (
        submittedRevision !== modalRevision
        || sessionGeneration !== authStore.sessionGeneration
        || !isModalOpen.value
      ) return

      toastStore.addToast(t('admin.settings.messages.saved'), 'success')
      closeCreateModal()
    } catch {
      // Error handled globally
    } finally {
      if (submittedRevision === modalRevision) isCreatingConfig.value = false
    }
  }

  async function handleDelete(key: string) {
    const targetKey = configs.value.find((config) => config.key === key)?.key
    if (!targetKey) return
    const intent = captureAuthSessionIntent(authStore)
    const isConfirmed = await confirm(t('common.confirmDelete'))
    if (!isConfirmed) return
    if (
      !isAuthSessionIntentCurrent(authStore, intent)
      || !configs.value.some((config) => config.key === targetKey)
    ) return

    try {
      await deleteConfig(targetKey)
      if (!isAuthSessionIntentCurrent(authStore, intent)) return
      if (targetKey === EMOTICON_IMAGE_MAX_COUNT_KEY) {
        await refreshEmoticonImagePolicy()
        if (!isAuthSessionIntentCurrent(authStore, intent)) return
      }
      toastStore.addToast(t('common.deleted'), 'success')
    } catch {
      // Error handled globally
    }
  }

  return {
    closeCreateModal,
    configs,
    handleCreateConfig,
    handleDelete,
    handleSave,
    hasUnsavedChanges,
    isLoading,
    isCreatingConfig,
    isModalOpen,
    newConfig,
    openCreateModal,
    updateDraft,
  }
}
