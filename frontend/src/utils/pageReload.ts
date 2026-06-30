export function reloadPage() {
    if (typeof window === 'undefined') return
    window.location.reload()
}
