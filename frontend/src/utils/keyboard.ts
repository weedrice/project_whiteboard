/**
 * 현재 포커스된 요소가 입력 필드인지 확인합니다.
 * 키보드 단축키에서 입력 중인 경우를 구분하기 위해 사용합니다.
 *
 * @returns 입력 필드(input, textarea, select, contenteditable, 에디터)에 포커스가 있으면 true
 */
export function isInputFocused(): boolean {
    const activeElement = document.activeElement
    if (!activeElement) return false
    const tagName = activeElement.tagName.toLowerCase()
    if (tagName === 'input' || tagName === 'textarea' || tagName === 'select') return true
    if (activeElement.getAttribute('contenteditable') === 'true') return true
    if (activeElement.closest('.ql-editor')) return true
    // TipTap 에디터 (ProseMirror)
    if (activeElement.closest('.ProseMirror')) return true
    return false
}
