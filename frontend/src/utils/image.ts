/**
 * 이미지 최적화 유틸리티 함수
 */

/**
 * 이미지 URL에 최적화 파라미터 추가
 * 
 * @param url 원본 이미지 URL
 * @param options 최적화 옵션
 * @returns 최적화된 이미지 URL
 * 
 * @example
 * ```typescript
 * // 기본 사용
 * getOptimizedImageUrl(imageUrl, { width: 800 })
 * 
 * // WebP 형식으로 변환
 * getOptimizedImageUrl(imageUrl, { width: 800, format: 'webp' })
 * 
 * // 품질 조정
 * getOptimizedImageUrl(imageUrl, { width: 800, quality: 80 })
 * ```
 */
export function getOptimizedImageUrl(
    url: string | null | undefined,
    options?: {
        width?: number
        height?: number
        quality?: number
        format?: 'webp' | 'jpeg' | 'png'
    }
): string {
    if (!url) return ''
    
    // CDN이나 이미지 최적화 서비스가 없는 경우 원본 URL 반환
    // 실제 프로젝트에서는 Cloudinary, Imgix, 또는 자체 이미지 최적화 서비스 사용
    // 예시: Cloudinary 형식
    // return `https://res.cloudinary.com/your-cloud/image/upload/w_${width},q_${quality},f_${format}/${url}`
    
    // 현재는 원본 URL 반환 (향후 CDN 연동 시 수정)
    // 브라우저 네이티브 lazy loading과 함께 사용
    return url
}

/**
 * 프로필 이미지 최적화 URL 생성
 * 
 * @param url 프로필 이미지 URL
 * @param size 이미지 크기 (정사각형)
 * @returns 최적화된 프로필 이미지 URL
 */
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

/**
 * 게시판 아이콘 최적화 URL 생성
 * 
 * @param url 게시판 아이콘 URL
 * @param size 이미지 크기 (정사각형)
 * @returns 최적화된 게시판 아이콘 URL
 */
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

/**
 * 게시글 이미지 최적화 URL 생성
 * 
 * @param url 게시글 이미지 URL
 * @param width 이미지 너비
 * @param height 이미지 높이 (선택)
 * @returns 최적화된 게시글 이미지 URL
 */
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

/**
 * 이미지 로드 에러 핸들러
 * 기본 placeholder 이미지로 대체
 * 
 * @param event 이미지 로드 에러 이벤트
 * @param placeholderUrl 대체 이미지 URL (선택)
 */
export function handleImageError(
    event: Event,
    placeholderUrl?: string
): void {
    const img = event.target as HTMLImageElement
    if (img && placeholderUrl) {
        img.src = placeholderUrl
    } else {
        // 기본 placeholder
        img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="200"%3E%3Crect width="200" height="200" fill="%23e5e7eb"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" dy=".3em" fill="%239ca3af" font-size="14"%3ENo Image%3C/text%3E%3C/svg%3E'
    }
}
