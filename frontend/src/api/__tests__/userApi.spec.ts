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

import { toScrapPostSummaryPage, userApi } from '../user'

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
        userApi.verifyEmail({ email: 'test@example.com', verificationTicket: 'ticket-1' })
        userApi.getUserSettings()
        userApi.updateUserSettings(settingsData as never)
        userApi.getNotificationSettings()
        userApi.updateNotificationSettingsBulk(notificationBulkData)
        userApi.claimAgent('noviis_agt_xxx')
        userApi.getMyAgents()
        userApi.suspendMyAgent(7)
        userApi.activateMyAgent(7)
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
            { email: 'test@example.com', verificationTicket: 'ticket-1' },
        )
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/users/me/settings')
        expect(apiMock.put).toHaveBeenNthCalledWith(3, '/users/me/settings', settingsData)
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/users/me/notification-settings')
        expect(apiMock.put).toHaveBeenNthCalledWith(4, '/users/me/notification-settings/bulk', notificationBulkData)
        expect(apiMock.post).toHaveBeenNthCalledWith(2, '/users/me/agents/claim', { agentToken: 'noviis_agt_xxx' })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/users/me/agents')
        expect(apiMock.patch).toHaveBeenNthCalledWith(1, '/users/me/agents/7/suspend')
        expect(apiMock.patch).toHaveBeenNthCalledWith(2, '/users/me/agents/7/activate')
        expect(apiMock.delete).toHaveBeenNthCalledWith(2, '/users/me/agents/7')
    })

    it('calls block and activity endpoints with params', async () => {
        const params = { page: 1, size: 20, sort: 'latest' }
        apiMock.get.mockImplementation((url: string) => {
            if (url === '/users/me/scraps') {
                return Promise.resolve({
                    data: {
                        success: true,
                        data: {
                            content: [],
                            page: 1,
                            size: 20,
                            totalElements: 0,
                            totalPages: 0,
                            hasNext: false,
                            hasPrevious: true,
                            last: true,
                        },
                    },
                })
            }
            if (url === '/points/me/history') {
                return Promise.resolve({
                    data: {
                        success: true,
                        data: {
                            content: [],
                            page: 1,
                            size: 20,
                            totalElements: 0,
                            totalPages: 0,
                            hasNext: false,
                            hasPrevious: true,
                            last: true,
                        },
                    },
                })
            }
            return undefined
        })

        userApi.blockUser(3)
        userApi.unblockUser(3)
        userApi.getBlockList()
        userApi.getBlockList(params)
        userApi.getMyPosts(params)
        userApi.getMyComments(params)
        const scrapsResponse = await userApi.getMyScraps(params)
        userApi.getMyDrafts(params)
        userApi.getRecentlyViewedPosts(params)
        userApi.getMySubscriptions(params)
        userApi.getMyPoint()
        const pointResponse = await userApi.getMyPointHistories(params)

        expect(apiMock.post).toHaveBeenNthCalledWith(1, '/users/3/block')
        expect(apiMock.delete).toHaveBeenNthCalledWith(1, '/users/3/block')
        expect(apiMock.get).toHaveBeenNthCalledWith(1, '/users/me/blocks')
        expect(apiMock.get).toHaveBeenNthCalledWith(2, '/users/me/blocks', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(3, '/users/me/posts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(4, '/users/me/comments', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(5, '/users/me/scraps', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(6, '/users/me/drafts', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(7, '/users/me/history/views', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(8, '/users/me/subscriptions', { params })
        expect(apiMock.get).toHaveBeenNthCalledWith(9, '/points/me')
        expect(apiMock.get).toHaveBeenNthCalledWith(10, '/points/me/history', { params })
        expect(scrapsResponse.data.data.number).toBe(1)
        expect(pointResponse.data.data.number).toBe(1)
    })

    it('maps scrap list response to post summary page', () => {
        const result = toScrapPostSummaryPage({
            content: [{
                scrapId: 10,
                post: {
                    postId: 33,
                    title: 'Saved post',
                    boardName: 'Free',
                    boardUrl: 'free',
                    viewCount: 11,
                    likeCount: 3,
                    commentCount: 4,
                    isNotice: true,
                    author: {
                        userId: 7,
                        displayName: 'writer',
                    },
                    createdAt: '2026-05-19T12:00:00',
                    rowNum: 5,
                },
                remark: null,
                createdAt: '2026-05-19T12:30:00',
            }],
            page: 2,
            size: 15,
            totalElements: 31,
            totalPages: 3,
            hasNext: false,
            hasPrevious: true,
            last: true,
        })

        expect(result.number).toBe(2)
        expect(result.totalElements).toBe(31)
        expect(result.content[0]).toMatchObject({
            postId: 33,
            title: 'Saved post',
            boardName: 'Free',
            boardUrl: 'free',
            commentCount: 4,
            isNotice: true,
            isNsfw: false,
            isSpoiler: false,
            isSecret: false,
            scrapped: true,
        })
    })

    it('preserves failed page envelopes without mapping missing data', async () => {
        apiMock.get.mockResolvedValueOnce({
            data: {
                success: false,
                message: 'failed',
                data: null,
            },
        })

        const response = await userApi.getMyScraps({ page: 0, size: 15 })

        expect(response.data.success).toBe(false)
        expect(response.data.data).toBeNull()
    })
})
