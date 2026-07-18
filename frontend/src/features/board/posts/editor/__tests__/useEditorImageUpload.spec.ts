import { defineComponent, h, nextTick, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditorImageUpload } from '@/features/board/posts/editor/useEditorImageUpload'

const uploadFileMock = vi.hoisted(() => vi.fn())
const discardUploadsMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/file', () => ({
    fileApi: {
        uploadFile: uploadFileMock,
        discardUploads: discardUploadsMock,
    },
    resolveFileUploadUrl: (uploadedFile: { url?: string; fileUrl?: string }) => uploadedFile.url ?? uploadedFile.fileUrl ?? null,
}))

vi.mock('@/utils/logger', () => ({
    default: { warn: vi.fn() },
}))

const createHarness = (maxImageSizeBytes = 1024, ownerIdentity?: Ref<string | undefined>) => {
    let composable: ReturnType<typeof useEditorImageUpload> | null = null
    const Harness = defineComponent({
        setup() {
            composable = useEditorImageUpload(maxImageSizeBytes, ownerIdentity)
            return () => h('div')
        },
    })

    const wrapper = mount(Harness)
    if (!composable) {
        throw new Error('Composable was not initialized')
    }
    return {
        wrapper,
        composable: composable as ReturnType<typeof useEditorImageUpload>,
    }
}

