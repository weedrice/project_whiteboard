import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { computed } from 'vue'

export function useAdmin() {
    const queryClient = useQueryClient()

    // --- Admin Management ---
    const useAdmins = () => {
        return useQuery({
            queryKey: ['admin', 'admins'],
            queryFn: async () => {
                const { data } = await adminApi.getAdmins()
                return data.data
            }
        })
    }

    const useCreateAdmin = () => {
        return useMutation({
            mutationFn: (data) => adminApi.createAdmin(data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'admins'])
        })
    }

    const useUpdateAdminStatus = () => {
        return useMutation({
            mutationFn: ({ adminId, action }) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId)
                return adminApi.deactivateAdmin(adminId)
            },
            onSuccess: () => queryClient.invalidateQueries(['admin', 'admins'])
        })
    }

    const useSuperAdmins = () => {
        return useQuery({
            queryKey: ['admin', 'super'],
            queryFn: async () => {
                const { data } = await adminApi.getSuperAdmin()
                return data.data
            }
        })
    }

    const useUpdateSuperAdminStatus = () => {
        return useMutation({
            mutationFn: ({ loginId, action }) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId })
                return adminApi.deactiveSuperAdmin({ loginId })
            },
            onSuccess: () => queryClient.invalidateQueries(['admin', 'super'])
        })
    }

    // --- User Management ---
    const useUsers = (params) => {
        return useQuery({
            queryKey: ['admin', 'users', params],
            queryFn: async () => {
                const { data } = await adminApi.getUsers(params.value)
                return data.data
            },
            keepPreviousData: true
        })
    }

    const useUpdateUserStatus = () => {
        return useMutation({
            mutationFn: ({ userId, status }) => adminApi.updateUserStatus(userId, status),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'users'])
        })
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data) => adminApi.sanctionUser(data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'users'])
        })
    }

    // --- Report Management ---
    const useReports = (params) => {
        return useQuery({
            queryKey: ['admin', 'reports', params],
            queryFn: async () => {
                const { data } = await adminApi.getReports(params.value)
                return data.data
            },
            keepPreviousData: true
        })
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }) => adminApi.resolveReport(reportId, data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'reports'])
        })
    }

    // --- IP Block Management ---
    const useIpBlocks = () => {
        return useQuery({
            queryKey: ['admin', 'ip-blocks'],
            queryFn: async () => {
                const { data } = await adminApi.getIpBlocks()
                return data.data
            }
        })
    }

    const useBlockIp = () => {
        return useMutation({
            mutationFn: (data) => adminApi.blockIp(data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'ip-blocks'])
        })
    }

    const useUnblockIp = () => {
        return useMutation({
            mutationFn: (ipAddress) => adminApi.unblockIp(ipAddress),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'ip-blocks'])
        })
    }

    // --- Config Management ---
    const useConfigs = () => {
        return useQuery({
            queryKey: ['admin', 'configs'],
            queryFn: async () => {
                const { data } = await adminApi.getConfigs()
                return data.data
            }
        })
    }

    const useUpdateConfig = () => {
        return useMutation({
            mutationFn: ({ key, value }) => adminApi.updateConfig(key, value),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'configs'])
        })
    }

    const useCreateConfig = () => {
        return useMutation({
            mutationFn: (data) => adminApi.createConfig(data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'configs'])
        })
    }

    const useDeleteConfig = () => {
        return useMutation({
            mutationFn: (key) => adminApi.deleteConfig(key),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'configs'])
        })
    }

    // --- Dashboard Stats ---
    const useDashboardStats = () => {
        return useQuery({
            queryKey: ['admin', 'stats'],
            queryFn: async () => {
                const { data } = await adminApi.getDashboardStats()
                return data.data
            }
        })
    }

    // --- Board Management (Admin) ---
    const useAdminBoards = () => {
        return useQuery({
            queryKey: ['admin', 'boards'],
            queryFn: async () => {
                const { data } = await adminApi.getBoards()
                return data.data
            }
        })
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data) => adminApi.createBoard(data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'boards'])
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }) => adminApi.updateBoard(boardUrl, data),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'boards'])
        })
    }

    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: (boardUrl) => adminApi.deleteBoard(boardUrl),
            onSuccess: () => queryClient.invalidateQueries(['admin', 'boards'])
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
        useSanctionUser,
        useReports,
        useResolveReport,
        useIpBlocks,
        useBlockIp,
        useUnblockIp,
        useConfigs,
        useUpdateConfig,
        useCreateConfig,
        useDeleteConfig,
        useDashboardStats,
        useAdminBoards,
        useCreateBoard,
        useUpdateBoard,
        useDeleteBoard
    }
}
