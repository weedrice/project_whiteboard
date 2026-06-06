/**
 * Returns whether the currently focused element is a text-entry surface.
 * Global shortcuts use this to avoid hijacking user input.
 */
export function isInputFocused(): boolean {
    const activeElement = document.activeElement
    if (!activeElement) return false
    const tagName = activeElement.tagName.toLowerCase()
    if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') return true
    if (activeElement.getAttribute('contenteditable') === 'true') return true
    if (activeElement.closest('.ql-editor')) return true
    if (activeElement.closest('.ProseMirror')) return true
    return false
}
