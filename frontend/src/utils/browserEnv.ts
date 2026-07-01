export const getCurrentPathname = (): string => {
    if (typeof window === 'undefined') {
        return ''
    }
    return window.location.pathname
}

export const getWindowOrigin = (fallback = 'http://localhost'): string => {
    if (typeof window === 'undefined') {
        return fallback
    }
    return window.location.origin
}

export const getViewportWidth = (): number => {
    if (typeof window === 'undefined') {
        return Number.POSITIVE_INFINITY
    }
    return window.innerWidth
}

export const getViewportSize = () => {
    if (typeof window === 'undefined') {
        return {
            width: Number.POSITIVE_INFINITY,
            height: Number.POSITIVE_INFINITY,
        }
    }
    return {
        width: window.innerWidth,
        height: window.innerHeight,
    }
}

export const getWindowScroll = () => {
    if (typeof window === 'undefined') {
        return { x: 0, y: 0 }
    }
    return {
        x: window.scrollX,
        y: window.scrollY,
    }
}

export const isNarrowViewport = (maxExclusiveWidth: number): boolean => (
    getViewportWidth() < maxExclusiveWidth
)

export const matchesMediaQuery = (query: string): boolean => (
    typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia(query).matches
)
