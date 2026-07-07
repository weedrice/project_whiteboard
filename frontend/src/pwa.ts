import { registerSW } from 'virtual:pwa-register'
import type { Pinia } from 'pinia'
import { useToastStore } from '@/stores/toast'

export function registerPwaUpdatePrompt(pinia: Pinia) {
  registerSW({
    onNeedRefresh() {
      useToastStore(pinia).addToast('새 버전이 준비되었습니다. 새로고침하면 적용됩니다.', 'info', 8000)
    },
    onOfflineReady() {
      useToastStore(pinia).addToast('오프라인에서 열 수 있는 기본 화면이 준비되었습니다.', 'success')
    },
  })
}
