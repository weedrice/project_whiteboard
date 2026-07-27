import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '../routes'

const flattenRoutes = (records: RouteRecordRaw[]): RouteRecordRaw[] =>
    records.flatMap((route) => [route, ...(route.children ? flattenRoutes(route.children) : [])])

const byName = new Map(flattenRoutes(routes).map((route) => [route.name, route]))

describe('routes table', () => {
    it('keeps inquiry before dynamic board detail routes', () => {
        const inquiryIndex = routes.findIndex((route) => route.path === '/inquiry')
        const boardDetailIndex = routes.findIndex((route) => route.path === '/board/:boardUrl')

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

    it('hides the shop route and protects purchase history through my page', () => {
        expect(byName.has('shop')).toBe(false)
        expect(routes.some((route) => route.path === '/shop')).toBe(false)
        expect(byName.get('purchase-history')).toMatchObject({ path: 'purchases' })
        expect(routes.find((route) => route.path === '/mypage')?.meta).toMatchObject({ requiresAuth: true })
    })
})
