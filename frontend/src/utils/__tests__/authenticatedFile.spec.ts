import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  resolveAuthenticatedFileDisposition,
  resolveAuthenticatedFileRequestPath,
} from '@/utils/authenticatedFile'

const utf8ContentDispositionContract = readFileSync(
  resolve(process.cwd(), '../backend/src/test/resources/contracts/file-download-content-disposition-utf8.txt'),
  'utf8',
).trim()

describe('authenticatedFile', () => {
  it('resolves same-origin file and variant API paths', () => {
    expect(resolveAuthenticatedFileRequestPath(
      '/api/v1/files/11?download=true',
      'https://noviis.kr',
    )).toBe('/files/11?download=true')
    expect(resolveAuthenticatedFileRequestPath(
      'https://noviis.kr/api/v1/files/12/variants/thumbnail',
      'https://noviis.kr',
    )).toBe('/files/12/variants/thumbnail')
    expect(resolveAuthenticatedFileRequestPath(
      'https://cdn.noviis.kr/files/12',
      'https://noviis.kr',
    )).toBeNull()
  })

  it('preserves UTF-8 attachment filenames and rejects path separators', () => {
    expect(resolveAuthenticatedFileDisposition(
      "attachment; filename=report.pdf; filename*=UTF-8''%EB%B3%B4%EA%B3%A0%EC%84%9C.pdf",
    )).toEqual({
      forceDownload: true,
      fileName: '보고서.pdf',
    })
    expect(resolveAuthenticatedFileDisposition(
      'attachment; filename="../unsafe:report?.txt"',
    )).toEqual({
      forceDownload: true,
      fileName: '.._unsafe_report_.txt',
    })
  })

  it('parses the UTF-8 Content-Disposition contract emitted by the backend', () => {
    expect(resolveAuthenticatedFileDisposition(utf8ContentDispositionContract)).toEqual({
      forceDownload: true,
      fileName: '보고서.pdf',
    })
  })

  it('does not force inline responses to download', () => {
    expect(resolveAuthenticatedFileDisposition('inline; filename="image.png"'))
      .toEqual({ forceDownload: false })
    expect(resolveAuthenticatedFileDisposition(undefined))
      .toEqual({ forceDownload: false })
  })
})
