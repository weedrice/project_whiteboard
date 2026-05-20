import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { EmoticonMaster } from '@/types/emoticon'

const mocks = vi.hoisted(() => {
    const purchasedEmoticons: { __v_isRef: true; value: EmoticonMaster[] | undefined } = { __v_isRef: true, value: undefined }
    const isLoading: { __v_isRef: true; value: boolean } = { __v_isRef: true, value: false }
    const queryOptions: Array<Record<string, unknown>> = []
    const getPurchasedEmoticons = vi.fn()
    const getEmoticon = vi.fn()
    const loggerError = vi.fn()

    return {
        purchasedEmoticons,
        isLoading,
        queryOptions,
        getPurchasedEmoticons,
        getEmoticon,
        loggerError,
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: mocks.purchasedEmoticons,
            isLoading: mocks.isLoading,
        }
    }),
}))

vi.mock('@/api/emoticon', () => ({
    emoticonApi: {
        getPurchasedEmoticons: mocks.getPurchasedEmoticons,
        getEmoticon: mocks.getEmoticon,
    },
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: mocks.loggerError,
    },
}))

import EmoticonPicker from '../EmoticonPicker.vue'

const flushPromises = async () => {
    await Promise.resolve()
    await Promise.resolve()
}

const createEmoticon = (id: number, name: string, tags: string[] = []): EmoticonMaster => ({
    emoticonId: id,
    name,
    tags,
    isActive: true,
    images: [
        {
            imageId: id * 10 + 1,
            emoticonId: id,
            imageUrl: `https://cdn.test/${id}-1.png`,
            sortOrder: 1,
        },
    ],
    createdAt: '2026-01-01T00:00:00.000Z',
    modifiedAt: '2026-01-01T00:00:00.000Z',
})

const mountPicker = (show = true) => {
    return mount(EmoticonPicker, {
        props: { show },
        global: {
            stubs: {
                X: true,
                ArrowLeft: true,
                Search: true,
                Smile: true,
            },
        },
    })
}

