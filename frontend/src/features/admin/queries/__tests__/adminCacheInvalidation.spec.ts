import { describe, expect, it } from 'vitest'
import {
  invalidateAdminErrorLogCaches,
  invalidateAdminUserCaches,
} from '@/features/admin/queries/adminCacheInvalidation'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'

describe('admin cache invalidation', () => {
  it('refreshes user lists, details, and nested activity once without cancelling requests or touching other sessions', async () => {
    const affectedKeys = [
      adminQueryKeys.users({ page: 0 }),
      adminQueryKeys.userDetail(7),
      adminQueryKeys.userDetail(8),
      adminQueryKeys.userPosts(7, { page: 0 }),
      adminQueryKeys.userComments(7, { page: 0 }),
      adminQueryKeys.userSubscriptions(7, { page: 0 }),
      adminQueryKeys.userSanctions(7, { page: 0 }),
    ]
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => invalidateAdminUserCaches(queryClient, 3),
      affectedKeys.map((key) => sessionQueryKey(3, key)),
      [
        ...affectedKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, adminQueryKeys.admins({ page: 0 })),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys.map((key) => sessionQueryKey(3, key)))
  })

  it('refreshes error log lists, all details, and the separate stats key once without touching other sessions', async () => {
    const affectedKeys = [
      adminQueryKeys.errorLogs({ page: 0 }),
      adminQueryKeys.errorLogDetail(1),
      adminQueryKeys.errorLogDetail(2),
      adminQueryKeys.errorLogStats,
    ]
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => invalidateAdminErrorLogCaches(queryClient, 3),
      affectedKeys.map((key) => sessionQueryKey(3, key)),
      [
        ...affectedKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, adminQueryKeys.stats),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys.map((key) => sessionQueryKey(3, key)))
  })
})
