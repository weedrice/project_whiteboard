import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import { invalidateAdminUserCaches } from '@/features/admin/queries/adminCacheInvalidation'
import {
    callAdminApiWithOptionalConfig,
    useAdminDataQuery,
    useAdminNullableDataQuery,
    useAdminNullablePageQuery,
    useAdminPageQuery,
} from '@/features/admin/queries/adminApiQuery'
import type {
    AdminCreateData,
    UserSearchParams,
} from '@/api/admin'
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
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getAdmins(params.value, requestConfig),
                () => adminApi.getAdmins(params.value),
            )
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
        return useAdminDataQuery<SuperAdminInfo[]>(
            adminQueryKeys.superAdmins,
            (config) => callAdminApiWithOptionalConfig(config, adminApi.getSuperAdmin, () => adminApi.getSuperAdmin()),
        )
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
            (config) => callAdminApiWithOptionalConfig(
                config,
                (requestConfig) => adminApi.getUsers(params.value, requestConfig),
                () => adminApi.getUsers(params.value),
            ),
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
            (config) => userId.value == null
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getUserDetail(userId.value as number, requestConfig),
                    () => adminApi.getUserDetail(userId.value as number),
                ),
            enabled
        )
    }

    const useAdminUserPosts = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserPostItem>(
            adminQueryKeys.userPosts(userId, params),
            (config) => userId.value == null
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getUserPosts(userId.value as number, params.value, requestConfig),
                    () => adminApi.getUserPosts(userId.value as number, params.value),
                ),
            enabled
        )
    }

    const useAdminUserComments = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserCommentItem>(
            adminQueryKeys.userComments(userId, params),
            (config) => userId.value == null
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getUserComments(userId.value as number, params.value, requestConfig),
                    () => adminApi.getUserComments(userId.value as number, params.value),
                ),
            enabled
        )
    }

    const useAdminUserSubscriptions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserSubscriptionItem>(
            adminQueryKeys.userSubscriptions(userId, params),
            (config) => userId.value == null
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getUserSubscriptions(userId.value as number, params.value, requestConfig),
                    () => adminApi.getUserSubscriptions(userId.value as number, params.value),
                ),
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
