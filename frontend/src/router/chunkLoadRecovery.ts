import type { Router } from 'vue-router'
import logger from '@/utils/logger'
import { reloadPage } from '@/utils/pageReload'
import { SessionStorage } from '@/utils/storage'

const CHUNK_RELOAD_KEY = 'chunk-reload-attempted'

const isChunkLoadError = (error: Error) => (
    error.message.includes('Failed to fetch dynamically imported module')
    || error.message.includes('Importing a module script failed')
)

export const installChunkLoadRecovery = (router: Router) => {
    router.afterEach(() => {
        if (SessionStorage.getString(CHUNK_RELOAD_KEY)) {
            SessionStorage.remove(CHUNK_RELOAD_KEY)
        }
    })

    router.onError((error) => {
        if (isChunkLoadError(error)) {
            const alreadyRetried = SessionStorage.getString(CHUNK_RELOAD_KEY) === '1'
            if (!alreadyRetried) {
                SessionStorage.setString(CHUNK_RELOAD_KEY, '1')
                reloadPage()
                return
            }
            SessionStorage.remove(CHUNK_RELOAD_KEY)
            router.push({
                name: 'error',
                query: {
                    status: '500',
                    message: 'Chunk load failed after retry',
                },
            })
            return
        }

        logger.error('Router Error:', error)
        router.push({
            name: 'error',
            query: {
                status: '500',
                message: error.message || 'Navigation Error',
            },
        })
    })
}