describe('useEditorImageUpload', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        discardUploadsMock.mockResolvedValue({ data: { data: { discardedCount: 1 } } })
    })

    it('validates image mime type and size', () => {
        const { composable } = createHarness(100)
        const textFile = new File(['hello'], 'a.txt', { type: 'text/plain' })
        const svgFile = new File(['<svg />'], 'vector.svg', { type: 'image/svg+xml' })
        const mismatchedExtension = new File([new ArrayBuffer(50)], 'small.svg', { type: 'image/png' })
        const mismatchedMime = new File([new ArrayBuffer(50)], 'small.png', { type: 'image/svg+xml' })
        const largeImage = new File([new ArrayBuffer(200)], 'large.png', { type: 'image/png' })
        const validImage = new File([new ArrayBuffer(50)], 'small.png', { type: 'image/png' })
        const validJpgAlias = new File([new ArrayBuffer(50)], 'small.jpg', { type: 'image/jpg' })

        expect(composable.validateImageFile(textFile)).toBe('type')
        expect(composable.validateImageFile(svgFile)).toBe('type')
        expect(composable.validateImageFile(mismatchedExtension)).toBe('type')
        expect(composable.validateImageFile(mismatchedMime)).toBe('type')
        expect(composable.validateImageFile(largeImage)).toBe('size')
        expect(composable.validateImageFile(validImage)).toBeNull()
        expect(composable.validateImageFile(validJpgAlias)).toBeNull()
    })

    it('uploads image and returns url/fileId on success', async () => {
        const { composable } = createHarness()
        const image = new File([new ArrayBuffer(10)], 'img.png', { type: 'image/png' })
        uploadFileMock.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    url: 'https://cdn.example.com/img.png',
                    fileId: 77,
                },
            },
        })

        const result = await composable.uploadImage(image)

        expect(uploadFileMock).toHaveBeenCalledWith(
            image,
            expect.objectContaining({
                signal: expect.any(AbortSignal),
            }),
        )
        expect(result).toEqual({
            url: 'https://cdn.example.com/img.png',
            fileId: 77,
        })
        expect(composable.isUploadingImage.value).toBe(false)
    })

    it('accepts backend fileUrl upload responses', async () => {
        const { composable } = createHarness()
        const image = new File([new ArrayBuffer(10)], 'img.png', { type: 'image/png' })
        uploadFileMock.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    fileUrl: '/api/v1/files/77',
                    fileId: 77,
                },
            },
        })

        await expect(composable.uploadImage(image)).resolves.toEqual({
            url: '/api/v1/files/77',
            fileId: 77,
        })
    })

    it('returns null when upload result is not successful', async () => {
        const { composable } = createHarness()
        const image = new File([new ArrayBuffer(10)], 'img.png', { type: 'image/png' })
        uploadFileMock.mockResolvedValueOnce({
            data: {
                success: false,
                data: {},
            },
        })

        const result = await composable.uploadImage(image)
        expect(result).toBeNull()
    })

    it('prevents duplicate uploads while one upload is in progress', async () => {
        const { composable } = createHarness()
        const image = new File([new ArrayBuffer(10)], 'img.png', { type: 'image/png' })

        let resolveUpload!: (value: unknown) => void
        uploadFileMock.mockImplementationOnce(() => new Promise((resolve) => {
            resolveUpload = resolve
        }))

        const firstUpload = composable.uploadImage(image)
        const secondUpload = await composable.uploadImage(image)

        expect(secondUpload).toBeNull()
        expect(uploadFileMock).toHaveBeenCalledTimes(1)

        resolveUpload({
            data: {
                success: true,
                data: {
                    url: 'https://cdn.example.com/1.png',
                },
            },
        })
        await firstUpload
    })

    it('aborts in-flight upload on demand and on unmount', async () => {
        const { composable, wrapper } = createHarness()
        const image = new File([new ArrayBuffer(10)], 'img.png', { type: 'image/png' })

        uploadFileMock.mockImplementationOnce((_file: File, config: { signal: AbortSignal }) => new Promise((_, reject) => {
            config.signal.addEventListener('abort', () => reject({ name: 'AbortError' }))
        }))

        const uploadPromise = composable.uploadImage(image)
        const uploadSignal = uploadFileMock.mock.calls[0][1].signal as AbortSignal
        expect(uploadSignal.aborted).toBe(false)

        composable.abortImageUpload()
        await expect(uploadPromise).rejects.toEqual({ name: 'AbortError' })
        expect(uploadSignal.aborted).toBe(true)
        expect(composable.isUploadingImage.value).toBe(false)

        uploadFileMock.mockImplementationOnce((_file: File, config: { signal: AbortSignal }) => new Promise((_, reject) => {
            config.signal.addEventListener('abort', () => reject({ code: 'ERR_CANCELED' }))
        }))

        const secondUpload = composable.uploadImage(image)
        const secondSignal = uploadFileMock.mock.calls[1][1].signal as AbortSignal
        wrapper.unmount()
        await nextTick()
        await expect(secondUpload).rejects.toEqual({ code: 'ERR_CANCELED' })
        expect(secondSignal.aborted).toBe(true)
    })

    it('discards a late successful upload after composer identity changes', async () => {
        const ownerIdentity = ref<string | undefined>('session-1:create:free:new')
        const { composable } = createHarness(1024, ownerIdentity)
        const image = new File([new ArrayBuffer(10)], 'late.png', { type: 'image/png' })
        let resolveUpload!: (value: unknown) => void
        uploadFileMock.mockImplementationOnce(() => new Promise((resolve) => {
            resolveUpload = resolve
        }))

        const uploadPromise = composable.uploadImage(image)
        const signal = uploadFileMock.mock.calls[0][1].signal as AbortSignal
        ownerIdentity.value = 'session-2:create:free:new'

        expect(signal.aborted).toBe(true)
        resolveUpload({
            data: {
                success: true,
                data: { url: '/api/v1/files/88', fileId: 88 },
            },
        })

        await expect(uploadPromise).resolves.toBeNull()
        expect(discardUploadsMock).toHaveBeenCalledWith([88], { skipGlobalErrorHandler: true })
    })

    it('recognizes abort/cancel errors', () => {
        const { composable } = createHarness()

        expect(composable.isAbortUploadError({ name: 'AbortError' })).toBe(true)
        expect(composable.isAbortUploadError({ name: 'CanceledError' })).toBe(true)
        expect(composable.isAbortUploadError({ code: 'ERR_CANCELED' })).toBe(true)
        expect(composable.isAbortUploadError({ name: 'TypeError' })).toBe(false)
    })
})
