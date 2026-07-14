import { setImageFallback } from '@/utils/imageFallback'

const DEFAULT_IMAGE_PLACEHOLDER_URL =
    'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="200" aria-hidden="true"%3E%3Crect width="200" height="200" fill="%23e5e7eb"/%3E%3Cpath d="M55 130l28-32 18 20 14-16 30 28H55z" fill="%239ca3af"/%3E%3Ccircle cx="78" cy="72" r="12" fill="%239ca3af"/%3E%3C/svg%3E'

export function getOptimizedImageUrl(
    url: string | null | undefined,
    _options?: {
        width?: number
        height?: number
        quality?: number
        format?: 'webp' | 'jpeg' | 'png'
    }
): string {
    if (!url) return ''

    return url
}

export function getOptimizedProfileImageUrl(
    url: string | null | undefined,
    size: number = 150
): string {
    return getOptimizedImageUrl(url, {
        width: size,
        height: size,
        quality: 85,
        format: 'webp'
    })
}

export function getOptimizedBoardIconUrl(
    url: string | null | undefined,
    size: number = 80
): string {
    return getOptimizedImageUrl(url, {
        width: size,
        height: size,
        quality: 85,
        format: 'webp'
    })
}

export function getOptimizedPostImageUrl(
    url: string | null | undefined,
    width: number = 800,
    height?: number
): string {
    return getOptimizedImageUrl(url, {
        width,
        height,
        quality: 85,
        format: 'webp'
    })
}

export function handleImageError(
    event: Event,
    placeholderUrl = DEFAULT_IMAGE_PLACEHOLDER_URL
): void {
    setImageFallback(event, placeholderUrl)
}
