import { afterEach, describe, expect, it, vi } from 'vitest'
import { reloadPage } from '../pageReload'

describe('reloadPage', () => {
    afterEach(() => {
        vi.unstubAllGlobals()
    })

    it('does not throw when window is unavailable', () => {
        vi.stubGlobal('window', undefined)

        expect(() => reloadPage()).not.toThrow()
    })
})
