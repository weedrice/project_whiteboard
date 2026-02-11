/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Quill 커스텀 이모티콘 Blot
 * 이모티콘 이미지를 인라인으로 삽입합니다.
 */

export const registerEmoticonBlot = (Quill: any) => {
    if (!Quill) {
        return
    }

    const Embed = Quill.import('blots/embed')

    class EmoticonBlot extends Embed {
        static blotName = 'emoticon'
        static tagName = 'img'
        static className = 'ql-emoticon'

        static create(value: { src: string; alt: string }) {
            const node = super.create() as HTMLImageElement
            node.setAttribute('src', value.src)
            node.setAttribute('alt', value.alt || ':emoticon:')
            node.setAttribute('class', 'ql-emoticon')
            node.style.width = '100px'
            node.style.height = '100px'
            node.style.verticalAlign = 'baseline'
            node.style.display = 'inline-block'
            node.style.margin = '0 4px'
            return node
        }

        static value(node: HTMLImageElement) {
            return {
                src: node.getAttribute('src'),
                alt: node.getAttribute('alt')
            }
        }
    }

    // true: 기존 blot 덮어쓰기 허용
    Quill.register(EmoticonBlot, true)
}

export default registerEmoticonBlot
