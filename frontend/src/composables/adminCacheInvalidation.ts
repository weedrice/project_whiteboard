import type { QueryClient } from '@tanstack/vue-query'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'

export function invalidateAdminUserCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.usersRoot,
    adminQueryKeys.userDetailRoot,
  ])
}

export function invalidateAdminBoardCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.boards,
    adminQueryKeys.adminsRoot,
  ])
}

export function invalidateAdminReportCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.reportsRoot,
  ])
}

export function invalidateAdminIpBlockCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.ipBlocksRoot,
  ])
}
