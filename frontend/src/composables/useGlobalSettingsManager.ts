import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/composables/useAdmin'
import { useConfigEditor } from '@/composables/useConfigEditor'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { trimText } from '@/utils/inputNormalization'

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
  const { confirm } = useConfirm()
  const { useConfigs, useUpdateConfig, useCreateConfig, useDeleteConfig } = useAdmin()

  const isModalOpen = ref(false)
  const newConfig = reactive(createEmptyConfigForm())

  const { data: configsData, isLoading } = useConfigs()
  const { mutateAsync: updateConfig } = useUpdateConfig()
  const { mutateAsync: createConfig } = useCreateConfig()
  const { mutateAsync: deleteConfig } = useDeleteConfig()
  const { configs, updateDraft, getDraft } = useConfigEditor(configsData)

  function openCreateModal() {
    isModalOpen.value = true
  }

  function closeCreateModal() {
    isModalOpen.value = false
  }

  function resetNewConfig() {
    Object.assign(newConfig, createEmptyConfigForm())
  }

  async function handleSave(key: string) {
    const config = getDraft(key)
    if (!config) return

    const value = trimText(config.value)
    const description = typeof config.description === 'string'
      ? trimText(config.description)
      : config.description
    if (!value) return

    try {
      await updateConfig({ key: config.key, value, description })
      toastStore.addToast(t('admin.settings.messages.saved'), 'success')
    } catch {
      // Error handled globally
    }
  }

  async function handleCreateConfig() {
    const key = trimText(newConfig.key)
    const value = trimText(newConfig.value)
    const description = trimText(newConfig.description)
    if (!key || !value) return

    try {
      await createConfig({
        key,
        value,
        description,
      })

      toastStore.addToast(t('admin.settings.messages.saved'), 'success')
      closeCreateModal()
      resetNewConfig()
    } catch {
      // Error handled globally
    }
  }

  async function handleDelete(key: string) {
    const isConfirmed = await confirm(t('common.confirmDelete'))
    if (!isConfirmed) return

    try {
      await deleteConfig(key)
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
    isLoading,
    isModalOpen,
    newConfig,
    openCreateModal,
    updateDraft,
  }
}
