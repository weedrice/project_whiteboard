import { computed, ref } from 'vue'
import type { Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import type { AuthSessionIntentSource } from '@/utils/authSessionIntent'

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
    upload: (file: File, config?: AxiosRequestConfig) => Promise<TUploaded | null>
    authSource?: AuthSessionIntentSource
    isAbort: (error: unknown) => boolean
    onUploaded: (uploaded: TUploaded, file: File) => void
    onValidationError?: (error: unknown, file: File) => void
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
    let processingRevision = 0
    const scopes = new Map<number, AuthSessionScope>()
    const releaseScope = (id: number, cancel = false) => {
        const scope = scopes.get(id)
        if (cancel) scope?.cancel()
        scope?.dispose()
        scopes.delete(id)
    }

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
        const revision = processingRevision
        try {
            while (!isDisposed && revision === processingRevision && queuedItemIds.value.length > 0) {
                const itemId = queuedItemIds.value.shift()
                const item = typeof itemId === 'number' ? findItem(itemId) : undefined
                if (!item || item.status !== 'queued') continue

                if (isDisposed || scopes.get(item.id)?.isCurrent() === false) {
                    releaseScope(item.id)
                    item.status = 'canceled'
                    continue
                }

                item.status = 'uploading'
                item.error = undefined
                currentItem.value = item

                try {
                    const requestConfig = scopes.get(item.id)?.requestConfig
                    const uploaded = await (requestConfig ? options.upload(item.file, requestConfig) : options.upload(item.file))
                    if (isDisposed || revision !== processingRevision || scopes.get(item.id)?.isCurrent() === false || (item.status as EditorImageUploadStatus) === 'canceled') continue
                    if (uploaded) {
                        item.status = 'uploaded'
                        item.uploaded = uploaded
                        options.onUploaded(uploaded, item.file)
                    } else {
                        item.status = 'failed'
                        options.onFailed?.(null, item.file)
                    }
                } catch (error: unknown) {
                    if (revision !== processingRevision || scopes.get(item.id)?.isCurrent() === false || (item.status as EditorImageUploadStatus) === 'canceled' || options.isAbort(error)) {
                        item.status = 'canceled'
                        continue
                    }
                    item.status = 'failed'
                    item.error = error
                    options.onFailed?.(error, item.file)
                } finally {
                    if (item.status !== 'failed') releaseScope(item.id)
                    if (currentItem.value?.id === item.id) {
                        currentItem.value = null
                    }
                }
            }
        } finally {
            if (revision === processingRevision) isProcessing.value = false
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
            if (!isDisposed && options.authSource) scopes.set(item.id, createAuthSessionScope(options.authSource))
            items.value.push(item)
            if (item.status === 'queued') {
                enqueueItem(item)
            } else if (!isDisposed && validationError) {
                options.onValidationError?.(validationError, file)
            }
            return item
        })

        void processQueue()
        return createdItems
    }

    const retryItem = (target: ItemTarget<TUploaded>) => {
        if (isDisposed) return false
        const item = findItem(target)
        if (!item || item.status !== 'failed' || scopes.get(item.id)?.isCurrent() === false) return false

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
        releaseScope(item.id)
        items.value = items.value.filter((candidate) => candidate.id !== item.id)
        return true
    }

    const dismissFailed = () => {
        failedItems.value.forEach(item => releaseScope(item.id))
        items.value = items.value.filter((item) => item.status !== 'failed')
    }

    const cancel = () => {
        queuedItems.value.forEach((item) => {
            item.status = 'canceled'
            releaseScope(item.id, true)
        })
        queuedItemIds.value = []
        if (currentItem.value) {
            currentItem.value.status = 'canceled'
            releaseScope(currentItem.value.id, true)
        }
        options.abort?.()
    }

    const clear = () => {
        processingRevision += 1
        cancel()
        scopes.forEach(scope => { scope.cancel(); scope.dispose() })
        scopes.clear()
        items.value = []
        currentItem.value = null
        isProcessing.value = false
    }

    const dispose = () => {
        isDisposed = true
        clear()
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
        clear,
        dispose,
    }
}
