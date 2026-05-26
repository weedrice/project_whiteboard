import { computed, ref } from 'vue'
import type { Ref } from 'vue'

export type EditorImageUploadStatus = 'queued' | 'uploading' | 'failed' | 'uploaded' | 'canceled'

export interface EditorImageUploadItem<TUploaded = unknown> {
    id: number
    file: File
    status: EditorImageUploadStatus
    uploaded?: TUploaded
    error?: unknown
}

interface UseEditorImageUploadQueueOptions<TUploaded> {
    validate: (file: File) => unknown
    upload: (file: File) => Promise<TUploaded | null>
    isAbort: (error: unknown) => boolean
    onUploaded: (uploaded: TUploaded, file: File) => void
    onFailed?: (error: unknown, file: File) => void
    abort?: () => void
}

type ItemTarget<TUploaded> = number | EditorImageUploadItem<TUploaded>

export function useEditorImageUploadQueue<TUploaded>(
    options: UseEditorImageUploadQueueOptions<TUploaded>,
) {
    const items: Ref<EditorImageUploadItem<TUploaded>[]> = ref([])
    const queuedItemIds: Ref<number[]> = ref([])
    const currentItem: Ref<EditorImageUploadItem<TUploaded> | null> = ref(null)
    const isProcessing = ref(false)
    let nextItemId = 1
    let isDisposed = false

    const queuedItems = computed(() => queuedItemIds.value
        .map((id) => items.value.find((item) => item.id === id))
        .filter((item): item is EditorImageUploadItem<TUploaded> => Boolean(item)))
    const failedItems = computed(() => items.value.filter((item) => item.status === 'failed'))
    const queueCount = computed(() => queuedItems.value.length)
    const failedCount = computed(() => failedItems.value.length)

    const findItem = (target: ItemTarget<TUploaded>) => {
        const id = typeof target === 'number' ? target : target.id
        return items.value.find((item) => item.id === id)
    }

    const removeFromQueue = (itemId: number) => {
        queuedItemIds.value = queuedItemIds.value.filter((queuedId) => queuedId !== itemId)
    }

    const enqueueItem = (item: EditorImageUploadItem<TUploaded>, priority = false) => {
        removeFromQueue(item.id)
        if (priority) {
            queuedItemIds.value.unshift(item.id)
            return
        }
        queuedItemIds.value.push(item.id)
    }

    const processQueue = async () => {
        if (isProcessing.value) return

        isProcessing.value = true
        try {
            while (!isDisposed && queuedItemIds.value.length > 0) {
                const itemId = queuedItemIds.value.shift()
                const item = typeof itemId === 'number' ? findItem(itemId) : undefined
                if (!item || item.status !== 'queued') continue

                if (isDisposed) {
                    item.status = 'canceled'
                    continue
                }

                item.status = 'uploading'
                item.error = undefined
                currentItem.value = item

                try {
                    const uploaded = await options.upload(item.file)
                    if (isDisposed || (item.status as EditorImageUploadStatus) === 'canceled') continue
                    if (uploaded) {
                        item.status = 'uploaded'
                        item.uploaded = uploaded
                        options.onUploaded(uploaded, item.file)
                    } else {
                        item.status = 'failed'
                        options.onFailed?.(null, item.file)
                    }
                } catch (error: unknown) {
                    if ((item.status as EditorImageUploadStatus) === 'canceled' || options.isAbort(error)) {
                        item.status = 'canceled'
                        continue
                    }
                    item.status = 'failed'
                    item.error = error
                    options.onFailed?.(error, item.file)
                } finally {
                    if (currentItem.value?.id === item.id) {
                        currentItem.value = null
                    }
                }
            }
        } finally {
            isProcessing.value = false
        }
    }

    const enqueueFiles = (files: File[]) => {
        const createdItems = files.map((file) => {
            const validationError = isDisposed ? 'disposed' : options.validate(file)
            const item: EditorImageUploadItem<TUploaded> = {
                id: nextItemId,
                file,
                status: isDisposed ? 'canceled' : validationError ? 'failed' : 'queued',
                error: validationError || undefined,
            }
            nextItemId += 1
            items.value.push(item)
            if (item.status === 'queued') enqueueItem(item)
            return item
        })

        void processQueue()
        return createdItems
    }

    const retryItem = (target: ItemTarget<TUploaded>) => {
        if (isDisposed) return false
        const item = findItem(target)
        if (!item || item.status === 'uploading') return false

        const validationError = options.validate(item.file)
        item.uploaded = undefined
        item.error = validationError || undefined
        item.status = validationError ? 'failed' : 'queued'
        if (item.status === 'queued') {
            enqueueItem(item, true)
            void processQueue()
        }
        return true
    }

    const retryNextFailed = () => {
        const item = failedItems.value[0]
        if (!item) return false
        return retryItem(item)
    }

    const dismissItem = (target: ItemTarget<TUploaded>) => {
        const item = findItem(target)
        if (!item || item.status === 'uploading') return false
        removeFromQueue(item.id)
        items.value = items.value.filter((candidate) => candidate.id !== item.id)
        return true
    }

    const dismissFailed = () => {
        items.value = items.value.filter((item) => item.status !== 'failed')
    }

    const cancel = () => {
        queuedItems.value.forEach((item) => {
            item.status = 'canceled'
        })
        queuedItemIds.value = []
        if (currentItem.value) {
            currentItem.value.status = 'canceled'
        }
        options.abort?.()
    }

    const dispose = () => {
        isDisposed = true
        cancel()
    }

    return {
        items,
        queuedItems,
        failedItems,
        currentItem,
        isProcessing,
        queueCount,
        failedCount,
        enqueueFiles,
        retryItem,
        retryNextFailed,
        dismissItem,
        dismissFailed,
        cancel,
        dispose,
    }
}
