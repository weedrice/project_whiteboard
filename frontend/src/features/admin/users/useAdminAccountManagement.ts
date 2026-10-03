import { type QueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
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
    SanctionHistoryItem,
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
        return useAuthScopedMutation({
            mutationFn: (data: AdminCreateData, mutationContext) => adminApi.createAdmin(data, mutationContext.requestConfig),
            onMutate: captureMutationSession,
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateAdminAccountCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const useUpdateAdminStatus = () => {
        return useAuthScopedMutation({
            mutationFn: ({ adminId, action }: { adminId: string | number, action: 'activate' | 'deactivate' }, mutationContext) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId, mutationContext.requestConfig)
                return adminApi.deactivateAdmin(adminId, mutationContext.requestConfig)
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
        return useAuthScopedMutation({
            mutationFn: ({ loginId, action, reason }: { loginId: string, action: 'activate' | 'deactivate', reason: string }, mutationContext) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId, reason }, mutationContext.requestConfig)
                return adminApi.deactivateSuperAdmin({ loginId, reason }, mutationContext.requestConfig)
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
        return useAuthScopedMutation({
            mutationFn: ({ userId, status, reason }: { userId: string | number, status: string, reason: string }, mutationContext) => adminApi.updateUserStatus(userId, status, reason, mutationContext.requestConfig),
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

    const useAdminUserSanctions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<SanctionHistoryItem>(
            computed(() => adminQueryKeys.userSanctions(userId.value, params.value)),
            (config) => userId.value == null
                ? null
                : callAdminApiWithOptionalConfig(
                    config,
                    (requestConfig) => adminApi.getUserSanctions(userId.value as number, params.value, requestConfig),
                    () => adminApi.getUserSanctions(userId.value as number, params.value),
                ),
            enabled
        )
    }

    const useSanctionUser = () => {
        return useAuthScopedMutation({
            mutationFn: (data: SanctionData, mutationContext) => adminApi.sanctionUser(data, mutationContext.requestConfig),
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
        useAdminUserSanctions,
        useSanctionUser,
    }
}
