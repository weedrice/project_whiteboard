const LEGACY_FILE_URL_PATTERN = /^\/files\/(\d+)([?#].*)?$/
const LEGACY_MARKDOWN_FILE_PATTERN = /!\[emoticon\]\(\/files\/(\d+)([?#][^)]*)?\)/g
const LEGACY_HTML_FILE_PATTERN = /(\s(?:src|href)=["'])\/files\/(\d+)([?#][^"']*)?(["'])/gi

export function normalizeFileUrl(url: string): string {
    return url.replace(LEGACY_FILE_URL_PATTERN, '/api/v1/files/$1$2')
}

export function normalizeLegacyFileUrls(content: string): string {
    return content
        .replace(LEGACY_MARKDOWN_FILE_PATTERN, '![emoticon](/api/v1/files/$1$2)')
        .replace(LEGACY_HTML_FILE_PATTERN, '$1/api/v1/files/$2$3$4')
}
