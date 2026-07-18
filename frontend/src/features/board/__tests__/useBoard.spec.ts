import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { useBoard } from '../useBoard'
import { boardApi } from '@/api/board'
import { userApi } from '@/api/user'
import { searchApi } from '@/api/search'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({ sessionGeneration: 0 }),
}))

const mocks = vi.hoisted(() => {
    const invalidateQueries = vi.fn()
    const removeQueries = vi.fn()
    const queryOptions: Array<Record<string, unknown>> = []
    const infiniteQueryOptions: Array<Record<string, unknown>> = []
    const mutationOptions: Array<Record<string, unknown>> = []

    return {
        invalidateQueries,
        removeQueries,
        queryOptions,
        infiniteQueryOptions,
        mutationOptions,
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({
        invalidateQueries: mocks.invalidateQueries,
        removeQueries: mocks.removeQueries,
    }),
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: async () => {
                if (options.queryFn) {
                    return await (options.queryFn as () => Promise<unknown>)()
                }
                return null
            },
        }
    }),
    useInfiniteQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.infiniteQueryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            isFetching: ref(false),
            isFetchingNextPage: ref(false),
            hasNextPage: ref(false),
            error: ref(null),
            fetchNextPage: vi.fn(),
        }
    }),
    useMutation: vi.fn((options: Record<string, unknown>) => {
      mocks.mutationOptions.push(options)
      const mutationFn = options.mutationFn as (variables: unknown) => Promise<unknown>
      const onMutate = options.onMutate as ((variables: unknown) => unknown) | undefined
      const onSuccess = options.onSuccess as ((data: unknown, variables: unknown, context: unknown) => unknown) | undefined
      const onError = options.onError as ((error: unknown, variables: unknown, context: unknown) => unknown) | undefined
      const onSettled = options.onSettled as ((data: unknown, error: unknown, variables: unknown, context: unknown) => unknown) | undefined
      return {
        mutateAsync: async (variables: unknown) => {
            const context = await onMutate?.(variables)
            try {
                const result = await mutationFn(variables)
                await onSuccess?.(result, variables, context)
                await onSettled?.(result, null, variables, context)
                return result
            } catch (error) {
                await onError?.(error, variables, context)
                await onSettled?.(undefined, error, variables, context)
                throw error
            }
        },
      }
    }),
}))

vi.mock('@/api/board', () => ({
    boardApi: {
        getBoards: vi.fn(),
        getBoard: vi.fn(),
        getPosts: vi.fn(),
        getNotices: vi.fn(),
        subscribeBoard: vi.fn(),
        unsubscribeBoard: vi.fn(),
        getCategories: vi.fn(),
        createBoard: vi.fn(),
        updateBoard: vi.fn(),
        updateBoardManager: vi.fn(),
        getBoardManagerCandidates: vi.fn(),
        deleteBoard: vi.fn(),
    },
}))

vi.mock('@/api/user', () => ({
    userApi: {
        getMySubscriptions: vi.fn(),
    },
}))

vi.mock('@/api/search', () => ({
    searchApi: {
        searchPosts: vi.fn(),
    },
}))

