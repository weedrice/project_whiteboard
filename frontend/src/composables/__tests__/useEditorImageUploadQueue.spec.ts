import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createDeferred } from '@/test/async'
import { useEditorImageUploadQueue } from '../useEditorImageUploadQueue'

const createFile = (name: string) => new File(['image'], name, { type: 'image/png' })

describe('useEditorImageUploadQueue', () => {
    const validate = vi.fn()
    const upload = vi.fn()
    const isAbort = vi.fn()
    const onUploaded = vi.fn()
    const onValidationError = vi.fn()
    const abort = vi.fn()

    const createQueue = () => useEditorImageUploadQueue({
        validate,
        upload,
        isAbort,
        onUploaded,
        onValidationError,
        abort,
    })

    beforeEach(() => {
        vi.resetAllMocks()
        validate.mockReturnValue(null)
        isAbort.mockReturnValue(false)
    })

    it('uploads queued files sequentially', async () => {
        const firstUpload = createDeferred<{ url: string }>()
        const secondUpload = createDeferred<{ url: string }>()
        upload
            .mockReturnValueOnce(firstUpload.promise)
            .mockReturnValueOnce(secondUpload.promise)
        const firstFile = createFile('first.png')
        const secondFile = createFile('second.png')
        const queue = createQueue()

        const [firstItem, secondItem] = queue.enqueueFiles([firstFile, secondFile])

        expect(upload).toHaveBeenCalledTimes(1)
        expect(upload).toHaveBeenCalledWith(firstFile)
        expect(firstItem.status).toBe('uploading')
        expect(secondItem.status).toBe('queued')
        expect(queue.currentItem.value?.id).toBe(firstItem.id)
        expect(queue.queueCount.value).toBe(1)

        firstUpload.resolve({ url: 'https://cdn.test/first.png' })
        await flushPromises()

        expect(firstItem.status).toBe('uploaded')
        expect(upload).toHaveBeenCalledTimes(2)
        expect(upload).toHaveBeenLastCalledWith(secondFile)
        expect(secondItem.status).toBe('uploading')

        secondUpload.resolve({ url: 'https://cdn.test/second.png' })
        await flushPromises()

        expect(secondItem.status).toBe('uploaded')
        expect(queue.currentItem.value).toBeNull()
        expect(queue.isProcessing.value).toBe(false)
        expect(queue.queueCount.value).toBe(0)
        expect(onUploaded).toHaveBeenNthCalledWith(1, { url: 'https://cdn.test/first.png' }, firstFile)
        expect(onUploaded).toHaveBeenNthCalledWith(2, { url: 'https://cdn.test/second.png' }, secondFile)
    })

    it('preserves failed items when later files are enqueued', async () => {
        const failedFile = createFile('failed.png')
        const laterFile = createFile('later.png')
        const queue = createQueue()
        upload
            .mockRejectedValueOnce(new Error('network'))
            .mockResolvedValueOnce({ url: 'https://cdn.test/later.png' })

        const [failedItem] = queue.enqueueFiles([failedFile])
        await flushPromises()

        expect(failedItem.status).toBe('failed')
        expect(queue.failedItems.value).toEqual([failedItem])

        const [laterItem] = queue.enqueueFiles([laterFile])
        await flushPromises()

        expect(laterItem.status).toBe('uploaded')
        expect(queue.failedItems.value).toEqual([failedItem])
        expect(queue.failedCount.value).toBe(1)
    })

    it('retries and dismisses individual failed items', async () => {
        const failedFile = createFile('retry.png')
        const queue = createQueue()
        upload
            .mockRejectedValueOnce(new Error('fail once'))
            .mockResolvedValueOnce({ url: 'https://cdn.test/retry.png' })

        const [failedItem] = queue.enqueueFiles([failedFile])
        await flushPromises()

        expect(failedItem.status).toBe('failed')
        expect(queue.failedCount.value).toBe(1)

        expect(queue.retryItem(failedItem)).toBe(true)
        expect(failedItem.status).toBe('uploading')
        await flushPromises()

        expect(failedItem.status).toBe('uploaded')
        expect(queue.failedCount.value).toBe(0)

        upload.mockRejectedValueOnce(new Error('dismiss'))
        const [dismissedItem] = queue.enqueueFiles([createFile('dismiss.png')])
        await flushPromises()
        expect(dismissedItem.status).toBe('failed')

        expect(queue.dismissItem(dismissedItem)).toBe(true)
        expect(queue.items.value).not.toContain(dismissedItem)
        expect(queue.failedCount.value).toBe(0)
    })

    it('retries the next failed item and dismisses all failures', async () => {
        const queue = createQueue()
        upload
            .mockRejectedValueOnce(new Error('first'))
            .mockRejectedValueOnce(new Error('second'))
            .mockResolvedValueOnce({ url: 'https://cdn.test/first.png' })

        const [firstItem, secondItem] = queue.enqueueFiles([
            createFile('first.png'),
            createFile('second.png'),
        ])
        await flushPromises()

        expect(queue.failedItems.value).toEqual([firstItem, secondItem])
        expect(queue.retryNextFailed()).toBe(true)
        await flushPromises()

        expect(firstItem.status).toBe('uploaded')
        expect(secondItem.status).toBe('failed')

        queue.dismissFailed()

        expect(queue.failedItems.value).toEqual([])
        expect(queue.items.value).not.toContain(secondItem)
    })

    it('cancels the current upload and clears remaining queued files', async () => {
        const currentUpload = createDeferred<{ url: string }>()
        upload.mockReturnValueOnce(currentUpload.promise)
        const queue = createQueue()
        const [currentItem, queuedItem] = queue.enqueueFiles([
            createFile('current.png'),
            createFile('queued.png'),
        ])

        queue.cancel()

        expect(abort).toHaveBeenCalledTimes(1)
        expect(currentItem.status).toBe('canceled')
        expect(queuedItem.status).toBe('canceled')
        expect(queue.queueCount.value).toBe(0)

        currentUpload.resolve({ url: 'https://cdn.test/current.png' })
        await flushPromises()

        expect(currentItem.status).toBe('canceled')
        expect(upload).toHaveBeenCalledTimes(1)
        expect(onUploaded).not.toHaveBeenCalled()
        expect(queue.isProcessing.value).toBe(false)
    })

    it('disposes the queue and prevents later uploads from starting', async () => {
        const currentUpload = createDeferred<{ url: string }>()
        upload.mockReturnValueOnce(currentUpload.promise)
        const queue = createQueue()
        const [currentItem, queuedItem] = queue.enqueueFiles([
            createFile('current.png'),
            createFile('queued.png'),
        ])

        queue.dispose()
        const [lateItem] = queue.enqueueFiles([createFile('late.png')])

        expect(abort).toHaveBeenCalledTimes(1)
        expect(currentItem.status).toBe('canceled')
        expect(queuedItem.status).toBe('canceled')
        expect(lateItem.status).toBe('canceled')
        expect(queue.queueCount.value).toBe(0)

        currentUpload.resolve({ url: 'https://cdn.test/current.png' })
        await flushPromises()

        expect(upload).toHaveBeenCalledTimes(1)
        expect(onUploaded).not.toHaveBeenCalled()
        expect(queue.isProcessing.value).toBe(false)
    })

    it('stores validation failures without uploading them', async () => {
        const invalidFile = createFile('invalid.png')
        const validFile = createFile('valid.png')
        validate
            .mockReturnValueOnce('type')
            .mockReturnValueOnce(null)
        upload.mockResolvedValueOnce({ url: 'https://cdn.test/valid.png' })
        const queue = createQueue()

        const [invalidItem, validItem] = queue.enqueueFiles([invalidFile, validFile])
        await flushPromises()

        expect(invalidItem.status).toBe('failed')
        expect(invalidItem.error).toBe('type')
        expect(validItem.status).toBe('uploaded')
        expect(onValidationError).toHaveBeenCalledWith('type', invalidFile)
        expect(upload).toHaveBeenCalledTimes(1)
        expect(upload).toHaveBeenCalledWith(validFile)
        expect(queue.failedItems.value).toEqual([invalidItem])
    })
})