describe('EmoticonPicker', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
        mocks.purchasedEmoticons.value = undefined
        mocks.isLoading.value = false
    })

    it('configures purchased emoticon query and respects show flag', async () => {
        const mockList = [createEmoticon(1, 'Cat')]
        mocks.getPurchasedEmoticons.mockResolvedValueOnce({
            data: { data: { content: mockList } },
        })

        mountPicker(false)
        const options = mocks.queryOptions[0]

        expect(options.queryKey).toEqual(['emoticons', 'purchased', 'picker'])
        expect((options.enabled as () => boolean)()).toBe(false)

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(mocks.getPurchasedEmoticons).toHaveBeenCalledWith({ size: 100 })
        expect(result).toEqual(mockList)
    })

    it('renders empty-state safely when purchased emoticons are undefined', () => {
        mocks.purchasedEmoticons.value = undefined
        const wrapper = mountPicker(true)

        expect(wrapper.find('.empty-state').exists()).toBe(true)
    })

    it('renders and filters purchased emoticons by name and tag', async () => {
        mocks.purchasedEmoticons.value = [
            createEmoticon(1, 'HappyCat', ['cute', 'animal']),
            createEmoticon(2, 'SadDog', ['pet']),
        ]

        const wrapper = mountPicker(true)

        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(2)

        await wrapper.get('.search-input').setValue('happy')
        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
        expect(wrapper.text()).toContain('HappyCat')

        await wrapper.get('.search-input').setValue('pet')
        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
        expect(wrapper.text()).toContain('SadDog')
    })

    it('keeps full list for whitespace keyword and prefers thumbnail image url', async () => {
        const withThumbnail = {
            ...createEmoticon(11, 'ThumbPack', ['thumb']),
            thumbnailUrl: 'https://cdn.test/11-thumb.png',
            images: [{ imageId: 111, emoticonId: 11, imageUrl: 'https://cdn.test/11-image.png', sortOrder: 1 }],
        }
        mocks.purchasedEmoticons.value = [withThumbnail, createEmoticon(12, 'OtherPack', ['other'])]

        const wrapper = mountPicker(true)
        await wrapper.get('.search-input').setValue('   ')

        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(2)
        expect(wrapper.find('.emoticon-btn img').attributes('src')).toBe('https://cdn.test/11-thumb.png')
    })

    it('shows empty state for no purchased emoticons and no search results', async () => {
        mocks.purchasedEmoticons.value = []
        const emptyWrapper = mountPicker(true)

        expect(emptyWrapper.find('.empty-state').exists()).toBe(true)

        mocks.purchasedEmoticons.value = [createEmoticon(3, 'OnlyOne', ['tag'])]
        const noMatchWrapper = mountPicker(true)
        await noMatchWrapper.get('.search-input').setValue('not-found')

        expect(noMatchWrapper.find('.empty-state').exists()).toBe(true)
    })

    it('loads detail on emoticon click, emits selected image and supports goBack', async () => {
        const listItem = createEmoticon(10, 'PackOne', ['fun'])
        mocks.purchasedEmoticons.value = [listItem]
        mocks.getEmoticon.mockResolvedValueOnce({
            data: {
                data: {
                    ...listItem,
                    images: [
                        {
                            imageId: 101,
                            emoticonId: 10,
                            imageUrl: 'https://cdn.test/10-1.png',
                            sortOrder: 1,
                        },
                        {
                            imageId: 102,
                            emoticonId: 10,
                            imageUrl: 'https://cdn.test/10-2.png',
                            sortOrder: 2,
                        },
                    ],
                },
            },
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(mocks.getEmoticon).toHaveBeenCalledWith(10)
        expect(wrapper.findAll('.image-btn')).toHaveLength(2)

        await wrapper.findAll('.image-btn')[0].trigger('click')
        expect(wrapper.emitted('select')?.[0]?.[0]).toEqual({
            imageId: 101,
            emoticonId: 10,
            imageUrl: 'https://cdn.test/10-1.png',
            sortOrder: 1,
        })

        await wrapper.get('.back-btn').trigger('click')
        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
    })

    it('ignores stale detail responses after selecting another emoticon', async () => {
        const first = createEmoticon(40, 'FirstPack', ['first'])
        const second = createEmoticon(41, 'SecondPack', ['second'])
        let resolveFirst: (value: unknown) => void = () => undefined
        let resolveSecond: (value: unknown) => void = () => undefined

        mocks.purchasedEmoticons.value = [first, second]
        mocks.getEmoticon
            .mockReturnValueOnce(new Promise((resolve) => {
                resolveFirst = resolve
            }))
            .mockReturnValueOnce(new Promise((resolve) => {
                resolveSecond = resolve
            }))

        const wrapper = mountPicker(true)
        const buttons = wrapper.findAll('.emoticon-btn')
        await buttons[0].trigger('click')
        await buttons[1].trigger('click')

        resolveSecond({ data: { data: second } })
        await flushPromises()
        expect(wrapper.text()).toContain('SecondPack')

        resolveFirst({ data: { data: first } })
        await flushPromises()
        expect(wrapper.text()).toContain('SecondPack')
        expect(wrapper.text()).not.toContain('FirstPack')
    })

    it('logs detail load error and exits loading state', async () => {
        mocks.purchasedEmoticons.value = [createEmoticon(20, 'BrokenPack', ['oops'])]
        mocks.getEmoticon.mockRejectedValueOnce(new Error('detail failed'))

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(mocks.loggerError).toHaveBeenCalledWith('Failed to load emoticon detail:', expect.any(Error))
        expect(wrapper.find('.loading-state').exists()).toBe(false)
    })

    it('handles detail payload without images by rendering empty image list', async () => {
        const listItem = createEmoticon(21, 'NoImagesPack', ['edge'])
        mocks.purchasedEmoticons.value = [listItem]
        mocks.getEmoticon.mockResolvedValueOnce({
            data: {
                data: {
                    emoticonId: 21,
                    name: 'NoImagesPack',
                    tags: ['edge'],
                    isActive: true,
                },
            },
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(wrapper.find('.images-grid').exists()).toBe(true)
        expect(wrapper.findAll('.image-btn')).toHaveLength(0)
    })

    it('closes by button/backdrop and resets state when show becomes false', async () => {
        mocks.purchasedEmoticons.value = [createEmoticon(30, 'ResetPack', ['reset'])]
        mocks.getEmoticon.mockResolvedValueOnce({
            data: {
                data: createEmoticon(30, 'ResetPack', ['reset']),
            },
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.search-input').setValue('reset')
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        await wrapper.get('.close-btn').trigger('click')
        await wrapper.get('.emoticon-picker-backdrop').trigger('click')
        expect(wrapper.emitted('close')).toHaveLength(2)

        await wrapper.setProps({ show: false })
        await wrapper.setProps({ show: true })
        await nextTick()

        expect((wrapper.get('.search-input').element as HTMLInputElement).value).toBe('')
        expect(wrapper.find('.back-btn').exists()).toBe(false)
        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
    })
})
