import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { EmoticonMaster } from '@/types/emoticon'

const mocks = vi.hoisted(() => {
    const purchasedEmoticons: { __v_isRef: true; value: EmoticonMaster[] | undefined } = { __v_isRef: true, value: undefined }
    const isLoading: { __v_isRef: true; value: boolean } = { __v_isRef: true, value: false }
    const isError: { __v_isRef: true; value: boolean } = { __v_isRef: true, value: false }
    const error: { __v_isRef: true; value: Error | null } = { __v_isRef: true, value: null }
    const queryOptions: Array<Record<string, unknown>> = []
    const getPurchasedEmoticons = vi.fn()
    const getMyEmoticons = vi.fn()
    const getEmoticon = vi.fn()
    const refetchPurchasedEmoticons = vi.fn()
    const loggerError = vi.fn()

    return {
        purchasedEmoticons,
        isLoading,
        isError,
        error,
        queryOptions,
        getPurchasedEmoticons,
        getMyEmoticons,
        getEmoticon,
        refetchPurchasedEmoticons,
        loggerError,
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: mocks.purchasedEmoticons,
            isLoading: mocks.isLoading,
            isError: mocks.isError,
            error: mocks.error,
            refetch: mocks.refetchPurchasedEmoticons,
        }
    }),
}))

