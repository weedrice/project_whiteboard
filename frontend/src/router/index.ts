import { createRouter, createWebHistory } from 'vue-router'
import { createAppNavigationGuard } from '@/router/guards'
import { installChunkLoadRecovery } from '@/router/chunkLoadRecovery'
import { routes } from '@/router/routes'
import { scrollBehavior } from '@/router/scrollBehavior'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    scrollBehavior,
    routes,
})

router.beforeEach(createAppNavigationGuard())
installChunkLoadRecovery(router)

export default router
