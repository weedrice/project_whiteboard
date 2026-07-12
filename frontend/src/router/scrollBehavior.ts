import type { RouteLocationNormalized, RouterScrollBehavior } from 'vue-router'
import { getMotionAwareScrollBehavior } from '@/utils/motion'

const isSamePostDetailRoute = (to: RouteLocationNormalized, from: RouteLocationNormalized) => (
    to.name === 'post-detail'
    && from.name === 'post-detail'
    && to.params.boardUrl === from.params.boardUrl
    && to.params.postId === from.params.postId
)

const isPostDetailListPageNavigation = (to: RouteLocationNormalized, from: RouteLocationNormalized) => (
    isSamePostDetailRoute(to, from)
    && String(to.query.page ?? '1') !== String(from.query.page ?? '1')
)

export const scrollBehavior: RouterScrollBehavior = (to, from, savedPosition) => {
    if (savedPosition) {
        return savedPosition
    }
    if (to.hash) {
        return { el: to.hash, behavior: getMotionAwareScrollBehavior() }
    }
    if (isPostDetailListPageNavigation(to, from)) {
        return false
    }
    return { top: 0 }
}
