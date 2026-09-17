import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '../routes'

const flattenRoutes = (records: RouteRecordRaw[]): RouteRecordRaw[] =>
    records.flatMap((route) => [route, ...(route.children ? flattenRoutes(route.children) : [])])

const byName = new Map(flattenRoutes(routes).map((route) => [route.name, route]))

describe('routes table', () => {
    it('keeps inquiry before dynamic board detail routes', () => {
        const inquiryIndex = routes.findIndex((route) => route.path === '/inquiry')
        const boardDetailIndex = routes.findIndex((route) => route.path === '/board/:boardUrl/')

        expect(inquiryIndex).toBeGreaterThanOrEqual(0)
        expect(boardDetailIndex).toBeGreaterThanOrEqual(0)
        expect(inquiryIndex).toBeLessThan(boardDetailIndex)
    })

    it('protects write, edit and admin management routes with the expected meta flags', () => {
        expect(routes.find((route) => route.path === '/auth')?.meta).toMatchObject({
            guestOnly: true,
            layout: 'BareLayout',
        })
        expect(byName.get('post-write')?.meta).toMatchObject({
            requiresAuth: true,
            requiresWritableBoard: true,
        })
        expect(byName.get('post-edit')?.meta).toMatchObject({
            requiresAuth: true,
            requiresPostAuthor: true,
        })
        expect(byName.get('board-edit')?.meta).toMatchObject({
            requiresAuth: true,
            requiresBoardAdmin: true,
        })
        expect(routes.find((route) => route.path === '/admin')?.meta).toMatchObject({
            requiresAuth: true,
            roles: ['SUPER_ADMIN'],
            layout: 'AdminLayout',
        })
    })

    it('keeps route names unique and unknown paths redirected to the error route', () => {
        const routeNames = flattenRoutes(routes)
            .map((route) => route.name)
            .filter(Boolean)

        expect(new Set(routeNames).size).toBe(routeNames.length)
        expect(routes.at(-1)).toMatchObject({
            path: '/:pathMatch(.*)*',
            redirect: {
                name: 'error',
                query: { status: '404' },
            },
        })
    })

    it('exposes the authenticated attendance calendar under my page', () => {
        expect(byName.get('MyAttendance')).toMatchObject({ path: 'attendance' })
    })

    it('exposes earned badges under the authenticated my page', () => {
        expect(byName.get('MyBadges')).toMatchObject({ path: 'badges' })
        expect(routes.find((route) => route.path === '/mypage')?.meta).toMatchObject({ requiresAuth: true })
    })

    it('renders inquiry pages inside the authenticated my page layout', () => {
        const myPageRoute = routes.find((route) => route.path === '/mypage')
        const router = createRouter({ history: createMemoryHistory(), routes })
        const resolvedDetail = router.resolve('/inquiries/42')

        expect(myPageRoute?.children).toEqual(expect.arrayContaining([
            expect.objectContaining({ name: 'inquiry-list', path: '/inquiries' }),
            expect.objectContaining({ name: 'inquiry-new', path: '/inquiries/new' }),
            expect.objectContaining({ name: 'inquiry-detail', path: '/inquiries/:inquiryId' }),
        ]))
        expect(routes.filter((route) => route.path.startsWith('/inquiries'))).toHaveLength(0)
        expect(resolvedDetail.matched.map((route) => route.path)).toEqual([
            '/mypage',
            '/inquiries/:inquiryId',
        ])
        expect(resolvedDetail.meta).toMatchObject({
            requiresAuth: true,
            positiveIntegerParams: ['inquiryId'],
        })
    })

    it('hides the shop route and protects purchase history through my page', () => {
        expect(byName.has('shop')).toBe(false)
        expect(routes.some((route) => route.path === '/shop')).toBe(false)
        expect(byName.get('purchase-history')).toMatchObject({ path: 'purchases' })
        expect(routes.find((route) => route.path === '/mypage')?.meta).toMatchObject({ requiresAuth: true })
    })
})
