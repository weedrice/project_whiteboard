import { computed, onBeforeUnmount, onMounted, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'

type ConfirmLeave = (message: string) => Promise<boolean>

export function useUnsavedChangesGuard(
  hasUnsavedChanges: Readonly<Ref<boolean>>,
  isOperationPending: Readonly<Ref<boolean>>,
  message: () => string,
  confirmLeave: ConfirmLeave,
) {
  const shouldBlockUnload = computed(() => hasUnsavedChanges.value || isOperationPending.value)

  const handleBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!shouldBlockUnload.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave(async () => {
    if (isOperationPending.value) return false
    if (!hasUnsavedChanges.value) return true
    return confirmLeave(message())
  })

  usePwaReloadBlocker(shouldBlockUnload)
  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
}
