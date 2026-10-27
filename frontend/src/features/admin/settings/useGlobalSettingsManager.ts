import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/features/admin/useAdmin'
import { useConfigEditor } from '@/features/admin/settings/useConfigEditor'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { normalizeConfigWritePayload } from '@/utils/inputNormalization'
import { unwrapAxiosApiData } from '@/api/response'
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
  const operations = useAuthScopedOperation(authStore)
  const isCreatingConfig = computed(() => operations.isPending('create'))
  const newConfig = reactive(createEmptyConfigForm())
  let modalRevision = 0

  const { data: configsData, isLoading } = useConfigs()
  const { mutateAsync: updateConfig } = useUpdateConfig()
  const { mutateAsync: createConfig } = useCreateConfig()
  const { mutateAsync: deleteConfig } = useDeleteConfig()
  const { configs, hasUnsavedChanges: hasUnsavedConfigChanges, updateDraft, captureSave, resetDrafts } = useConfigEditor(configsData)
  const hasUnsavedChanges = computed(() => hasUnsavedConfigChanges.value || (
    isModalOpen.value
    && Object.values(newConfig).some((value) => value.trim().length > 0)
  ))

  function openCreateModal() {
    if (isCreatingConfig.value) return
    modalRevision += 1
    resetNewConfig()
    isModalOpen.value = true
  }

  function forceCloseCreateModal() {
    modalRevision += 1
    isModalOpen.value = false
    resetNewConfig()
  }

  function closeCreateModal() {
    if (isCreatingConfig.value) return
    forceCloseCreateModal()
  }

  function resetNewConfig() {
    Object.assign(newConfig, createEmptyConfigForm())
  }

  useAuthSessionReset(() => {
    resetDrafts()
    forceCloseCreateModal()
  }, authStore)

  const isSavePending = (key: string) => operations.isPending(`config:${key}`)

  async function handleSave(key: string) {
    const submission = captureSave(key)
    if (!submission) return
    const config = submission.draft
    const payload = normalizeConfigWritePayload(config)
    if (!payload) {
      submission.release()
      return
    }
    try {
      await operations.run(`config:${key}`, async (scope) => {
        try {
          const response = await updateConfig({ key: config.key, value: payload.value, description: payload.description })
          scope.assertCurrent()
          submission.acknowledge(unwrapAxiosApiData(response))
          if (config.key === EMOTICON_IMAGE_MAX_COUNT_KEY) {
            await refreshEmoticonImagePolicy()
            scope.assertCurrent()
          }
          toastStore.addToast(t('admin.settings.messages.saved'), 'success')
        } catch {
          // Error handled globally.
        }
      })
    } finally {
      submission.release()
    }
  }

  async function handleCreateConfig() {
    if (isCreatingConfig.value || !isModalOpen.value) return
    const payload = normalizeConfigWritePayload(newConfig, { requireKey: true })
    if (!payload?.key) return
    const key = payload.key
    const submittedRevision = modalRevision
    await operations.run('create', async (scope) => {
      try {
        await createConfig({ key, value: payload.value, description: payload.description })
        scope.assertCurrent()
        if (submittedRevision !== modalRevision || !isModalOpen.value) return
        if (key === EMOTICON_IMAGE_MAX_COUNT_KEY) {
          await refreshEmoticonImagePolicy()
          scope.assertCurrent()
        }
        if (submittedRevision !== modalRevision || !isModalOpen.value) return
        toastStore.addToast(t('admin.settings.messages.saved'), 'success')
        forceCloseCreateModal()
      } catch {
        // Error handled globally.
      }
    })
  }

  async function handleDelete(key: string) {
    const targetKey = configs.value.find((config) => config.key === key)?.key
    if (!targetKey) return
    await operations.run(`config:${key}`, async (scope) => {
      const isConfirmed = await confirm(t('common.confirmDelete'))
      scope.assertCurrent()
      if (!isConfirmed || !configs.value.some((config) => config.key === targetKey)) return
      try {
        await deleteConfig(targetKey)
        scope.assertCurrent()
        if (targetKey === EMOTICON_IMAGE_MAX_COUNT_KEY) {
          await refreshEmoticonImagePolicy()
          scope.assertCurrent()
        }
        toastStore.addToast(t('common.deleted'), 'success')
      } catch {
        // Error handled globally.
      }
    })
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
    isSavePending,
    isModalOpen,
    newConfig,
    openCreateModal,
    updateDraft,
  }
}