describe('useBoard', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
        mocks.infiniteQueryOptions.length = 0
        mocks.mutationOptions.length = 0
    })

    it('fetches boards with medium staleTime', async () => {
        vi.mocked(boardApi.getBoards).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getBoards>([{ boardId: 1 }])
        )

        const { useBoards } = useBoard()
        const query = useBoards()
        const options = mocks.queryOptions.at(-1)!
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session', 0, 'boards',
        ])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.MEDIUM)
        expect(options.meta).toEqual({ authScoped: true, errorMessage: false })
        expect(result).toEqual([{ boardId: 1 }])
        expect(boardApi.getBoards).toHaveBeenCalledWith({ skipGlobalErrorHandler: true })
        expect(query).toHaveProperty('data')
    })

    it('fetches subscribed boards and supports enabled variations', async () => {
        vi.mocked(userApi.getMySubscriptions).mockResolvedValue(
            apiDataResponse<typeof userApi.getMySubscriptions>({
                content: [
                    { boardId: 11, boardName: 'General', accessState: 'ACCESSIBLE' },
                    {
                        boardId: 12,
                        boardName: null,
                        accessState: 'INACCESSIBLE',
                        inaccessibleReason: 'PRIVATE',
                    },
                ],
            })
        )

        const { useSubscribedBoards } = useBoard()

        useSubscribedBoards()
        let options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session', 0, 'boards', 'subscriptions', 10,
        ])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)

        useSubscribedBoards(5, true)
        options = mocks.queryOptions.at(-1)!
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(userApi.getMySubscriptions).toHaveBeenCalledWith({ size: 5 })
        expect(result).toEqual([{ boardId: 11, boardName: 'General', accessState: 'ACCESSIBLE' }])

        useSubscribedBoards(3, ref(false))
        options = mocks.queryOptions.at(-1)!
        expect((options.enabled as { value: boolean }).value).toBe(false)
    })

    it('fetches board detail with enabled guards', async () => {
        vi.mocked(boardApi.getBoard).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getBoard>({ boardId: 2, boardUrl: 'free' })
        )
        const { useBoardDetail } = useBoard()
        const boardUrl = ref('free')

        useBoardDetail(boardUrl)
        const options = mocks.queryOptions.at(-1)!
        const queryKey = options.queryKey as ReturnType<typeof computed>
        expect(queryKey.value).toEqual(['session', 0, 'board', 'detail', 'free'])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({ boardId: 2, boardUrl: 'free' })
        expect(boardApi.getBoard).toHaveBeenCalledWith('free')

        boardUrl.value = 'notice'
        expect(queryKey.value).toEqual(['session', 0, 'board', 'detail', 'notice'])
    })

    it('fetches board notices with enabled guards', async () => {
        vi.mocked(boardApi.getNotices).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getNotices>([{ postId: 11, title: 'Notice' }])
        )
        const { useBoardNotices } = useBoard()
        const boardUrl = ref('free')
        const enabled = ref(true)

        useBoardNotices(boardUrl, enabled)
        const options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session', 0, 'board', 'notices', 'free',
        ])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual([{ postId: 11, title: 'Notice' }])
        expect(boardApi.getNotices).toHaveBeenCalledWith('free')
    })

    it('fetches board posts through boardApi when not searching', async () => {
        vi.mocked(boardApi.getPosts).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getPosts>({ content: [{ postId: 1 }] })
        )

        const { useBoardPosts } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 0, size: 20, sort: 'latest' })
        const isSearching = ref(false)
        useBoardPosts(boardUrl, params, isSearching)

        const options = mocks.queryOptions.at(-1)!
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        expect(options.placeholderData).toBeUndefined()
        expect(boardApi.getPosts).toHaveBeenCalledWith('free', { page: 0, size: 20, sort: 'latest' })
        expect(result).toEqual({ content: [{ postId: 1 }] })

        const queryKey = options.queryKey as ReturnType<typeof computed>
        expect(queryKey.value).toEqual([
            'session',
            0,
            'board',
            'posts',
            'free',
            { page: 0, size: 20, sort: 'latest' },
            false,
        ])
        boardUrl.value = 'notice'
        params.value = { page: 1, size: 10, sort: 'oldest' }
        isSearching.value = true
        expect(queryKey.value).toEqual([
            'session',
            0,
            'board',
            'posts',
            'notice',
            { page: 1, size: 10, sort: 'oldest' },
            true,
        ])
    })

    it('disables board posts when boardUrl is empty or caller enabled flag is false', () => {
        const { useBoardPosts } = useBoard()
        const params = ref({ page: 0, size: 20 })

        useBoardPosts(ref(''), params, ref(false))
        const emptyBoardUrlOptions = mocks.queryOptions.at(-1)!
        expect((emptyBoardUrlOptions.enabled as ReturnType<typeof computed>).value).toBe(false)

        useBoardPosts(ref('free'), params, ref(false), ref(false))
        const disabledOptions = mocks.queryOptions.at(-1)!
        expect((disabledOptions.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('fetches board posts through searchApi when searching', async () => {
        vi.mocked(searchApi.searchPosts).mockResolvedValueOnce(
            apiDataResponse<typeof searchApi.searchPosts>({ content: [{ postId: 2 }] })
        )

        const { useBoardPosts } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 1, size: 10, q: 'keyword', searchType: 'TITLE' })
        const isSearching = ref(true)
        useBoardPosts(boardUrl, params, isSearching)

        const options = mocks.queryOptions.at(-1)!
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect(searchApi.searchPosts).toHaveBeenCalledWith({
            page: 1,
            size: 10,
            q: 'keyword',
            searchType: 'TITLE',
            boardUrl: 'free',
        })
        expect(result).toEqual({ content: [{ postId: 2 }] })
    })

    it('forwards board post requestConfig and query options when provided', async () => {
        vi.mocked(boardApi.getPosts).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getPosts>({ content: [] })
        )

        const { useBoardPosts } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 0, size: 20, sort: 'createdAt,desc' })

        useBoardPosts(boardUrl, params, ref(false), ref(true), {
            meta: { errorMessage: false },
            requestConfig: { skipGlobalErrorHandler: true }
        })

        const options = mocks.queryOptions.at(-1)!
        await (options.queryFn as () => Promise<unknown>)()

        expect(options.meta).toEqual({ errorMessage: false, authScoped: true })
        expect(boardApi.getPosts).toHaveBeenCalledWith(
            'free',
            { page: 0, size: 20, sort: 'createdAt,desc' },
            { skipGlobalErrorHandler: true }
        )
    })

    it('forwards search requestConfig when board posts are fetched through searchApi', async () => {
        vi.mocked(searchApi.searchPosts).mockResolvedValueOnce(
            apiDataResponse<typeof searchApi.searchPosts>({ content: [] })
        )

        const { useBoardPosts } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 1, size: 10, q: 'keyword', searchType: 'TITLE' })

        useBoardPosts(boardUrl, params, ref(true), ref(true), {
            requestConfig: { skipGlobalErrorHandler: true }
        })

        const options = mocks.queryOptions.at(-1)!
        await (options.queryFn as () => Promise<unknown>)()

        expect(searchApi.searchPosts).toHaveBeenCalledWith(
            {
                page: 1,
                size: 10,
                q: 'keyword',
                searchType: 'TITLE',
                boardUrl: 'free',
            },
            { skipGlobalErrorHandler: true }
        )
    })

    it('fetches infinite board posts with pageParam while excluding page from the cache params', async () => {
        vi.mocked(boardApi.getPosts).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getPosts>({
                content: [{ postId: 3 }],
                number: 2,
                last: false,
            })
        )

        const { useInfiniteBoardPosts } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 7, size: 20, sort: 'createdAt,desc', categoryId: 2 })

        useInfiniteBoardPosts(boardUrl, params, ref(false), ref(true), {
            requestConfig: { skipGlobalErrorHandler: true }
        })

        const options = mocks.infiniteQueryOptions.at(-1)!
        const result = await (options.queryFn as (context: { pageParam: number, signal?: AbortSignal }) => Promise<unknown>)({
            pageParam: 2,
        })
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session',
            0,
            'board',
            'posts',
            'infinite',
            'free',
            { size: 20, sort: 'createdAt,desc', categoryId: 2 },
            false,
        ])
        expect(boardApi.getPosts).toHaveBeenCalledWith(
            'free',
            { size: 20, sort: 'createdAt,desc', categoryId: 2, page: 2 },
            { skipGlobalErrorHandler: true }
        )
        expect((options.getNextPageParam as (page: { number: number, last: boolean }) => number | undefined)({
            number: 2,
            last: false,
        })).toBe(3)
        expect((options.getNextPageParam as (page: { number: number, last: boolean }) => number | undefined)({
            number: 3,
            last: true,
        })).toBeUndefined()
        expect(result).toEqual({ content: [{ postId: 3 }], number: 2, last: false })
    })

    it('fetches board categories', async () => {
        vi.mocked(boardApi.getCategories).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getCategories>([{ categoryId: 1, name: 'notice' }])
        )

        const { useBoardCategories } = useBoard()
        const boardUrl = ref('free')
        useBoardCategories(boardUrl)
        const options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session', 0, 'board', 'categories', 'free',
        ])
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect(boardApi.getCategories).toHaveBeenCalledWith('free')
        expect(result).toEqual([{ categoryId: 1, name: 'notice' }])
    })

    it('disables category queries when boardUrl is empty', () => {
        const { useBoardCategories } = useBoard()
        const emptyBoardUrl = ref('')

        useBoardCategories(emptyBoardUrl)
        const options = mocks.queryOptions.at(-1)!
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('subscribes or unsubscribes and invalidates related caches', async () => {
        vi.mocked(boardApi.subscribeBoard).mockResolvedValueOnce(apiSuccessResponse<typeof boardApi.subscribeBoard>())
        vi.mocked(boardApi.unsubscribeBoard).mockResolvedValueOnce(apiSuccessResponse<typeof boardApi.unsubscribeBoard>())

        const { useSubscribeBoard } = useBoard()
        const mutation = useSubscribeBoard()

        await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: false })
        expect(boardApi.subscribeBoard).toHaveBeenCalledWith('free')

        await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: true })
        expect(boardApi.unsubscribeBoard).toHaveBeenCalledWith('free')

        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })

    it('forwards subscribe requestConfig when provided', async () => {
        vi.mocked(boardApi.subscribeBoard).mockResolvedValueOnce(apiSuccessResponse<typeof boardApi.subscribeBoard>())
        vi.mocked(boardApi.unsubscribeBoard).mockResolvedValueOnce(apiSuccessResponse<typeof boardApi.unsubscribeBoard>())

        const { useSubscribeBoard } = useBoard()
        const mutation = useSubscribeBoard({
            requestConfig: { skipGlobalErrorHandler: true }
        })

        await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: false })
        expect(boardApi.subscribeBoard).toHaveBeenCalledWith('free', { skipGlobalErrorHandler: true })

        await mutation.mutateAsync({ boardUrl: 'free', isSubscribed: true })
        expect(boardApi.unsubscribeBoard).toHaveBeenCalledWith('free', { skipGlobalErrorHandler: true })
    })

    it('creates board and invalidates board lists', async () => {
        vi.mocked(boardApi.createBoard).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.createBoard>({ boardId: 3, boardUrl: 'new' })
        )

        const { useCreateBoard } = useBoard()
        const mutation = useCreateBoard()
        const result = await mutation.mutateAsync({ boardName: 'New board', boardUrl: 'new' })

        expect(boardApi.createBoard).toHaveBeenCalledWith(
            { boardName: 'New board', boardUrl: 'new' },
            { signal: undefined, skipGlobalErrorHandler: true },
        )
        expect(result).toEqual({ boardId: 3, boardUrl: 'new' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })

    it('assigns local error ownership to board form mutations', () => {
        const {
            useCreateBoard,
            useUpdateBoard,
            useTransferBoardManager,
            useDeleteBoard,
        } = useBoard()

        useCreateBoard()
        useUpdateBoard()
        useTransferBoardManager()
        useDeleteBoard()

        expect(mocks.mutationOptions.map((options) => options.meta)).toEqual([
            { errorMessage: false },
            { errorMessage: false },
            { errorMessage: false },
            { errorMessage: false },
        ])
    })

    it('updates board and invalidates detail plus lists', async () => {
        vi.mocked(boardApi.updateBoard).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.updateBoard>({ boardId: 4, boardUrl: 'free', boardName: 'updated' })
        )

        const { useUpdateBoard } = useBoard()
        const mutation = useUpdateBoard()
        const result = await mutation.mutateAsync({ boardUrl: 'free', data: { boardName: 'updated' } })

        expect(boardApi.updateBoard).toHaveBeenCalledWith(
            'free',
            { boardName: 'updated' },
            { signal: undefined, skipGlobalErrorHandler: true },
        )
        expect(result).toEqual({ boardId: 4, boardUrl: 'free', boardName: 'updated' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })

    it('invalidates old and new board detail caches when board URL changes', async () => {
        vi.mocked(boardApi.updateBoard).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.updateBoard>({ boardId: 4, boardUrl: 'new-free', boardName: 'updated' })
        )

        const { useUpdateBoard } = useBoard()
        const mutation = useUpdateBoard()
        await mutation.mutateAsync({ boardUrl: 'free', data: { boardName: 'updated', boardUrl: 'new-free' } })

        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'infinite', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'notices', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'categories', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'manager-candidates', 'free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'infinite', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'notices', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'categories', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'manager-candidates', 'new-free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })

    it('transfers board manager and invalidates detail plus lists', async () => {
        vi.mocked(boardApi.updateBoardManager).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.updateBoardManager>({ boardId: 4, boardUrl: 'free', adminDisplayName: 'manager' })
        )

        const { useTransferBoardManager } = useBoard()
        const mutation = useTransferBoardManager()
        const result = await mutation.mutateAsync({ boardUrl: 'free', loginId: 'manager' })

        expect(boardApi.updateBoardManager).toHaveBeenCalledWith(
            'free',
            { loginId: 'manager' },
            { skipGlobalErrorHandler: true },
        )
        expect(result).toEqual({ boardId: 4, boardUrl: 'free', adminDisplayName: 'manager' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })

    it('fetches board manager candidates with enabled guard', async () => {
        vi.mocked(boardApi.getBoardManagerCandidates).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.getBoardManagerCandidates>({
                content: [{ userId: 1, loginId: 'manager', currentManager: true }],
            })
        )

        const { useBoardManagerCandidates } = useBoard()
        const boardUrl = ref('free')
        const params = ref({ page: 0, size: 10, q: 'manager' })
        const enabled = ref(false)

        useBoardManagerCandidates(boardUrl, params, enabled)
        let options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session',
            0,
            'board',
            'manager-candidates',
            'free',
            { page: 0, size: 10, q: 'manager' },
        ])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)

        enabled.value = true
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect(boardApi.getBoardManagerCandidates).toHaveBeenCalledWith('free', { page: 0, size: 10, q: 'manager' })
        expect(result).toEqual({
            content: [{ userId: 1, loginId: 'manager', currentManager: true }],
            empty: false,
            first: true,
            last: true,
            number: 0,
            size: 1,
            totalElements: 1,
            totalPages: 1,
        })

        boardUrl.value = ''
        useBoardManagerCandidates(boardUrl, params, ref(true))
        options = mocks.queryOptions.at(-1)!
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('deletes board and invalidates board lists', async () => {
        vi.mocked(boardApi.deleteBoard).mockResolvedValueOnce(
            apiDataResponse<typeof boardApi.deleteBoard>(null)
        )

        const { useDeleteBoard } = useBoard()
        const mutation = useDeleteBoard()
        const result = await mutation.mutateAsync('free')

        expect(boardApi.deleteBoard).toHaveBeenCalledWith('free', { skipGlobalErrorHandler: true })
        expect(result).toBeNull()
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'detail', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts', 'infinite', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'notices', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'categories', 'free'] })
        expect(mocks.removeQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'manager-candidates', 'free'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    })
})
