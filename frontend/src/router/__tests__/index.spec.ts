import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import type { RouteRecordRaw } from 'vue-router'
import router from '../index'
import { useAuthStore } from '@/stores/auth'
import logger from '@/utils/logger'
import { boardApi } from '@/api/board'
import { emoticonApi } from '@/api/emoticon'
import { postApi } from '@/api/post'
import { postDetailQueryKey } from '@/composables/usePost'

const queryClientMock = vi.hoisted(() => ({
    fetchQuery: vi.fn(({ queryFn }: { queryFn: () => Promise<unknown> }) => queryFn())
}))

// Mock Auth Store
vi.mock('@/stores/auth', () => ({
    useAuthStore: vi.fn()
}))

vi.mock('@/queryClient', () => ({
    queryClient: queryClientMock
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        getBoard: vi.fn()
    }
}))

vi.mock('@/api/emoticon', () => ({
    emoticonApi: {
        getEmoticonData: vi.fn()
    }
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getPost: vi.fn()
    }
}))

// Mock View Components to prevent API calls during routing
vi.mock('@/views/PrivacyPolicy.vue', () => ({ default: { template: '<div>PrivacyPolicy</div>' } }))
vi.mock('@/views/TermsOfService.vue', () => ({ default: { template: '<div>TermsOfService</div>' } }))
vi.mock('@/views/common/ErrorPage.vue', () => ({ default: { template: '<div>ErrorPage</div>' } }))
vi.mock('@/views/user/MyPage.vue', () => ({ default: { template: '<div>MyPage</div>' } }))
vi.mock('@/views/user/MyPageDashboard.vue', () => ({ default: { template: '<div>MyPageDashboard</div>' } }))
vi.mock('@/views/user/UserSettings.vue', () => ({ default: { template: '<div>UserSettings</div>' } }))
vi.mock('@/views/user/PointHistory.vue', () => ({ default: { template: '<div>PointHistory</div>' } }))
vi.mock('@/views/user/ScrapList.vue', () => ({ default: { template: '<div>ScrapList</div>' } }))
vi.mock('@/views/user/MyMessages.vue', () => ({ default: { template: '<div>MyMessages</div>' } }))
vi.mock('@/views/user/MyNotifications.vue', () => ({ default: { template: '<div>MyNotifications</div>' } }))
vi.mock('@/views/user/MyReports.vue', () => ({ default: { template: '<div>MyReports</div>' } }))
vi.mock('@/views/user/BlockList.vue', () => ({ default: { template: '<div>BlockList</div>' } }))
vi.mock('@/views/user/SubscribedBoards.vue', () => ({ default: { template: '<div>SubscribedBoards</div>' } }))
vi.mock('@/views/home/HomeFeed.vue', () => ({ default: { template: '<div>HomeFeed</div>' } }))
vi.mock('@/views/search/SearchPage.vue', () => ({ default: { template: '<div>SearchPage</div>' } }))
vi.mock('@/views/search/RecentViewed.vue', () => ({ default: { template: '<div>RecentViewed</div>' } }))
vi.mock('@/views/auth/AuthLayout.vue', () => ({ default: { template: '<div>AuthLayout</div>' } }))
vi.mock('@/views/auth/LoginPage.vue', () => ({ default: { template: '<div>LoginPage</div>' } }))
vi.mock('@/views/auth/SignupPage.vue', () => ({ default: { template: '<div>SignupPage</div>' } }))
vi.mock('@/views/auth/FindAccountPage.vue', () => ({ default: { template: '<div>FindAccountPage</div>' } }))
vi.mock('@/views/auth/ForgotPasswordPage.vue', () => ({ default: { template: '<div>ForgotPasswordPage</div>' } }))
vi.mock('@/views/auth/ResetPasswordPage.vue', () => ({ default: { template: '<div>ResetPasswordPage</div>' } }))
vi.mock('@/views/auth/OAuthCallback.vue', () => ({ default: { template: '<div>OAuthCallback</div>' } }))
vi.mock('@/views/board/AllBoardsPage.vue', () => ({ default: { template: '<div>AllBoardsPage</div>' } }))
vi.mock('@/views/board/BoardCreate.vue', () => ({ default: { template: '<div>BoardCreate</div>' } }))
vi.mock('@/views/board/BoardEdit.vue', () => ({ default: { template: '<div>BoardEdit</div>' } }))
vi.mock('@/views/board/BoardDetail.vue', () => ({ default: { template: '<div>BoardDetail</div>' } }))
vi.mock('@/views/board/PostDetail.vue', () => ({ default: { template: '<div>PostDetail</div>' } }))
vi.mock('@/views/board/InquiryWrite.vue', () => ({ default: { template: '<div>InquiryWrite</div>' } }))
vi.mock('@/views/board/PostWrite.vue', () => ({ default: { template: '<div>PostWrite</div>' } }))
vi.mock('@/views/board/PostEdit.vue', () => ({ default: { template: '<div>PostEdit</div>' } }))
vi.mock('@/views/emoticon/EmoticonList.vue', () => ({ default: { template: '<div>EmoticonList</div>' } }))
vi.mock('@/views/emoticon/EmoticonRegister.vue', () => ({ default: { template: '<div>EmoticonRegister</div>' } }))
vi.mock('@/views/emoticon/EmoticonDetail.vue', () => ({ default: { template: '<div>EmoticonDetail</div>' } }))
vi.mock('@/views/emoticon/EmoticonEdit.vue', () => ({ default: { template: '<div>EmoticonEdit</div>' } }))
vi.mock('@/views/admin/AdminDashboard.vue', () => ({ default: { template: '<div>AdminDashboard</div>' } }))
vi.mock('@/views/admin/UserManagement.vue', () => ({ default: { template: '<div>UserManagement</div>' } }))
vi.mock('@/views/admin/BoardManagement.vue', () => ({ default: { template: '<div>BoardManagement</div>' } }))
vi.mock('@/views/admin/AdminInquiryPosts.vue', () => ({ default: { template: '<div>AdminInquiryPosts</div>' } }))
vi.mock('@/views/admin/AdminManagement.vue', () => ({ default: { template: '<div>AdminManagement</div>' } }))
vi.mock('@/views/admin/ReportManagement.vue', () => ({ default: { template: '<div>ReportManagement</div>' } }))
vi.mock('@/views/admin/SecuritySettings.vue', () => ({ default: { template: '<div>SecuritySettings</div>' } }))
vi.mock('@/views/admin/GlobalSettings.vue', () => ({ default: { template: '<div>GlobalSettings</div>' } }))

