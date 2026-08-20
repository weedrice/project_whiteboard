import { mergeAttributes, Node } from '@tiptap/core'

export interface VideoOptions {
  HTMLAttributes: Record<string, unknown>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    video: {
      setVideo: (options: { src: string }) => ReturnType
    }
  }
}

/**
 * YouTube/Vimeo 등 iframe embed용 비디오 노드
 */
export const Video = Node.create<VideoOptions>({
  name: 'video',

  addOptions() {
    return {
      HTMLAttributes: {},
    }
  },

  group: 'block',

  draggable: true,

  addAttributes() {
    return {
      src: {
        default: null,
        parseHTML: el => (el as HTMLIFrameElement).getAttribute('src'),
        renderHTML: attrs => (attrs.src ? { src: attrs.src } : {}),
      },
      width: createIframeDimensionAttribute('width'),
      height: createIframeDimensionAttribute('height'),
    }
  },

  parseHTML() {
    return [
      {
        tag: 'iframe[src*="youtube.com/embed"], iframe[src*="youtube-nocookie.com/embed"], iframe[src*="player.vimeo.com/video/"]',
        getAttrs: el => getIframeAttributes(el as HTMLIFrameElement),
      },
      {
        tag: 'div.tiptap-video-wrapper',
        getAttrs: el => {
          const iframe = (el as HTMLElement).querySelector('iframe')
          return iframe ? getIframeAttributes(iframe) : false
        },
      },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      { class: 'tiptap-video-wrapper', 'data-video-embed': '' },
      [
        'iframe',
        mergeAttributes(
          {
            frameborder: '0',
            allowfullscreen: 'true',
            loading: 'lazy',
            referrerpolicy: 'strict-origin-when-cross-origin',
            sandbox: 'allow-scripts allow-same-origin allow-presentation',
            allow: 'encrypted-media; picture-in-picture',
          },
          this.options.HTMLAttributes,
          HTMLAttributes
        ),
      ],
    ]
  },

  addCommands() {
    return {
      setVideo:
        ({ src }) =>
        ({ commands }) => {
          return commands.insertContent({
            type: this.name,
            attrs: { src },
          })
        },
    }
  },
})

function createIframeDimensionAttribute(name: 'width' | 'height') {
  return {
    default: null,
    parseHTML: (element: HTMLElement) => {
      const iframe = element instanceof HTMLIFrameElement
        ? element
        : element.querySelector('iframe')
      return iframe?.getAttribute(name) ?? null
    },
    renderHTML: (attributes: Record<string, unknown>) => {
      const value = attributes[name]
      return typeof value === 'string' && value ? { [name]: value } : {}
    },
  }
}

function getIframeAttributes(iframe: HTMLIFrameElement) {
  return {
    src: iframe.getAttribute('src'),
    width: iframe.getAttribute('width'),
    height: iframe.getAttribute('height'),
  }
}
