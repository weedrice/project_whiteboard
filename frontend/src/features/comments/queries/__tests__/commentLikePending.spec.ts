import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { onlineManager, QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary, sessionQueryKey } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { useComment } from '../useComment'

const auth = reactive({
    sessionGeneration: 1,
    user: { userId: 1 },
    accessToken: 'token-a',
    fetchUser: vi.fn(async () => true),
    setTokens: vi.fn(),
    applyTokenIfCurrent: vi.fn(() => true),
    clearSessionState: vi.fn(),
})
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/router', () => ({ default: { push: vi.fn(), currentRoute: { value: { meta: {} } } } }))

type Write = {
    config: InternalAxiosRequestConfig
    finish: (result: 'success' | 'failure') => void
}
const originalAdapter = api.defaults.adapter
const wrappers = new Set<VueWrapper>()
const clients = new Set<QueryClient>()
let client: QueryClient
let writes: Write[]
let reads: number
let server: Map<string, boolean>

function newClient() {
    const result = new QueryClient({ defaultOptions: {
        queries: { retry: false, gcTime: 0 }, mutations: { retry: false, gcTime: 0 },
    } })
    clients.add(result)
    return result
}

function observer(commentId: string | number = 7, queryClient = client) {
    const id = ref(commentId)
    let mutation!: ReturnType<ReturnType<typeof useComment>['useToggleCommentLike']>
    let pending!: ReturnType<ReturnType<typeof useComment>['useCommentLikePending']>
    const wrapper = mount(defineComponent({
        setup() {
            const feature = useComment()
            mutation = feature.useToggleCommentLike()
            pending = feature.useCommentLikePending(id)
            return () => null
        },
    }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
    wrappers.add(wrapper)
    return { mutation, pending, id, wrapper }
}

function queries() {
    let list!: ReturnType<ReturnType<typeof useComment>['useComments']>
    let best!: ReturnType<ReturnType<typeof useComment>['useBestComments']>
    let replies!: ReturnType<ReturnType<typeof useComment>['useReplies']>
    wrappers.add(mount(defineComponent({
        setup() {
            const feature = useComment()
            list = feature.useComments(ref(3), ref({ page: 0, size: 50 }))
            best = feature.useBestComments(ref(3))
            replies = feature.useReplies(ref(1), ref({ page: 0, size: 50 }))
            return () => null
        },
    }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))
    return { list, best, replies }
}

const like = (commentId = 7) => ({ commentId, postId: 3, liked: true })

beforeEach(() => {
    vi.clearAllMocks()
    auth.sessionGeneration = 1
    auth.user = { userId: 1 }
    auth.accessToken = 'token-a'
    persistAccessToken(auth.accessToken)
    configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user.userId)
    configureApiStoreResolvers({ resolveAuthStore: () => auth })
    onlineManager.setOnline(true)
    client = newClient()
    writes = []
    reads = 0
    server = new Map()
    api.defaults.adapter = async config => {
        const response = { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data: null as unknown } }
        const userId = config.expectedSession?.userId ?? 1
        if (config.method === 'get') {
            reads += 1
            const comments = [7, 8].map(commentId => ({
                commentId, content: `comment ${commentId}`, liked: server.get(`${userId}:${commentId}`) ?? false,
                likeCount: 10 + Number(server.get(`${userId}:${commentId}`) ?? false),
            }))
            response.data.data = config.url?.endsWith('/best') ? comments.slice(0, 1) : {
                content: comments, number: 0, size: 50, totalElements: 2, totalPages: 1, last: true,
            }
            return response
        }
        const result = await new Promise<'success' | 'failure'>(finish => writes.push({ config, finish }))
        if (result === 'failure') {
            throw new AxiosError('like failed', 'ERR_BAD_RESPONSE', config, undefined, { ...response, status: 500 })
        }
        const commentId = config.url?.match(/\/comments\/(\d+)\/like$/)?.[1]
        const key = `${userId}:${commentId}`
        const liked = config.method === 'post'
        if (!liked && !server.get(key)) {
            throw new AxiosError('NOT_LIKED', 'ERR_BAD_REQUEST', config, undefined, { ...response, status: 400 })
        }
        server.set(key, liked)
        return response
    }
})

afterEach(() => {
    wrappers.forEach(wrapper => wrapper.unmount())
    wrappers.clear()
    clients.forEach(queryClient => queryClient.clear())
    clients.clear()
    api.defaults.adapter = originalAdapter
    configureAuthQueryScope(() => 0)
    configureApiStoreResolvers({ resolveAuthStore: () => null })
    clearStoredAuthTokens(false)
    onlineManager.setOnline(true)
})

describe('shared comment likes through real Vue Query and Axios', () => {
    it('locks best, regular and reply instances before awaiting and allows unlike after settlement', async () => {
        const { list, best, replies } = queries()
        const first = observer()
        const second = observer('7')
        const reply = observer()
        await vi.waitFor(() => expect(best.data.value?.[0]?.liked).toBe(false))
        const operation = first.mutation.mutateAsync(like())
        expect([first.pending.value, second.pending.value, reply.pending.value]).toEqual([true, true, true])
        // A second callback can arrive before Vue renders disabled buttons.
        second.mutation.mutate({ ...like(), liked: false })
        expect(await reply.mutation.mutateAsync(like())).toBeUndefined()
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        expect(writes[0]!.config.method).toBe('post')
        expect(list.data.value?.content[0]).toMatchObject({ liked: true, likeCount: 11 })
        expect(best.data.value?.[0]).toMatchObject({ liked: true, likeCount: 11 })
        expect(replies.data.value?.content[0]).toMatchObject({ liked: true, likeCount: 11 })
        expect(await second.mutation.mutateAsync({ ...like(), liked: false })).toBeUndefined()
        writes[0]!.finish('success')
        await operation
        await vi.waitFor(() => expect(client.isFetching()).toBe(0))
        expect(reads).toBe(6)
        expect([first.pending.value, second.pending.value, reply.pending.value]).toEqual([false, false, false])
        const unlike = second.mutation.mutateAsync({ ...like(), liked: false })
        await vi.waitFor(() => expect(writes).toHaveLength(2))
        expect(writes[1]!.config.method).toBe('delete')
        writes[1]!.finish('success')
        await unlike
        await vi.waitFor(() => expect(client.isFetching()).toBe(0))
        expect(server.get('1:7')).toBe(false)
        expect(list.data.value?.content[0]).toMatchObject({ liked: false, likeCount: 10 })
        expect(best.data.value?.[0]).toMatchObject({ liked: false, likeCount: 10 })
        expect(replies.data.value?.content[0]).toMatchObject({ liked: false, likeCount: 10 })
    })

    it('keeps different comments and query clients independent', async () => {
        const first = observer()
        const otherComment = observer(8)
        const otherClient = observer(7, newClient())
        const firstOperation = first.mutation.mutateAsync(like())
        expect(otherComment.pending.value).toBe(false)
        expect(otherClient.pending.value).toBe(false)
        const otherOperation = otherComment.mutation.mutateAsync(like(8))
        await vi.waitFor(() => expect(writes).toHaveLength(2))
        writes.find(write => write.config.url === '/comments/8/like')!.finish('success')
        await otherOperation
        expect(first.pending.value).toBe(true)
        expect(otherComment.pending.value).toBe(false)
        writes.find(write => write.config.url === '/comments/7/like')!.finish('success')
        await firstOperation
    })

    it('releases every instance after failure and permits retry', async () => {
        const { best } = queries()
        const first = observer()
        const second = observer()
        await vi.waitFor(() => expect(best.data.value?.[0]?.liked).toBe(false))
        const operation = first.mutation.mutateAsync(like()).catch(error => error)
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        writes[0]!.finish('failure')
        expect(await operation).toBeInstanceOf(AxiosError)
        await vi.waitFor(() => expect(client.isFetching()).toBe(0))
        expect(first.pending.value).toBe(false)
        expect(second.pending.value).toBe(false)
        expect(best.data.value?.[0]).toMatchObject({ liked: false, likeCount: 10 })
        const retry = second.mutation.mutateAsync(like())
        await vi.waitFor(() => expect(writes).toHaveLength(2))
        writes[1]!.finish('success')
        await retry
        expect(second.pending.value).toBe(false)
    })

    it.each([
        { userId: 2, generation: 2, label: 'account switch' },
        { userId: 1, generation: 2, label: 'same-account login' },
        { userId: 2, generation: 1, label: 'principal change' },
    ])('isolates pending and stale completion on $label, even in the same tick', async ({ userId, generation }) => {
        const first = observer()
        const second = observer()
        const oldOperation = first.mutation.mutateAsync(like()).catch(error => error)
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        auth.user = { userId }
        auth.sessionGeneration = generation
        if (generation !== 1) notifyAuthSessionBoundary(generation)
        persistAccessToken('new-session-token')
        expect(second.pending.value).toBe(false)
        const newOperation = second.mutation.mutateAsync(like())
        expect(first.pending.value).toBe(true)
        await vi.waitFor(() => expect(writes).toHaveLength(2))
        const key = sessionQueryKey(generation, ['comments', 'sentinel'])
        client.setQueryData(key, { content: [{ commentId: 7, liked: false, likeCount: 99 }] })
        writes[0]!.finish('failure')
        expect(await oldOperation).toMatchObject({ name: 'AbortError', suppressGlobalErrorToast: true })
        expect(second.pending.value).toBe(true)
        expect(client.getQueryData(key)).toEqual({ content: [{ commentId: 7, liked: false, likeCount: 99 }] })
        expect(writes[1]!.config.expectedSession).toEqual({ sessionGeneration: generation, userId })
        writes[1]!.finish('success')
        await newOperation
        expect(second.pending.value).toBe(false)
    })

    it('retains the lock during normal token rotation', async () => {
        const first = observer()
        const second = observer()
        const operation = first.mutation.mutateAsync(like())
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        auth.accessToken = 'rotated-token'
        persistAccessToken(auth.accessToken)
        expect(second.pending.value).toBe(true)
        expect(await second.mutation.mutateAsync(like())).toBeUndefined()
        writes[0]!.finish('success')
        await operation
        expect(second.pending.value).toBe(false)
        expect(writes).toHaveLength(1)
    })

    it('preserves pending and shared cache settlement after the initiating instance unmounts', async () => {
        const { best } = queries()
        const first = observer()
        const operation = first.mutation.mutateAsync(like())
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        first.wrapper.unmount()
        wrappers.delete(first.wrapper)
        const replacement = observer()
        expect(replacement.pending.value).toBe(true)
        expect(await replacement.mutation.mutateAsync(like())).toBeUndefined()
        writes[0]!.finish('success')
        await operation
        await vi.waitFor(() => expect(client.isFetching()).toBe(0))
        expect(best.data.value?.[0]).toMatchObject({ liked: true, likeCount: 11 })
        expect(replacement.pending.value).toBe(false)
        expect(writes).toHaveLength(1)
    })

    it('releases an offline old session without allowing it to replay in the next session', async () => {
        const first = observer()
        const second = observer()
        onlineManager.setOnline(false)
        const oldOperation = first.mutation.mutateAsync(like()).catch(error => error)
        await vi.waitFor(() => expect(first.mutation.isPaused.value).toBe(true))
        auth.sessionGeneration = 2
        auth.user = { userId: 2 }
        notifyAuthSessionBoundary(2)
        expect(second.pending.value).toBe(false)
        onlineManager.setOnline(true)
        const operation = second.mutation.mutateAsync(like())
        expect(await oldOperation).toMatchObject({ name: 'AbortError' })
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        expect(writes[0]!.config.expectedSession).toEqual({ sessionGeneration: 2, userId: 2 })
        expect(second.pending.value).toBe(true)
        writes[0]!.finish('success')
        await operation
        expect(second.pending.value).toBe(false)
    })

    it('follows a reused instance to another comment without releasing the original lock', async () => {
        const reused = observer()
        const original = observer()
        const operation = reused.mutation.mutateAsync(like())
        reused.id.value = 8
        expect(reused.pending.value).toBe(false)
        expect(original.pending.value).toBe(true)
        await vi.waitFor(() => expect(writes).toHaveLength(1))
        writes[0]!.finish('success')
        await operation
        expect(original.pending.value).toBe(false)
    })
})
