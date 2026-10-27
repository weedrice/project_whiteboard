/**
 * Returns whether the currently focused element is a text-entry surface.
 * Global shortcuts use this to avoid hijacking user input.
 */
export function isInputFocused(): boolean {
    if (typeof document === 'undefined') return false

    const activeElement = document.activeElement
    if (!activeElement) return false

    const tagName = activeElement.tagName.toLowerCase()
    if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') return true
    if (activeElement.getAttribute('contenteditable') === 'true') return true
    if (activeElement.closest('.ql-editor')) return true
    if (activeElement.closest('.ProseMirror')) return true
    return false
}

export function isComposingKeyboardEvent(event: KeyboardEvent): boolean {
    return event.isComposing || event.keyCode === 229
}

/** Page-level shortcuts must not escape the active modal, including button focus. */
export function isModalDialogOpen(): boolean {
    if (typeof document === 'undefined') return false
    return document.querySelector('[role="dialog"][aria-modal="true"], [role="alertdialog"][aria-modal="true"], dialog[open]') !== null
}