const collectLazyLoaders = (routes: RouteRecordRaw[]): Array<() => Promise<unknown>> => {
    const loaders: Array<() => Promise<unknown>> = []
    for (const route of routes) {
        if (typeof route.component === 'function') {
            loaders.push(route.component as () => Promise<unknown>)
        }
        if (route.children) {
            loaders.push(...collectLazyLoaders(route.children))
        }
    }
    return loaders
}

describe('Router Navigation Guards', () => {
    let mockAuthStore: any

    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        sessionStorage.clear()

        // Setup default auth store mock
        mockAuthStore = {
            user: null,
            accessToken: null,
            isAuthenticated: false,
            fetchUser: vi.fn(),
            logout: vi.fn().mockImplementation(() => {
                mockAuthStore.user = null
                mockAuthStore.isAuthenticated = false
            }),
            handleSanctionedSession: vi.fn().mockImplementation(() => {
                mockAuthStore.user = null
                mockAuthStore.isAuthenticated = false
            })
        }
        vi.mocked(useAuthStore).mockReturnValue(mockAuthStore)
        vi.mocked(boardApi.getBoard).mockResolvedValue({
            data: {
                data: {
                    isAdmin: false,
                    categories: [{ categoryId: 1, name: 'Open', minWriteRole: 'USER' }]
                }
            }
        } as never)
        vi.mocked(emoticonApi.getEmoticonData).mockResolvedValue({
            emoticonId: 7,
            creatorId: 1,
            name: 'Owned',
        } as never)
        vi.mocked(postApi.getPost).mockResolvedValue({
            data: {
                data: {
                    postId: 10,
                    title: 'Editable post',
                    contents: 'content',
                    viewCount: 0,
                    likeCount: 0,
                    commentCount: 0,
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    author: {
                        userId: 1,
                        displayName: 'Author',
                        authorType: 'USER',
                    },
                    board: {
                        boardId: 1,
                        boardName: 'Open',
                        boardUrl: 'open',
                    },
                    createdAt: '2026-05-27T00:00:00Z',
                }
            }
        } as never)
        history.pushState({}, '', '/')
    })

    it('redirects to login if requiresAuth and not authenticated', async () => {
        mockAuthStore.isAuthenticated = false
        await router.push('/mypage')
        expect(router.currentRoute.value.name).toBe('login')
        expect(router.currentRoute.value.query.redirect).toBe('/mypage')
    })

    it('allows navigation if requiresAuth and authenticated', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        await router.push('/mypage')
        expect(router.currentRoute.value.name).toBe('mypage')
    })

    it('redirects to home if guestOnly and authenticated', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        await router.push('/login')
        expect(router.currentRoute.value.name).toBe('home')
    })

    it('allows guestOnly login route after token-backed user hydration returns false', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = true
        mockAuthStore.fetchUser.mockResolvedValueOnce(false)

        await router.push('/login')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
        expect(mockAuthStore.logout).not.toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('login')
    })

    it('allows navigation if guestOnly and not authenticated', async () => {
        mockAuthStore.isAuthenticated = false
        await router.push('/login')
        expect(router.currentRoute.value.name).toBe('login')
    })

    it('redirects to home if role check fails', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        await router.push('/admin/dashboard')
        expect(router.currentRoute.value.name).toBe('home')
    })

    it('allows navigation if role check passes', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'SUPER_ADMIN' }
        await router.push('/admin/dashboard')
        expect(router.currentRoute.value.name).toBe('AdminDashboard')
    })

    it('checks board write permission before entering post write route', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }

        await router.push('/board/open/write')

        expect(boardApi.getBoard).toHaveBeenCalledWith('open')
        expect(router.currentRoute.value.name).toBe('post-write')
    })

    it('redirects to board detail when the user cannot write any category', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        vi.mocked(boardApi.getBoard).mockResolvedValueOnce({
            data: {
                data: {
                    isAdmin: false,
                    categories: [{ categoryId: 1, name: 'Admins', minWriteRole: 'BOARD_ADMIN' }]
                }
            }
        } as never)

        await router.push('/board/restricted/write')

        expect(router.currentRoute.value.name).toBe('board-detail')
        expect(router.currentRoute.value.params.boardUrl).toBe('restricted')
    })

    it('redirects to error when board write permission cannot be verified', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        vi.mocked(boardApi.getBoard).mockRejectedValueOnce(new Error('board failed'))

        await router.push('/board/error/write')

        expect(router.currentRoute.value.name).toBe('error')
        expect(router.currentRoute.value.query.status).toBe('500')
    })

    it('checks post author permission before entering post edit route', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { userId: 1, role: 'USER' }
        await router.push('/')

        await router.push('/board/open/post/10/edit')

        expect(queryClientMock.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
            queryKey: postDetailQueryKey('10', false),
        }))
        expect(postApi.getPost).toHaveBeenCalledWith('10', { params: { incrementView: false } })
        expect(router.currentRoute.value.name).toBe('post-edit')
    })

    it('redirects to post detail when the user is not the post author', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { userId: 2, role: 'USER' }
        await router.push('/')

        await router.push('/board/open/post/10/edit')

        expect(router.currentRoute.value.name).toBe('post-detail')
        expect(router.currentRoute.value.params.boardUrl).toBe('open')
        expect(router.currentRoute.value.params.postId).toBe('10')
    })

    it('redirects to error when post author permission cannot be verified', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { userId: 1, role: 'USER' }
        vi.mocked(postApi.getPost).mockRejectedValueOnce(new Error('post failed'))
        await router.push('/')

        await router.push('/board/open/post/10/edit')

        expect(router.currentRoute.value.name).toBe('error')
        expect(router.currentRoute.value.query.status).toBe('500')
    })

    it('checks board manager permission before entering board edit route', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        vi.mocked(boardApi.getBoard).mockResolvedValueOnce({
            data: {
                data: {
                    isAdmin: true,
                    categories: []
                }
            }
        } as never)

        await router.push('/board/free/edit')

        expect(boardApi.getBoard).toHaveBeenCalledWith('free')
        expect(router.currentRoute.value.name).toBe('board-edit')
    })

    it('redirects to board detail when the user cannot manage the board', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { role: 'USER' }
        vi.mocked(boardApi.getBoard).mockResolvedValueOnce({
            data: {
                data: {
                    isAdmin: false,
                    categories: []
                }
            }
        } as never)

        await router.push('/board/restricted/edit')

        expect(router.currentRoute.value.name).toBe('board-detail')
        expect(router.currentRoute.value.params.boardUrl).toBe('restricted')
    })

    it('checks emoticon ownership before entering emoticon edit route', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { userId: 1, role: 'USER' }

        await router.push('/emoticons/7/edit')

        expect(queryClientMock.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
            queryKey: ['emoticon', 7],
            retry: false,
        }))
        expect(emoticonApi.getEmoticonData).toHaveBeenCalledWith(7)
        expect(router.currentRoute.value.name).toBe('emoticon-edit')
    })

    it('redirects to emoticon detail when the user does not own the emoticon', async () => {
        mockAuthStore.isAuthenticated = true
        mockAuthStore.user = { userId: 1, role: 'USER' }
        vi.mocked(emoticonApi.getEmoticonData).mockResolvedValueOnce({
            emoticonId: 8,
            creatorId: 2,
            name: 'Not owned',
        } as never)

        await router.push('/emoticons/8/edit')

        expect(router.currentRoute.value.name).toBe('emoticon-detail')
        expect(router.currentRoute.value.params.emoticonId).toBe('8')
    })

    it('fetches user if token exists but user is missing', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = true // Assuming token implies authenticated for this check

        await router.push('/')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
    })

    it('redirects to login when fetchUser fails for protected route', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = false
        mockAuthStore.fetchUser.mockRejectedValueOnce(new Error('invalid token'))

        await router.push('/mypage')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
        expect(mockAuthStore.logout).toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('login')
        expect(router.currentRoute.value.query.redirect).toBe('/mypage')
    })

    it('redirects to error when fetchUser resolves without restoring user but token remains for protected route', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = true
        mockAuthStore.fetchUser.mockResolvedValueOnce(false)

        await router.push('/mypage')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
        expect(mockAuthStore.logout).not.toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('error')
        expect(router.currentRoute.value.query.status).toBe('503')
    })

    it('redirects to login when fetchUser clears auth for protected route', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = true
        mockAuthStore.fetchUser.mockImplementationOnce(async () => {
            mockAuthStore.isAuthenticated = false
            return false
        })

        await router.push('/mypage')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
        expect(mockAuthStore.logout).toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('login')
        expect(router.currentRoute.value.query.redirect).toBe('/mypage')
    })

    it('logs out but keeps navigation for public route when fetchUser fails', async () => {
        mockAuthStore.accessToken = 'token'
        mockAuthStore.user = null
        mockAuthStore.isAuthenticated = false
        mockAuthStore.fetchUser.mockRejectedValueOnce(new Error('invalid token'))

        await router.push('/')

        expect(mockAuthStore.fetchUser).toHaveBeenCalled()
        expect(mockAuthStore.logout).toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('home')
    })

    it('stores loginRedirect when entering guest-only route from non-guest page', async () => {
        mockAuthStore.isAuthenticated = false

        await router.push('/')
        await router.push('/login')

        expect(sessionStorage.getItem('loginRedirect')).toBe('/')
    })

    it('does not store loginRedirect for oauth callback', async () => {
        mockAuthStore.isAuthenticated = false

        await router.push('/')
        await router.push('/auth/oauth/callback')

        expect(sessionStorage.getItem('loginRedirect')).toBeNull()
    })

    it('logs out and redirects to login if user is sanctioned', async () => {
        mockAuthStore.user = { status: 'SANCTIONED' }
        mockAuthStore.isAuthenticated = true

        await router.push('/boards')

        expect(mockAuthStore.handleSanctionedSession).toHaveBeenCalled()
        expect(router.currentRoute.value.name).toBe('login')
    })

    it('applies scroll behavior for saved position, hash and default top', () => {
        const scrollBehavior = router.options.scrollBehavior!

        expect(scrollBehavior({ hash: '' } as any, {} as any, { left: 10, top: 20 })).toEqual({ left: 10, top: 20 })
        expect(scrollBehavior({ hash: '#section-1' } as any, {} as any, null)).toEqual({ el: '#section-1', behavior: 'smooth' })
        expect(scrollBehavior({ hash: '' } as any, {} as any, null)).toEqual({ top: 0 })
    })

    it('preserves scroll when paginating the board list with a post open', () => {
        const scrollBehavior = router.options.scrollBehavior!

        const from = {
            name: 'post-detail',
            hash: '',
            params: {
                boardUrl: 'free',
                postId: '15',
            },
            query: {
                page: '1',
            },
        }
        const to = {
            name: 'post-detail',
            hash: '',
            params: {
                boardUrl: 'free',
                postId: '15',
            },
            query: {
                page: '2',
            },
        }

        expect(scrollBehavior(to as any, from as any, null)).toBe(false)
    })

    it('clears chunk reload key in afterEach hook', async () => {
        sessionStorage.setItem('chunk-reload-attempted', '1')

        await router.push('/login')

        expect(sessionStorage.getItem('chunk-reload-attempted')).toBeNull()
    })

    it('redirects to error route after repeated chunk load failure', async () => {
        const pushSpy = vi.spyOn(router, 'push')
        const removeRoute = router.addRoute({
            path: '/chunk-fail-retry',
            name: 'chunk-fail-retry',
            component: () => Promise.reject(new Error('Importing a module script failed')) as any,
        })
        sessionStorage.setItem('chunk-reload-attempted', '1')

        await router.push('/chunk-fail-retry').catch(() => undefined)

        expect(sessionStorage.getItem('chunk-reload-attempted')).toBeNull()
        expect(pushSpy).toHaveBeenCalledWith({
            name: 'error',
            query: {
                status: '500',
                message: 'Chunk load failed after retry',
            },
        })

        removeRoute()
        pushSpy.mockRestore()
    })

    it('sets chunk reload marker on first chunk load failure', async () => {
        const removeRoute = router.addRoute({
            path: '/chunk-fail-first',
            name: 'chunk-fail-first',
            component: () => Promise.reject(new Error('Failed to fetch dynamically imported module')) as any,
        })
        sessionStorage.removeItem('chunk-reload-attempted')

        await router.push('/chunk-fail-first').catch(() => undefined)

        expect(sessionStorage.getItem('chunk-reload-attempted')).toBe('1')

        removeRoute()
        sessionStorage.removeItem('chunk-reload-attempted')
    })

    it('logs and redirects to error route for non-chunk navigation errors', async () => {
        const loggerSpy = vi.spyOn(logger, 'error').mockImplementation(() => undefined)
        const pushSpy = vi.spyOn(router, 'push')
        const removeRoute = router.addRoute({
            path: '/general-error',
            name: 'general-error',
            component: () => Promise.reject(new Error('general failure')) as any,
        })

        await router.push('/general-error').catch(() => undefined)

        expect(loggerSpy).toHaveBeenCalled()
        expect(pushSpy).toHaveBeenCalledWith({
            name: 'error',
            query: {
                status: '500',
                message: 'general failure',
            },
        })

        removeRoute()
        pushSpy.mockRestore()
        loggerSpy.mockRestore()
    })

    it('loads all lazy route component loaders', async () => {
        const loaders = collectLazyLoaders(router.options.routes as RouteRecordRaw[])
        const uniqueLoaders = Array.from(new Set(loaders))

        const results = await Promise.allSettled(uniqueLoaders.map((loader) => loader()))
        expect(results.every((result) => result.status === 'fulfilled')).toBe(true)
    }, 15000)
})
