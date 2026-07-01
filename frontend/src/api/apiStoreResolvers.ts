import type { AuthStoreLike, ToastStore } from '@/api/authRefreshSession'

export interface ApiStoreResolvers {
  resolveToastStore?: () => ToastStore | Promise<ToastStore>
  resolveAuthStore?: () => AuthStoreLike | null | Promise<AuthStoreLike | null>
}

export const noopToastStore: ToastStore = {
  addToast: () => undefined,
}

let toastStoreResolver: ApiStoreResolvers['resolveToastStore'] | null = null
let authStoreResolver: ApiStoreResolvers['resolveAuthStore'] | null = null

export const configureApiStoreResolvers = (resolvers: ApiStoreResolvers): void => {
  if (resolvers.resolveToastStore) {
    toastStoreResolver = resolvers.resolveToastStore
  }
  if (resolvers.resolveAuthStore) {
    authStoreResolver = resolvers.resolveAuthStore
  }
}

export const resolveToastStore = async (): Promise<ToastStore> => {
  try {
    if (!toastStoreResolver) {
      return noopToastStore
    }
    return await toastStoreResolver()
  } catch {
    return noopToastStore
  }
}

export const resolveAuthStore = async (): Promise<AuthStoreLike | null> => {
  try {
    if (!authStoreResolver) {
      return null
    }
    return await authStoreResolver()
  } catch {
    return null
  }
}
