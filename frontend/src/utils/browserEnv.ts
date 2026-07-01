export const getCurrentPathname = (): string => {
    if (typeof window === 'undefined') {
        return ''
    }
    return window.location.pathname
}

export const getViewportWidth = (): number => {
    if (typeof window === 'undefined') {
        return Number.POSITIVE_INFINITY
    }
    return window.innerWidth
}

export const isNarrowViewport = (maxExclusiveWidth: number): boolean => (
    getViewportWidth() < maxExclusiveWidth
)
