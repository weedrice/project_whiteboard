export type RegisterSWOptions = {
  immediate?: boolean
  onNeedRefresh?: () => void
  onNeedReload?: () => void
  onOfflineReady?: () => void
  onRegisteredSW?: (swUrl: string, registration?: ServiceWorkerRegistration) => void
}

export function registerSW(_options: RegisterSWOptions = {}) {
  return async (_reloadPage = true) => undefined
}
