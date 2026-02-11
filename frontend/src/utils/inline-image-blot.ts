/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Quill 이미지를 인라인(텍스트처럼)으로 표시하는 Blot
 * 기본 image 포맷을 상속해 src/alt 등은 그대로 두고, 인라인 스타일만 추가합니다.
 */

export const registerInlineImageBlot = (Quill: any) => {
    if (!Quill) return

    const BaseImage = Quill.import('formats/image') as any

    class InlineImageBlot extends BaseImage {
        static create(value: unknown) {
            const node = super.create(value) as HTMLImageElement
            node.setAttribute('class', (node.getAttribute('class') || '') + ' ql-image-inline')
            node.style.display = 'inline-block'
            node.style.verticalAlign = 'baseline'
            node.style.maxWidth = '100%'
            node.style.height = 'auto'
            return node
        }
    }

    InlineImageBlot.blotName = 'image'
    InlineImageBlot.tagName = 'IMG'
    InlineImageBlot.className = 'ql-image'
    Quill.register(InlineImageBlot, true)
}

export default registerInlineImageBlot
