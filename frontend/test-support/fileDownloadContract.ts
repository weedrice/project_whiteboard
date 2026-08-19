import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const CONTRACT_PATH_SEGMENTS = [
  'backend',
  'src',
  'test',
  'resources',
  'contracts',
  'file-download-content-disposition-utf8.txt',
]

export function readUtf8ContentDispositionContract(startDirectory = process.cwd()): string {
  let currentDirectory = resolve(startDirectory)

  while (true) {
    const candidate = resolve(currentDirectory, ...CONTRACT_PATH_SEGMENTS)
    if (existsSync(candidate)) {
      return readFileSync(candidate, 'utf8').trim()
    }

    const parentDirectory = dirname(currentDirectory)
    if (parentDirectory === currentDirectory) {
      throw new Error(
        `Unable to locate ${CONTRACT_PATH_SEGMENTS.join('/')} from ${startDirectory}`,
      )
    }
    currentDirectory = parentDirectory
  }
}
