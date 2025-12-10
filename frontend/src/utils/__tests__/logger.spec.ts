import { describe, it, expect } from 'vitest'
import logger from '../logger'

describe('Logger', () => {
    it('should be defined', () => {
        expect(logger).toBeDefined()
    })

    it('should have log levels', () => {
        expect(logger.trace).toBeDefined()
        expect(logger.debug).toBeDefined()
        expect(logger.info).toBeDefined()
        expect(logger.warn).toBeDefined()
        expect(logger.error).toBeDefined()
    })
})
