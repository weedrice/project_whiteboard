import { computed, onMounted, onScopeDispose, ref, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'

type UseBoardCreationPolicyOptions = {
  isEdit: ComputedRef<boolean>
}

export function useBoardCreationPolicy({ isEdit }: UseBoardCreationPolicyOptions) {
  const authStore = useAuthStore()
  const configStore = useConfigStore()

  const configKey = 'POINT_BOARD_CREATE_COST'
  const costLoading = ref(!isEdit.value)
  const costLoadFailed = ref(false)
  const userPoints = computed(() => authStore.user?.points || 0)
  const boardCreateCost = computed(() => {
    const rawCost = configStore.getConfig(configKey)
    if (!rawCost || !/^\d+$/.test(rawCost)) return null
    const parsedCost = Number.parseInt(rawCost, 10)
    return Number.isSafeInteger(parsedCost) && parsedCost > 0 ? parsedCost : null
  })
  const isBoardCreateCostLoading = computed(() => !isEdit.value && costLoading.value)
  const boardCreateCostError = computed(() => !isEdit.value && costLoadFailed.value)
  const canCreate = computed(() => isEdit.value || (
    boardCreateCost.value !== null && userPoints.value >= boardCreateCost.value
  ))
  let costLoadRevision = 0

  async function loadBoardCreateCost() {
    const revision = ++costLoadRevision
    if (isEdit.value) return
    if (boardCreateCost.value !== null) {
      costLoading.value = false
      costLoadFailed.value = false
      return
    }
    costLoading.value = true
    costLoadFailed.value = false
    configStore.invalidateConfig(configKey)
    await configStore.fetchConfig(configKey)
    if (revision !== costLoadRevision) return
    costLoading.value = false
    costLoadFailed.value = boardCreateCost.value === null
  }

  onMounted(() => {
    void loadBoardCreateCost()
  })

  onScopeDispose(() => {
    costLoadRevision += 1
  })

  return {
    userPoints,
    boardCreateCost,
    isBoardCreateCostLoading,
    boardCreateCostError,
    canCreate,
    loadBoardCreateCost,
  }
}
