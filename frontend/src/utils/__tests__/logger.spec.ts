import { describe, it, expect, vi, beforeEach } from 'vitest'

type LogMock = {
  setLevel: ReturnType<typeof vi.fn>
  trace: ReturnType<typeof vi.fn>
  debug: ReturnType<typeof vi.fn>
  info: ReturnType<typeof vi.fn>
  warn: ReturnType<typeof vi.fn>
  error: ReturnType<typeof vi.fn>
}

async function importLoggerWithMocks() {
  const logMock: LogMock = {
    setLevel: vi.fn(),
    trace: vi.fn(),
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn()
  }
  const prefixMock = {
    reg: vi.fn(),
    apply: vi.fn()
  }

  vi.doMock('loglevel', () => ({
    default: logMock
  }))
  vi.doMock('loglevel-plugin-prefix', () => ({
    default: prefixMock
  }))

  const loggerModule = await import('../logger')
  return { logger: loggerModule.default, logMock, prefixMock }
}

describe('logger', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    vi.unstubAllEnvs()
  })

  it('registers prefix plugin and applies formatter options', async () => {
    const { logMock, prefixMock } = await importLoggerWithMocks()

    expect(prefixMock.reg).toHaveBeenCalledWith(logMock)
    expect(prefixMock.apply).toHaveBeenCalledTimes(1)

    const [targetLog, options] = prefixMock.apply.mock.calls[0]
    expect(targetLog).toBe(logMock)
    expect(options.template).toBe('[%t] %l:')
    expect(options.levelFormatter('warn')).toBe('WARN')
    expect(options.nameFormatter(undefined)).toBe('root')
    expect(options.nameFormatter('api')).toBe('api')
    expect(options.timestampFormatter(new Date('2026-01-01T00:00:00Z'))).toBeTypeOf('string')
  })

  it('sets default level based on environment and exports logger object', async () => {
    const { logger, logMock } = await importLoggerWithMocks()

    expect(logMock.setLevel).toHaveBeenCalledTimes(1)
    expect(logMock.setLevel).toHaveBeenCalledWith(expect.stringMatching(/trace|error/))
    expect(logger).toBe(logMock)
    expect(logger.trace).toBeDefined()
    expect(logger.debug).toBeDefined()
    expect(logger.info).toBeDefined()
    expect(logger.warn).toBeDefined()
    expect(logger.error).toBeDefined()
  })

  it('sets trace level in development env', async () => {
    vi.stubEnv('DEV', true)
    const { logMock } = await importLoggerWithMocks()

    expect(logMock.setLevel).toHaveBeenCalledWith('trace')
  })

  it('sets error level in production env', async () => {
    vi.stubEnv('DEV', false)
    const { logMock } = await importLoggerWithMocks()

    expect(logMock.setLevel).toHaveBeenCalledWith('error')
  })
})
