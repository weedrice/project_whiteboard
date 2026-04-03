import { beforeEach, describe, expect, it, vi } from 'vitest'
const apiMock = vi.hoisted(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
}))

vi.mock('@/api', () => ({
    default: apiMock,
}))

import { userApi } from '../user'

describe('userApi', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('calls profile and settings endpoints', () => {
        const profileData = { displayName: 'tester', profileImageId: 10 }
        const passwordData = { currentPassword: 'old', newPassword: 'new' }
        const settingsData = { language: 'ko' }
        const notificationBulkData = {
            settings: [
                { notificationType: 'LIKE' as const, isEnabled: true },
                { notificationType: 'COMMENT' as const, isEnabled: false },
                { notificationType: 'REPLY' as const, isEnabled: true },
            ],
        }

        userApi.getMyProfile()
        userApi.getUserProfile(12)
        userApi.updateMyProfile(profileData)
        userApi.updatePassword(passwordData.currentPassword, passwordData.newPassword)
        userApi.deleteAccount('secret')
        userApi.verifyEmail({ email: 'test@example.com', code: '123456' })
        userApi.getUserSettings()
        userApi.updateUserSettings(settingsData as never)
        userApi.getNotificationSettings()
        userApi.updateNotificationSettingsBulk(notificationBulkData)
        userApi.claimAgent('noviis_agt_xxx')
        userApi.getMyAgents()
        userApi.suspendMyAgent(7)
        userApi.deleteMyAgent(7)

        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/users/me')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/users/12')
        expect(apiMock.put).toHaveBeenNthCalledWith(1, '/users/me', profileData)
        expect(apiMock.put).toHaveBeenNthCalledWith(
            2,
            '/users/me/password',
            { currentPassword: 'old', newPassword: 'new' },
        )
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/users/me', { data: { password: 'secret' } })
        expect(apiMock.post).toHaveBeenNthCalledWith(
            1,
            '/users/me/email-verification',
            { email: 'test@example.com', code: '123456' },
        )
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/users/me/settings')
        expect(apiMock.put).toHaveBeenNthCalledWith(3, '/users/me/settings', settingsData)
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/users/me/notification-settings')
        expect(apiMock.put).toHaveBeenNthCalledWith(4, '/users/me/notification-settings/bulk', notificationBulkData)
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/users/me/agents/claim', { agentToken: 'noviis_agt_xxx' })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/users/me/agents')
        expect(apiMock.patch).toHaveBeenNthCalledWith(1, '/users/me/agents/7/suspend')
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/users/me/agents/7')
    })

    it('calls block and activity endpoints with params', () => {
        const params = { page: 1, size: 20, sort: 'latest' }

        userApi.blockUser(3)
        userApi.unblockUser(3)
        userApi.getBlockList()
        userApi.getMyPosts(params)
        userApi.getMyComments(params)
        userApi.getMyScraps(params)
        userApi.getMyDrafts(params)
        userApi.getRecentlyViewedPosts(params)
        userApi.getMySubscriptions(params)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/users/3/block')
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/users/3/block')
        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/users/me/blocks')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/users/me/posts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/users/me/comments', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/users/me/scraps', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/users/me/drafts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(6, '/users/me/history/views', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(7, '/users/me/subscriptions', { params })
    })
})
