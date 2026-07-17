import { useMutation, type QueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { adminApi } from '@/api/admin'
import { adminQueryKeys } from '@/features/admin/queries/adminQueryKeys'
import {
    invalidateAdminAccountCaches,
    invalidateAdminSuperAdminCaches,
    invalidateAdminUserCaches,
} from '@/features/admin/queries/adminCacheInvalidation'
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
import { useAuthStore } from '@/stores/auth'
import { captureSessionGeneration, isSessionGenerationCurrent } from '@/queryAuthScope'

export function useAdminAccountManagement(queryClient: QueryClient) {
    const authStore = useAuthStore()
    const captureMutationSession = () => ({
        sessionGeneration: captureSessionGeneration(authStore),
    })
    const isCurrentMutation = (
        context?: { sessionGeneration: number },
    ): context is { sessionGeneration: number } =>
        context !== undefined
        && isSessionGenerationCurrent(authStore, context.sessionGeneration)

    const useAdmins = (params: Ref<{ page?: number, size?: number }>) => {
        return useAdminPageQuery<BoardAdminInfo>(
            computed(() => adminQueryKeys.admins(params.value)),
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
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminAccountCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useUpdateAdminStatus = () => {
        return useMutation({
            mutationFn: ({ adminId, action }: { adminId: string | number, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId)
                return adminApi.deactivateAdmin(adminId)
            },
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminAccountCaches(queryClient, context.sessionGeneration)
            },
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
            mutationFn: ({ loginId, action, reason }: { loginId: string, action: 'activate' | 'deactivate', reason: string }) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId, reason })
                return adminApi.deactivateSuperAdmin({ loginId, reason })
            },
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminSuperAdminCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useUsers = (params: Ref<UserSearchParams>, enabled?: Ref<boolean>) => {
        return useAdminPageQuery<User>(
            computed(() => adminQueryKeys.users(params.value)),
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
            mutationFn: ({ userId, status, reason }: { userId: string | number, status: string, reason: string }) => adminApi.updateUserStatus(userId, status, reason),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminUserCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useAdminUserDetail = (userId: Ref<number | null>) => {
        const enabled = computed(() => userId.value !== null)
        return useAdminNullableDataQuery<AdminUserDetail>(
            computed(() => adminQueryKeys.userDetail(userId.value)),
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
            computed(() => adminQueryKeys.userPosts(userId.value, params.value)),
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
            computed(() => adminQueryKeys.userComments(userId.value, params.value)),
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
            computed(() => adminQueryKeys.userSubscriptions(userId.value, params.value)),
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
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminUserCaches(queryClient, context.sessionGeneration)
            },
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
