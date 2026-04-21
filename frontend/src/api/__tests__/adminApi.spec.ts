import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
}))

vi.mock('../index', () => ({
    default: apiMock,
}))

import { adminApi } from '../admin'

describe('adminApi - Error Log Management', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls getErrorLogs with correct path and params', () => {
        const params = { page: 0, size: 20, errorType: 'BusinessException', httpStatus: 500 }

        adminApi.getErrorLogs(params)

        expect(apiMock.get).toHaveBeenCalledWith('/admin/error-logs', { params })
    })

    it('calls getErrorLogs with minimal params', () => {
        const params = { page: 0, size: 20 }

        adminApi.getErrorLogs(params)

        expect(apiMock.get).toHaveBeenCalledWith('/admin/error-logs', { params })
    })

    it('calls getErrorLogs with date range params', () => {
        const params = {
            page: 0,
            size: 20,
            startDate: '2026-02-09',
            endDate: '2026-02-23',
            isResolved: 'N'
        }

        adminApi.getErrorLogs(params)

        expect(apiMock.get).toHaveBeenCalledWith('/admin/error-logs', { params })
    })

    it('calls getErrorLog with correct path', () => {
        adminApi.getErrorLog(42)

        expect(apiMock.get).toHaveBeenCalledWith('/admin/error-logs/42')
    })

    it('calls resolveErrorLog with memo data', () => {
        const data = { memo: '확인 완료' }

        adminApi.resolveErrorLog(1, data)

        expect(apiMock.put).toHaveBeenCalledWith('/admin/error-logs/1/resolve', data)
    })

    it('calls resolveErrorLog without memo data', () => {
        adminApi.resolveErrorLog(2, undefined)

        expect(apiMock.put).toHaveBeenCalledWith('/admin/error-logs/2/resolve', undefined)
    })

    it('calls getErrorLogStats with correct path', () => {
        adminApi.getErrorLogStats()

        expect(apiMock.get).toHaveBeenCalledWith('/admin/error-logs/stats')
    })
})

describe('adminApi - Other Endpoints', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls admin management endpoints correctly', () => {
        adminApi.getAdmins({ page: 0, size: 20 })
        adminApi.createAdmin({ loginId: 'testadmin', boardId: 1, role: 'BOARD_ADMIN' })
        adminApi.createAdmin({ loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })
        adminApi.activateAdmin(1)
        adminApi.deactivateAdmin(1)
        adminApi.getBoardManager(3)
        adminApi.updateBoardManager(3, { loginId: 'next' })

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/admins', { params: { page: 0, size: 20 } })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/admin/admins', { loginId: 'testadmin', boardId: 1, role: 'BOARD_ADMIN' })
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/admin/admins', { loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/admin/admins/1/activate')
        expect(apiMock.put).toHaveBeenNthCalledWith(2, '/admin/admins/1/deactivate')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/admin/boards/3/manager')
        expect(apiMock.put).toHaveBeenNthCalledWith(3, '/admin/boards/3/manager', { loginId: 'next' })
    })

    it('calls super admin endpoints correctly', () => {
        adminApi.getSuperAdmin()
        adminApi.activeSuperAdmin({ loginId: 'super' })
        adminApi.deactivateSuperAdmin({ loginId: 'super' })

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/super')
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/admin/super/active', { loginId: 'super' })
        expect(apiMock.put).toHaveBeenNthCalledWith(2, '/admin/super/deactive', { loginId: 'super' })
    })

    it('calls user management endpoints correctly', () => {
        const params = { page: 0, size: 10, q: 'john' }

        adminApi.getUsers(params)
        adminApi.updateUserStatus(1, 'ACTIVE')
        adminApi.sanctionUser({ userId: 1, type: 'BAN', reason: 'spam' } as any)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/users', { params })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/admin/users/1/status', { status: 'ACTIVE' })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/admin/sanctions', { userId: 1, type: 'BAN', reason: 'spam' })
    })

    it('calls report endpoints correctly', () => {
        const params = { page: 0, size: 20 }

        adminApi.getReports(params)
        adminApi.resolveReport(5, { status: 'RESOLVED' })

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/reports', { params })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/admin/reports/5', { status: 'RESOLVED' })
    })

    it('calls IP block endpoints correctly', () => {
        adminApi.getIpBlocks({ page: 0, size: 20 })
        adminApi.blockIp({ ipAddress: '192.168.1.1', reason: 'Spam' })
        adminApi.unblockIp('192.168.1.1')

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/ip-blocks', { params: { page: 0, size: 20 } })
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/admin/ip-blocks', { ipAddress: '192.168.1.1', reason: 'Spam' })
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/admin/ip-blocks/192.168.1.1')
    })

    it('calls config endpoints correctly', () => {
        adminApi.getConfigs()
        adminApi.createConfig({ key: 'site.name', value: 'Test', description: 'desc' })
        adminApi.updateConfig('site.name', 'Updated', 'new desc')
        adminApi.deleteConfig('site.name')

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/admin/configs')
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/admin/configs', { key: 'site.name', value: 'Test', description: 'desc' })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/admin/configs/site.name', { value: 'Updated', description: 'new desc' })
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/admin/configs/site.name')
    })

    it('calls dashboard stats endpoint correctly', () => {
        adminApi.getDashboardStats()

        expect(apiMock.get).toHaveBeenCalledWith('/admin/stats')
    })

    it('calls board management endpoints correctly', () => {
        adminApi.getBoards()
        adminApi.createBoard({ boardName: 'New', boardUrl: 'new' } as any)
        adminApi.updateBoard('test', { boardName: 'Updated' } as any)
        adminApi.deleteBoard('test')

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/boards/all')
        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/boards', { boardName: 'New', boardUrl: 'new' })
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/boards/test', { boardName: 'Updated' })
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/boards/test')
    })
})
