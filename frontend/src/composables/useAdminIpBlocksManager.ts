import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdmin } from '@/composables/useAdmin'
import { useConfirm } from '@/composables/useConfirm'
import { usePageResponseState } from '@/composables/usePaginatedQueryState'
import { useToastStore } from '@/stores/toast'
import type { IpBlock } from '@/types'

export function useAdminIpBlocksManager() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm } = useConfirm()
  const { useIpBlocks, useBlockIp, useUnblockIp } = useAdmin()

  const newIp = ref('')
  const blockReason = ref('')
  const page = ref(0)
  const ipBlockParams = computed(() => ({
    page: page.value,
    size: 20,
  }))

  const { data: ipBlocksData } = useIpBlocks(ipBlockParams)
  const { mutateAsync: blockIp } = useBlockIp()
  const { mutateAsync: unblockIp } = useUnblockIp()
  const { items: ipBlocks, totalPages } = usePageResponseState(ipBlocksData, page)

  const isDetailModalOpen = ref(false)
  const selectedIpBlock = ref<IpBlock | null>(null)

  function openDetailModal(ipBlock: IpBlock) {
    selectedIpBlock.value = ipBlock
    isDetailModalOpen.value = true
  }

  function closeDetailModal() {
    isDetailModalOpen.value = false
  }

  async function handleBlockIp() {
    const ipAddress = newIp.value.trim()
    const reason = blockReason.value.trim()
    if (!ipAddress || !reason) return

    try {
      await blockIp({ ipAddress, reason })
      page.value = 0
      toastStore.addToast(t('admin.security.messages.blocked'), 'success')
      newIp.value = ''
      blockReason.value = ''
    } catch {
      // Error handled globally
    }
  }

  async function handleUnblockIp(ipAddress: string) {
    const isConfirmed = await confirm(t('admin.security.messages.confirmUnblock', { ip: ipAddress }))
    if (!isConfirmed) return

    try {
      await unblockIp(ipAddress)
      if (page.value > 0 && ipBlocks.value.length === 1) {
        page.value -= 1
      }
      toastStore.addToast(t('admin.security.messages.unblocked'), 'success')
    } catch {
      // Error handled globally
    }
  }

  watch(totalPages, (nextTotalPages) => {
    if (nextTotalPages === 0) {
      page.value = 0
      return
    }

    if (page.value >= nextTotalPages) {
      page.value = nextTotalPages - 1
    }
  })

  return {
    blockReason,
    closeDetailModal,
    handleBlockIp,
    handleUnblockIp,
    ipBlocks,
    isDetailModalOpen,
    newIp,
    openDetailModal,
    page,
    selectedIpBlock,
    totalPages,
  }
}
