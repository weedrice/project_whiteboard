import type { QueryClient } from '@tanstack/vue-query'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'
import { sessionQueryKey } from '@/queryAuthScope'

function invalidateAdminQueryKeys(
  queryClient: QueryClient,
  sessionGeneration: number,
  queryKeys: readonly (readonly unknown[])[],
) {
  invalidateQueryKeys(
    queryClient,
    queryKeys.map((queryKey) => sessionQueryKey(sessionGeneration, queryKey)),
  )
}

export function invalidateAdminUserCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.usersRoot,
    adminQueryKeys.userDetailRoot,
  ])
}

export function invalidateAdminAccountCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.adminsRoot,
  ])
}

export function invalidateAdminSuperAdminCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.superAdmins,
  ])
}

export function invalidateAdminBoardListCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.boards,
  ])
}

export function invalidateAdminBoardCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.boards,
    adminQueryKeys.adminsRoot,
  ])
}

export function invalidateAdminBoardManagerCache(
  queryClient: QueryClient,
  sessionGeneration: number,
  boardId: number,
) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.boardManagerById(boardId),
  ])
}

export function invalidateAdminConfigCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.configs,
  ])
}

export function invalidateAdminReportCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.reportsRoot,
  ])
}

export function invalidateAdminIpBlockCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.ipBlocksRoot,
  ])
}

export function invalidateAdminErrorLogCaches(
  queryClient: QueryClient,
  sessionGeneration: number,
  errorLogId?: number,
) {
  invalidateAdminQueryKeys(queryClient, sessionGeneration, [
    adminQueryKeys.errorLogsRoot,
    adminQueryKeys.errorLogStats,
    ...(errorLogId === undefined ? [] : [adminQueryKeys.errorLogDetailById(errorLogId)]),
  ])
}