vi.mock('@/api/emoticon', () => ({
    emoticonApi: {
        getPurchasedEmoticonsData: mocks.getPurchasedEmoticons,
        getMyEmoticonsData: mocks.getMyEmoticons,
        getEmoticonData: mocks.getEmoticon,
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

const mountPicker = (show = true, attachTo?: HTMLElement) => {
    return mount(EmoticonPicker, {
        props: { show },
        attachTo,
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
    afterEach(() => {
        document.body.innerHTML = ''
    })

    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
        mocks.purchasedEmoticons.value = undefined
        mocks.isLoading.value = false
        mocks.isError.value = false
        mocks.error.value = null
    })

    it('configures accessible emoticon query and respects show flag', async () => {
        const purchasedList = [createEmoticon(1, 'Cat')]
        const myList = [createEmoticon(2, 'OwnerPack')]
        mocks.getPurchasedEmoticons.mockResolvedValueOnce({
            content: purchasedList,
        })
        mocks.getMyEmoticons.mockResolvedValueOnce({
            content: myList,
        })

        mountPicker(false)
        const options = mocks.queryOptions[0]

        expect(options.queryKey).toEqual(['emoticons', 'accessible', 'picker'])
        expect((options.enabled as () => boolean)()).toBe(false)

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(mocks.getPurchasedEmoticons).toHaveBeenCalledWith({ size: 100 })
        expect(mocks.getMyEmoticons).toHaveBeenCalledWith({ size: 100 })
        expect(result).toEqual([...purchasedList, ...myList])
    })

    it('deduplicates emoticons that appear in purchased and owned lists', async () => {
        const shared = createEmoticon(1, 'SharedPack')
        mocks.getPurchasedEmoticons.mockResolvedValueOnce({
            content: [shared],
        })
        mocks.getMyEmoticons.mockResolvedValueOnce({
            content: [shared, createEmoticon(2, 'OwnerPack')],
        })

        mountPicker(false)
        const options = mocks.queryOptions[0]

        const result = await (options.queryFn as () => Promise<EmoticonMaster[]>)()

        expect(result.map((emoticon) => emoticon.emoticonId)).toEqual([1, 2])
    })

    it('renders empty-state safely when purchased emoticons are undefined', () => {
        mocks.purchasedEmoticons.value = undefined
        const wrapper = mountPicker(true)

        expect(wrapper.get('.emoticon-picker').attributes()).toMatchObject({
            role: 'dialog',
            'aria-modal': 'true',
            'aria-labelledby': 'emoticon-picker-title',
        })
        const searchInput = wrapper.get('.search-input')
        expect(wrapper.get(`label[for="${searchInput.attributes('id')}"]`).text()).toBe('노비콘 검색')
        expect(wrapper.find('.empty-state').exists()).toBe(true)
    })

    it('renders and filters purchased emoticons by name and tag', async () => {
        mocks.purchasedEmoticons.value = [
            createEmoticon(1, 'HappyCat', ['cute', 'animal']),
            createEmoticon(2, 'SadDog', ['pet']),
        ]

        const wrapper = mountPicker(true)

        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(2)
        expect(wrapper.get('.emoticons-grid').attributes('role')).toBe('list')
        expect(wrapper.findAll('[role="listitem"]')).toHaveLength(2)
        expect(wrapper.get('.emoticon-btn').attributes('aria-label')).toBe('HappyCat 이미지 선택')

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

    it('shows list load error and retries purchased emoticons query', async () => {
        mocks.isError.value = true
        mocks.error.value = new Error('list failed')
        const wrapper = mountPicker(true)

        expect(wrapper.get('.error-state').text()).toContain('노비콘 목록을 불러오지 못했습니다.')
        await wrapper.get('.retry-btn').trigger('click')

        expect(mocks.refetchPurchasedEmoticons).toHaveBeenCalledTimes(1)
        expect(wrapper.find('.empty-state').exists()).toBe(false)
    })

    it('loads detail on emoticon click, emits selected image and supports goBack', async () => {
        const listItem = createEmoticon(10, 'PackOne', ['fun'])
        mocks.purchasedEmoticons.value = [listItem]
        mocks.getEmoticon.mockResolvedValueOnce({
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
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(mocks.getEmoticon).toHaveBeenCalledWith(10, {
            signal: expect.any(AbortSignal),
        })
        expect(wrapper.get('.images-grid').attributes('role')).toBe('list')
        expect(wrapper.findAll('[role="listitem"]')).toHaveLength(2)
        expect(wrapper.findAll('.image-btn')).toHaveLength(2)
        expect(wrapper.get('.back-btn').attributes('type')).toBe('button')
        expect(wrapper.get('.back-btn').attributes('aria-label')).toBe('이모티콘 목록으로 돌아가기')
        expect(wrapper.get('.image-btn').attributes('type')).toBe('button')
        expect(wrapper.get('.image-btn').attributes('aria-label')).toBe('PackOne 이미지 선택')

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
        await wrapper.findAll('.emoticon-btn')[0].trigger('click')
        await wrapper.get('.back-btn').trigger('click')
        await wrapper.findAll('.emoticon-btn')[1].trigger('click')

        resolveSecond(second)
        await flushPromises()
        expect(wrapper.text()).toContain('SecondPack')

        resolveFirst(first)
        await flushPromises()
        expect(wrapper.text()).toContain('SecondPack')
        expect(wrapper.text()).not.toContain('FirstPack')
    })

    it('aborts pending detail request on unmount', async () => {
        const listItem = createEmoticon(50, 'UnmountPack', ['cleanup'])
        let detailSignal: AbortSignal | undefined
        mocks.purchasedEmoticons.value = [listItem]
        mocks.getEmoticon.mockImplementationOnce((_id: number, config?: { signal?: AbortSignal }) => {
            detailSignal = config?.signal
            return new Promise(() => undefined)
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')

        expect(detailSignal?.aborted).toBe(false)

        wrapper.unmount()

        expect(detailSignal?.aborted).toBe(true)
    })

    it('logs detail load error and exits loading state', async () => {
        mocks.purchasedEmoticons.value = [createEmoticon(20, 'BrokenPack', ['oops'])]
        mocks.getEmoticon.mockRejectedValueOnce(new Error('detail failed'))

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(mocks.loggerError).toHaveBeenCalledWith('Failed to load emoticon detail:', expect.any(Error))
        expect(wrapper.find('.loading-state').exists()).toBe(false)
        expect(wrapper.get('.error-state').text()).toContain('노비콘 정보를 불러오지 못했습니다.')
        expect(wrapper.findAll('.retry-btn')).toHaveLength(2)
    })

    it('handles detail payload without images by rendering empty state', async () => {
        const listItem = createEmoticon(21, 'NoImagesPack', ['edge'])
        mocks.purchasedEmoticons.value = [listItem]
        mocks.getEmoticon.mockResolvedValueOnce({
            emoticonId: 21,
            name: 'NoImagesPack',
            tags: ['edge'],
            isActive: true,
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        expect(wrapper.find('.empty-state').exists()).toBe(true)
        expect(wrapper.findAll('.image-btn')).toHaveLength(0)
    })

    it('closes by button/backdrop and resets state when show becomes false', async () => {
        mocks.purchasedEmoticons.value = [createEmoticon(30, 'ResetPack', ['reset'])]
        mocks.getEmoticon.mockResolvedValueOnce({
            ...createEmoticon(30, 'ResetPack', ['reset']),
        })

        const wrapper = mountPicker(true)
        await wrapper.get('.search-input').setValue('reset')
        await wrapper.get('.emoticon-btn').trigger('click')
        await flushPromises()

        await wrapper.get('.close-btn').trigger('click')
        await wrapper.get('.emoticon-picker-backdrop').trigger('click')
        expect(wrapper.emitted('close')).toHaveLength(2)
        expect(wrapper.get('.close-btn').attributes('type')).toBe('button')
        expect(wrapper.get('.close-btn').attributes('aria-label')).toBe('이모티콘 선택기 닫기')

        await wrapper.setProps({ show: false })
        await wrapper.setProps({ show: true })
        await nextTick()

        expect((wrapper.get('.search-input').element as HTMLInputElement).value).toBe('')
        expect(wrapper.find('.back-btn').exists()).toBe(false)
        expect(wrapper.findAll('.emoticon-btn')).toHaveLength(1)
    })

    it('closes on Escape key', async () => {
        const wrapper = mountPicker(true)

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
        await nextTick()

        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('moves focus into the dialog and restores previous focus when hidden', async () => {
        const trigger = document.createElement('button')
        document.body.appendChild(trigger)
        trigger.focus()

        const host = document.createElement('div')
        document.body.appendChild(host)
        const wrapper = mountPicker(true, host)
        await nextTick()
        await nextTick()

        expect(document.activeElement).toBe(wrapper.get('.close-btn').element)

        await wrapper.setProps({ show: false })
        await nextTick()

        expect(document.activeElement).toBe(trigger)
    })

    it('loops Tab and Shift+Tab within the opened picker', async () => {
        mocks.purchasedEmoticons.value = [createEmoticon(1, 'Cat')]
        const host = document.createElement('div')
        document.body.appendChild(host)
        const wrapper = mountPicker(true, host)
        await nextTick()
        await nextTick()

        const focusable = Array.from(wrapper.get('[role="dialog"]').element.querySelectorAll<HTMLElement>(
            'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ))
        const first = focusable[0]
        const last = focusable.at(-1)!

        last.focus()
        const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
        document.dispatchEvent(tab)
        expect(tab.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(first)

        first.focus()
        const shiftTab = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
        document.dispatchEvent(shiftTab)
        expect(shiftTab.defaultPrevented).toBe(true)
        expect(document.activeElement).toBe(last)
    })
})
