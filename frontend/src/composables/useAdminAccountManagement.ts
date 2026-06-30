import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import { invalidateAdminUserCaches } from '@/composables/adminCacheInvalidation'
import {
    useAdminDataQuery,
    useAdminNullableDataQuery,
    useAdminNullablePageQuery,
    useAdminPageQuery,
} from '@/composables/adminApiQuery'
import type {
    AdminCreateData,
    UserSearchParams,
} from '@/composables/adminComposableTypes'
import type {
    AdminUserCommentItem,
    AdminUserDetail,
    AdminUserPostItem,
    AdminUserSubscriptionItem,
    BoardAdminInfo,
    SanctionData,
    SuperAdminInfo,
    User,
} from '@/types'

export function useAdminAccountManagement(queryClient: QueryClient) {
    const useAdmins = (params: Ref<{ page?: number, size?: number }>) => {
        return useAdminPageQuery<BoardAdminInfo>(
            adminQueryKeys.admins(params),
            () => adminApi.getAdmins(params.value)
        )
    }

    const useCreateAdmin = () => {
        return useMutation({
            mutationFn: (data: AdminCreateData) => adminApi.createAdmin(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.adminsRoot })
        })
    }

    const useUpdateAdminStatus = () => {
        return useMutation({
            mutationFn: ({ adminId, action }: { adminId: string | number, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId)
                return adminApi.deactivateAdmin(adminId)
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.adminsRoot })
        })
    }

    const useSuperAdmins = () => {
        return useAdminDataQuery<SuperAdminInfo[]>(adminQueryKeys.superAdmins, () => adminApi.getSuperAdmin())
    }

    const useUpdateSuperAdminStatus = () => {
        return useMutation({
            mutationFn: ({ loginId, action }: { loginId: string, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId })
                return adminApi.deactivateSuperAdmin({ loginId })
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.superAdmins })
        })
    }

    const useUsers = (params: Ref<UserSearchParams>, enabled?: Ref<boolean>) => {
        return useAdminPageQuery<User>(
            adminQueryKeys.users(params),
            () => adminApi.getUsers(params.value),
            {
                enabled,
            }
        )
    }

    const useUpdateUserStatus = () => {
        return useMutation({
            mutationFn: ({ userId, status }: { userId: string | number, status: string }) => adminApi.updateUserStatus(userId, status),
            onSuccess: () => {
                invalidateAdminUserCaches(queryClient)
            }
        })
    }

    const useAdminUserDetail = (userId: Ref<number | null>) => {
        const enabled = computed(() => userId.value !== null)
        return useAdminNullableDataQuery<AdminUserDetail>(
            adminQueryKeys.userDetail(userId),
            () => userId.value == null ? null : adminApi.getUserDetail(userId.value),
            enabled
        )
    }

    const useAdminUserPosts = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserPostItem>(
            adminQueryKeys.userPosts(userId, params),
            () => userId.value == null ? null : adminApi.getUserPosts(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserComments = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserCommentItem>(
            adminQueryKeys.userComments(userId, params),
            () => userId.value == null ? null : adminApi.getUserComments(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserSubscriptions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserSubscriptionItem>(
            adminQueryKeys.userSubscriptions(userId, params),
            () => userId.value == null ? null : adminApi.getUserSubscriptions(userId.value, params.value),
            enabled
        )
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data: SanctionData) => adminApi.sanctionUser(data),
            onSuccess: () => {
                invalidateAdminUserCaches(queryClient)
            }
        })
    }

    return {
        useAdmins,
        useCreateAdmin,
        useUpdateAdminStatus,
        useSuperAdmins,
        useUpdateSuperAdminStatus,
        useUsers,
        useUpdateUserStatus,
        useAdminUserDetail,
        useAdminUserPosts,
        useAdminUserComments,
        useAdminUserSubscriptions,
        useSanctionUser,
    }
}
