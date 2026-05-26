import { computed, type ComputedRef } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useConfigStore } from '@/stores/config'

type UseBoardCreationPolicyOptions = {
  isEdit: ComputedRef<boolean>
}

export function useBoardCreationPolicy({ isEdit }: UseBoardCreationPolicyOptions) {
  const authStore = useAuthStore()
  const configStore = useConfigStore()

  const userPoints = computed(() => authStore.user?.points || 0)
  const boardCreateCost = computed(() => {
    const cost = configStore.getConfig('POINT_BOARD_CREATE_COST')
    return cost ? parseInt(cost) : 500
  })
  const canCreate = computed(() => isEdit.value || userPoints.value >= boardCreateCost.value)

  return {
    userPoints,
    boardCreateCost,
    canCreate
  }
}
