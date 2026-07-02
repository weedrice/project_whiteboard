import type { QueryClient } from '@tanstack/vue-query'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'

export function invalidateAdminUserCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.usersRoot,
    adminQueryKeys.userDetailRoot,
  ])
}

export function invalidateAdminAccountCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.adminsRoot,
  ])
}

export function invalidateAdminSuperAdminCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.superAdmins,
  ])
}

export function invalidateAdminBoardListCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.boards,
  ])
}

export function invalidateAdminBoardCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.boards,
    adminQueryKeys.adminsRoot,
  ])
}

export function invalidateAdminBoardManagerCache(queryClient: QueryClient, boardId: number) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.boardManagerById(boardId),
  ])
}

export function invalidateAdminConfigCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.configs,
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

export function invalidateAdminErrorLogCaches(queryClient: QueryClient, errorLogId?: number) {
  invalidateQueryKeys(queryClient, [
    adminQueryKeys.errorLogsRoot,
    adminQueryKeys.errorLogStats,
    ...(errorLogId === undefined ? [] : [adminQueryKeys.errorLogDetailById(errorLogId)]),
  ])
}
