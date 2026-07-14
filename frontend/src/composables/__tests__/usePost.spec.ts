import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { postDetailQueryKey, usePost } from '../usePost'
import { postApi } from '@/api/post'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'

type QueryKey = unknown[]
type StoreEntry = { key: QueryKey; data: unknown }

const mocks = vi.hoisted(() => {
    const queryStore = new Map<string, StoreEntry>()
    const queryOptions: Array<Record<string, unknown>> = []

    const keyToString = (key: QueryKey) => JSON.stringify(key)

    const getQueryData = vi.fn((key: QueryKey) => queryStore.get(keyToString(key))?.data)

    const setQueryData = vi.fn((key: QueryKey, updater: unknown) => {
        const keyStr = keyToString(key)
        const oldValue = queryStore.get(keyStr)?.data
        const nextValue =
            typeof updater === 'function'
                ? (updater as (old: unknown) => unknown)(oldValue)
                : updater
        queryStore.set(keyStr, { key, data: nextValue })
        return nextValue
    })

    const getQueriesData = vi.fn((filters: { queryKey?: QueryKey }) => {
        const prefix = filters.queryKey ?? []
        return Array.from(queryStore.values())
            .filter(({ key }) => prefix.every((segment, index) => key[index] === segment))
            .map(({ key, data }) => [key, data] as [QueryKey, unknown])
    })

    const setQueriesData = vi.fn((filters: { queryKey?: QueryKey }, updater: (old: unknown) => unknown) => {
        const prefix = filters.queryKey ?? []
        const entries = Array.from(queryStore.values()).filter(({ key }) =>
            prefix.every((segment, index) => key[index] === segment),
        )
        entries.forEach(({ key, data }) => {
            queryStore.set(keyToString(key), { key, data: updater(data) })
        })
    })

    return {
        queryStore,
        queryOptions,
        keyToString,
        getQueryData,
        setQueryData,
        getQueriesData,
        setQueriesData,
        cancelQueries: vi.fn(),
        invalidateQueries: vi.fn(),
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        getQueryData: mocks.getQueryData,
        setQueryData: mocks.setQueryData,
        getQueriesData: mocks.getQueriesData,
        setQueriesData: mocks.setQueriesData,
        cancelQueries: mocks.cancelQueries,
        invalidateQueries: mocks.invalidateQueries,
    })),
    useQuery: vi.fn((options) => {
        mocks.queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
        }
    }),
    useMutation: vi.fn(({ mutationFn, onMutate, onSuccess, onError, onSettled }) => ({
        mutate: async (variables: unknown) => {
            let context: unknown
            let error: unknown
            try {
                if (onMutate) {
                    context = await onMutate(variables)
                }
                const result = await mutationFn(variables)
                if (onSuccess) {
                    onSuccess(result, variables, context)
                }
                return result
            } catch (err) {
                error = err
                if (onError) {
                    onError(err, variables, context)
                }
                throw err
            } finally {
                if (onSettled) {
                    onSettled(undefined, error, variables, context)
                }
            }
        },
    })),
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getPost: vi.fn(),
        createPost: vi.fn(),
        updatePost: vi.fn(),
        deletePost: vi.fn(),
        likePost: vi.fn(),
        unlikePost: vi.fn(),
        scrapPost: vi.fn(),
        unscrapPost: vi.fn(),
        reportPost: vi.fn(),
    },
}))

const seedQueryData = (key: QueryKey, data: unknown) => {
    mocks.queryStore.set(mocks.keyToString(key), { key, data })
}

const getQueryDataValue = (key: QueryKey) => mocks.queryStore.get(mocks.keyToString(key))?.data

type TestPostCacheItem = {
    postId: number
    likeCount?: number
    liked?: boolean
    scrapped?: boolean
}

type TestInfinitePostCache = {
    pages: Array<{ content: TestPostCacheItem[] | string }>
    pageParams?: unknown[]
}

type TestPostPageCache = {
    content: TestPostCacheItem[]
}

const getInfinitePostCache = (key: QueryKey) => getQueryDataValue(key) as TestInfinitePostCache

const getInfinitePostContent = (key: QueryKey, pageIndex = 0) => getInfinitePostCache(key).pages[pageIndex].content

const getInfinitePostItem = (key: QueryKey, itemIndex: number, pageIndex = 0) => {
    const content = getInfinitePostContent(key, pageIndex)
    if (!Array.isArray(content)) {
        throw new Error(`Expected post cache content to be an array for ${JSON.stringify(key)}`)
    }

    return content[itemIndex]
}

