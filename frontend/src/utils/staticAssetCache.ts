export const STATIC_ASSET_CACHE_NAME = 'noviis-static-v1'

export function cacheableStaticAssetResponse(response: Response | undefined): Response | null {
  if (!response || response.status !== 200) return null

  const contentType = response.headers.get('Content-Type')?.split(';', 1)[0]?.trim().toLowerCase()
  if (contentType === 'text/html' || contentType === 'application/xhtml+xml') return null

  return response
}

export async function purgeInvalidStaticAssetCache(storage: CacheStorage): Promise<void> {
  if (!await storage.has(STATIC_ASSET_CACHE_NAME)) return

  const cache = await storage.open(STATIC_ASSET_CACHE_NAME)
  const requests = await cache.keys()
  await Promise.all(requests.map(async (request) => {
    const response = await cache.match(request)
    if (response && !cacheableStaticAssetResponse(response)) await cache.delete(request)
  }))
}
