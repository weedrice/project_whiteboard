export function getHashToken(key: string): string | null {
  const rawHash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : window.location.hash
  if (!rawHash) return null
  return new URLSearchParams(rawHash).get(key)
}

export function clearSensitiveTokensFromUrl() {
  const current = new URL(window.location.href)
  const hadQueryToken = current.searchParams.has('accessToken') || current.searchParams.has('refreshToken')
  current.searchParams.delete('accessToken')
  current.searchParams.delete('refreshToken')

  let hadHashToken = false
  if (current.hash) {
    const rawHash = current.hash.startsWith('#') ? current.hash.slice(1) : current.hash
    const hashParams = new URLSearchParams(rawHash)
    hadHashToken = hashParams.has('accessToken') || hashParams.has('refreshToken')
    hashParams.delete('accessToken')
    hashParams.delete('refreshToken')
    const cleanedHash = hashParams.toString()
    current.hash = cleanedHash ? `#${cleanedHash}` : ''
  }

  if (hadQueryToken || hadHashToken) {
    const cleanUrl = `${current.pathname}${current.search}${current.hash}`
    window.history.replaceState(window.history.state, document.title, cleanUrl)
  }
}