const getPostPageItem = (key: QueryKey, itemIndex: number) => (getQueryDataValue(key) as TestPostPageCache).content[itemIndex]

describe('usePost', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryStore.clear()
        mocks.queryOptions.length = 0
    })

    it('registers post detail query and fetches post data with incrementView', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce(
            apiDataResponse<typeof postApi.getPost>({
                postId: 1,
                title: 'Test Post',
                liked: false,
                scrapped: false,
            })
        )

        const { usePostDetail } = usePost()
        const postId = ref(1)
        usePostDetail(postId)

        const query = mocks.queryOptions.at(-1)!
        expect(query.queryKey).toEqual(postDetailQueryKey(postId))
        expect((query.enabled as ReturnType<typeof computed>).value).toBe(true)

        const result = await (query.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({ postId: 1, title: 'Test Post', liked: false, scrapped: false })
        expect(postApi.getPost).toHaveBeenCalledWith(1, { params: { incrementView: true } })
    })

    it('registers post detail query without incrementing views when requested', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce(
            apiDataResponse<typeof postApi.getPost>({ postId: 1, title: 'Edit Post' })
        )

        const { usePostDetail } = usePost()
        const postId = ref(1)
        usePostDetail(postId, { requestConfig: { params: { incrementView: false } } })

        const query = mocks.queryOptions.at(-1)!
        expect(query.queryKey).toEqual(postDetailQueryKey(postId, false))

        await (query.queryFn as () => Promise<unknown>)()
        expect(postApi.getPost).toHaveBeenCalledWith(1, { params: { incrementView: false } })
    })

    it('uses normalized post detail reaction flags from the API response', async () => {
        vi.mocked(postApi.getPost).mockResolvedValueOnce(
            apiDataResponse<typeof postApi.getPost>({
                postId: 1,
                title: 'Alias Post',
                liked: true,
                scrapped: true,
            })
        )

        const { usePostDetail } = usePost()
        usePostDetail(ref(1))

        const query = mocks.queryOptions.at(-1)!
        const result = await (query.queryFn as () => Promise<unknown>)()

        expect(result).toMatchObject({
            postId: 1,
            liked: true,
            scrapped: true,
        })
    })

    it('disables post detail query when postId is falsy', () => {
        const { usePostDetail } = usePost()
        const postId = ref(0)
        usePostDetail(postId)

        const query = mocks.queryOptions.at(-1)!
        expect((query.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('invalidates all board post list queries after create', async () => {
        vi.mocked(postApi.createPost).mockResolvedValueOnce(
            apiDataResponse<typeof postApi.createPost>({ postId: 2 })
        )

        const { useCreatePost } = usePost()
        const mutation = useCreatePost()
        await mutation.mutate({ boardUrl: 'free', data: { title: 'new', contents: 'body' } })

        expect(postApi.createPost).toHaveBeenCalledWith('free', { title: 'new', contents: 'body' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['board', 'posts'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['home', 'landing'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'points'] })
    })

    it('invalidates related post caches after update', async () => {
        vi.mocked(postApi.updatePost).mockResolvedValueOnce(
            apiDataResponse<typeof postApi.updatePost>({ postId: 1 })
        )

        const { useUpdatePost } = usePost()
        const mutation = useUpdatePost()
        await mutation.mutate({ postId: 1, data: { title: 'updated' } })

        expect(postApi.updatePost).toHaveBeenCalledWith(1, { title: 'updated' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['posts'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['home', 'landing'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            predicate: expect.any(Function),
        })
    })

    it('invalidates board queries after delete', async () => {
        vi.mocked(postApi.deletePost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.deletePost>())

        const { useDeletePost } = usePost()
        const mutation = useDeletePost()
        await mutation.mutate(1)

        expect(postApi.deletePost).toHaveBeenCalledWith(1)
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['board', 'posts'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['home', 'landing'] })
    })

    it('applies optimistic like updates across all caches and invalidates on settle', async () => {
        vi.mocked(postApi.likePost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.likePost>())

        seedQueryData(['post', 1], { postId: 1, likeCount: 2, liked: false })
        seedQueryData(['posts'], {
            pages: [
                { content: [{ postId: 1, likeCount: 2, liked: false }, { postId: 2, likeCount: 7, liked: false }] },
                { content: 'not-an-array' },
            ],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'free'], {
            pages: [{ content: [{ postId: 1, likeCount: 2, liked: false }, { postId: 99, likeCount: 10, liked: false }] }],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'hot'], {
            content: [{ postId: 1, likeCount: 2, liked: false }],
        })
        seedQueryData(['board', 'detail', 'free'], { anything: true })

        const { useLikePost } = usePost()
        const mutation = useLikePost()
        await mutation.mutate(1)

        expect(mocks.cancelQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
        expect(mocks.cancelQueries).toHaveBeenCalledWith({ queryKey: ['posts'] })
        expect(getQueryDataValue(['post', 1])).toMatchObject({ liked: true, likeCount: 3 })
        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({ liked: true, likeCount: 3 })
        expect(getInfinitePostItem(['posts'], 1)).toMatchObject({ postId: 2, likeCount: 7, liked: false })
        expect(getInfinitePostContent(['posts'], 1)).toBe('not-an-array')
        expect(getInfinitePostItem(['board', 'posts', 'free'], 0)).toMatchObject({ liked: true, likeCount: 3 })
        expect(getInfinitePostItem(['board', 'posts', 'free'], 1)).toMatchObject({ postId: 99, likeCount: 10, liked: false })
        expect(getPostPageItem(['board', 'posts', 'hot'], 0)).toMatchObject({ liked: true, likeCount: 3 })
        expect(postApi.likePost).toHaveBeenCalledWith(1)
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['posts'] })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({
            predicate: expect.any(Function),
        })
    })

    it('handles missing/unsupported cache shapes during optimistic like update', async () => {
        vi.mocked(postApi.likePost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.likePost>())

        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 2, likeCount: 10 }, { postId: 1, liked: false }] }],
            pageParams: [],
        })
        seedQueryData(['posts', 'recent'], { content: [{ postId: 1 }] })
        seedQueryData(['board', 'posts', 'mix'], {
            pages: [{ content: 'not-an-array' }],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'page'], {
            content: [{ postId: 2, liked: false }, { postId: 1, liked: false }],
        })
        seedQueryData(['board', 'posts', 'empty'], undefined)
        seedQueryData(['board', 'posts', 'odd'], { anything: true })

        const { useLikePost } = usePost()
        await useLikePost().mutate(1)

        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({
            postId: 2,
            likeCount: 10,
        })
        expect(getInfinitePostItem(['posts'], 1)).toMatchObject({
            postId: 1,
            liked: true,
            likeCount: 1,
        })
        expect(getQueryDataValue(['posts', 'recent'])).toEqual({ content: [{ postId: 1 }] })
        expect(getPostPageItem(['board', 'posts', 'page'], 0)).toMatchObject({ postId: 2, liked: false })
        expect(getPostPageItem(['board', 'posts', 'page'], 1)).toMatchObject({ postId: 1, liked: true })
        expect(getQueryDataValue(['board', 'posts', 'mix'])).toEqual({
            pages: [{ content: 'not-an-array' }],
            pageParams: [],
        })
        expect(getQueryDataValue(['board', 'posts', 'empty'])).toBeUndefined()
        expect(getQueryDataValue(['board', 'posts', 'odd'])).toEqual({ anything: true })

        const predicate = mocks.invalidateQueries.mock.calls.find((call) => call[0]?.predicate)?.[0]?.predicate as
            | ((query: { queryKey: unknown }) => boolean)
            | undefined
        expect(predicate).toBeTypeOf('function')
        expect(predicate?.({ queryKey: ['board', 'posts', 'free'] })).toBe(true)
        expect(predicate?.({ queryKey: ['board', 'detail', 'free'] })).toBe(false)
        expect(predicate?.({ queryKey: 'not-array' })).toBe(false)
    })

    it('rolls back optimistic like update when API call fails', async () => {
        vi.mocked(postApi.likePost).mockRejectedValueOnce(new Error('like failed'))

        const snapshot = { postId: 1, likeCount: 4, liked: false }
        seedQueryData(['post', 1], snapshot)
        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 1, likeCount: 4, liked: false }] }],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'free'], {
            content: [{ postId: 1, likeCount: 4, liked: false }],
        })

        const { useLikePost } = usePost()
        const mutation = useLikePost()

        await expect(mutation.mutate(1)).rejects.toThrow('like failed')

        expect(getQueryDataValue(['post', 1])).toEqual(snapshot)
        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({
            likeCount: 4,
            liked: false,
        })
        expect(getPostPageItem(['board', 'posts', 'free'], 0)).toMatchObject({
            likeCount: 4,
            liked: false,
        })
    })

    it('unlikes without dropping below zero', async () => {
        vi.mocked(postApi.unlikePost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.unlikePost>())
        seedQueryData(['post', 1], { postId: 1, likeCount: 0, liked: true })

        const { useUnlikePost } = usePost()
        const mutation = useUnlikePost()
        await mutation.mutate(1)

        expect(getQueryDataValue(['post', 1])).toMatchObject({ likeCount: 0, liked: false })
    })

    it('rolls back optimistic unlike update when API call fails', async () => {
        vi.mocked(postApi.unlikePost).mockRejectedValueOnce(new Error('unlike failed'))
        seedQueryData(['post', 1], { postId: 1, likeCount: 3, liked: true })
        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 1, likeCount: 3, liked: true }] }],
            pageParams: [],
        })

        const { useUnlikePost } = usePost()
        await expect(useUnlikePost().mutate(1)).rejects.toThrow('unlike failed')

        expect(getQueryDataValue(['post', 1])).toMatchObject({ postId: 1, likeCount: 3, liked: true })
        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({
            postId: 1,
            likeCount: 3,
            liked: true,
        })
    })

    it('applies optimistic scrap/unscrap updates and supports rollback', async () => {
        seedQueryData(['post', 1], { postId: 1, scrapped: false })
        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 1, scrapped: false }] }],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'free'], {
            content: [{ postId: 1, scrapped: false }],
        })

        vi.mocked(postApi.scrapPost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.scrapPost>())
        const { useScrapPost } = usePost()
        await useScrapPost().mutate(1)
        expect(getQueryDataValue(['post', 1])).toMatchObject({ scrapped: true })

        vi.mocked(postApi.unscrapPost).mockRejectedValueOnce(new Error('unscrap failed'))
        const { useUnscrapPost } = usePost()
        await expect(useUnscrapPost().mutate(1)).rejects.toThrow('unscrap failed')
        expect(getQueryDataValue(['post', 1])).toMatchObject({ scrapped: true })
    })

    it('rolls back optimistic scrap update when API call fails', async () => {
        seedQueryData(['post', 1], { postId: 1, scrapped: false })
        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 1, scrapped: false }] }],
            pageParams: [],
        })
        vi.mocked(postApi.scrapPost).mockRejectedValueOnce(new Error('scrap failed'))

        const { useScrapPost } = usePost()
        await expect(useScrapPost().mutate(1)).rejects.toThrow('scrap failed')

        expect(getQueryDataValue(['post', 1])).toMatchObject({ postId: 1, scrapped: false })
        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({
            postId: 1,
            scrapped: false,
        })
    })

    it('keeps rollback paths safe when onMutate fails before snapshot creation', async () => {
        const cancelError = new Error('cancel failed')
        mocks.cancelQueries
            .mockRejectedValueOnce(cancelError)
            .mockRejectedValueOnce(cancelError)
            .mockRejectedValueOnce(cancelError)
            .mockRejectedValueOnce(cancelError)

        const { useLikePost, useUnlikePost, useScrapPost, useUnscrapPost } = usePost()

        await expect(useLikePost().mutate(1)).rejects.toThrow('cancel failed')
        await expect(useUnlikePost().mutate(1)).rejects.toThrow('cancel failed')
        await expect(useScrapPost().mutate(1)).rejects.toThrow('cancel failed')
        await expect(useUnscrapPost().mutate(1)).rejects.toThrow('cancel failed')
    })

    it('rolls back post list caches even when post detail snapshot is undefined', async () => {
        vi.mocked(postApi.likePost).mockRejectedValueOnce(new Error('like failed without detail'))
        seedQueryData(['posts'], {
            pages: [{ content: [{ postId: 1, likeCount: 2, liked: false }] }],
            pageParams: [],
        })
        seedQueryData(['board', 'posts', 'free'], {
            content: [{ postId: 1, likeCount: 2, liked: false }],
        })

        const { useLikePost } = usePost()
        await expect(useLikePost().mutate(1)).rejects.toThrow('like failed without detail')

        expect(getInfinitePostItem(['posts'], 0)).toMatchObject({
            postId: 1,
            likeCount: 2,
            liked: false,
        })
        expect(getPostPageItem(['board', 'posts', 'free'], 0)).toMatchObject({
            postId: 1,
            likeCount: 2,
            liked: false,
        })
    })

    it('calls report post API', async () => {
        vi.mocked(postApi.reportPost).mockResolvedValueOnce(apiSuccessResponse<typeof postApi.reportPost>())
        const payload = { targetPostId: 1, reason: 'spam' }

        const { useReportPost } = usePost()
        await useReportPost().mutate(payload)

        expect(postApi.reportPost).toHaveBeenCalledWith(payload)
    })
})
