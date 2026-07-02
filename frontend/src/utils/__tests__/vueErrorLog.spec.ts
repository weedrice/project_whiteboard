import { describe, expect, it } from 'vitest'
import type { ComponentPublicInstance } from 'vue'
import { createErrorLogPayload, createVueErrorLogPayload } from '../vueErrorLog'

describe('createVueErrorLogPayload', () => {
    it('serializes errors without exposing the full component instance', () => {
        const error = new Error('boom')
        const instance = {
            $: {
                type: {
                    name: 'ProfileEditor',
                },
            },
            sensitiveField: 'should not be logged',
        } as unknown as ComponentPublicInstance

        expect(createVueErrorLogPayload(error, instance, 'render function')).toEqual({
            error: {
                name: 'Error',
                message: 'boom',
                stack: error.stack,
            },
            info: 'render function',
            component: 'ProfileEditor',
        })
    })

    it('serializes generic errors for non-Vue logging paths', () => {
        const error = new TypeError('bad query')

        expect(createErrorLogPayload(error)).toEqual({
            error: {
                name: 'TypeError',
                message: 'bad query',
                stack: error.stack,
            },
        })
    })
})
