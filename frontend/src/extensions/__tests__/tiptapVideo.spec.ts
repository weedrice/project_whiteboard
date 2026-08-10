import { describe, it, expect, vi } from 'vitest'
import { Video } from '../tiptap-video'

type VideoAttributeHandlers = {
  src: {
    default: null
    parseHTML: (el: HTMLElement) => string | null
    renderHTML: (attrs: { src: string | null }) => Record<string, string>
  }
}

type ParseRule = {
  tag: string
  getAttrs?: (el: Element) => { src: string | null } | false
}

type RenderHTML = (
  this: { options: { HTMLAttributes: Record<string, unknown> } },
  props: { HTMLAttributes: Record<string, unknown> }
) => [string, Record<string, unknown>, [string, Record<string, unknown>]]

type AddCommands = (this: { name: string }) => {
  setVideo: (options: { src: string }) => (props: {
    commands: { insertContent: (content: unknown) => boolean }
  }) => boolean
}

describe('Video extension', () => {
  it('defines attributes and parse/render helpers for src', () => {
    expect(Video.name).toBe('video')
    expect(Video.options).toEqual({ HTMLAttributes: {} })

    const addAttributes = Video.config.addAttributes as () => VideoAttributeHandlers
    const attributes = addAttributes()
    expect(attributes).toBeDefined()
    expect(attributes.src.default).toBeNull()

    const iframe = document.createElement('iframe')
    iframe.setAttribute('src', 'https://www.youtube.com/embed/abc')

    expect(attributes.src.parseHTML(iframe)).toBe('https://www.youtube.com/embed/abc')
    expect(attributes.src.renderHTML({ src: 'https://www.youtube.com/embed/abc' })).toEqual({
      src: 'https://www.youtube.com/embed/abc'
    })
    expect(attributes.src.renderHTML({ src: null })).toEqual({})
  })

  it('parses iframe and wrapper html nodes', () => {
    const parseHTML = Video.config.parseHTML as () => ParseRule[]
    const parseRules = parseHTML()
    expect(parseRules).toHaveLength(2)

    const youtubeIframe = document.createElement('iframe')
    youtubeIframe.setAttribute('src', 'https://www.youtube.com/embed/123')

    expect(parseRules[0].getAttrs?.(youtubeIframe)).toEqual({
      src: 'https://www.youtube.com/embed/123'
    })
    expect(parseRules[0].tag).toContain('youtube-nocookie.com/embed')

    const wrapper = document.createElement('div')
    wrapper.className = 'tiptap-video-wrapper'
    const vimeoIframe = document.createElement('iframe')
    vimeoIframe.setAttribute('src', 'https://player.vimeo.com/video/123')
    wrapper.appendChild(vimeoIframe)

    expect(parseRules[1].getAttrs?.(wrapper)).toEqual({
      src: 'https://player.vimeo.com/video/123'
    })
    expect(parseRules[1].getAttrs?.(document.createElement('div'))).toBe(false)
  })

  it('renders wrapper and iframe with merged attributes', () => {
    const renderHTML = Video.config.renderHTML as RenderHTML
    const renderResult = renderHTML.call(
      {
        options: {
          HTMLAttributes: {
            title: 'Embedded Video'
          }
        }
      },
      {
      HTMLAttributes: { src: 'https://www.youtube.com/embed/xyz' }
      }
    )

    expect(renderResult[0]).toBe('div')
    expect(renderResult[1]).toEqual({
      class: 'tiptap-video-wrapper',
      'data-video-embed': ''
    })
    expect(renderResult[2]).toEqual([
      'iframe',
      expect.objectContaining({
        src: 'https://www.youtube.com/embed/xyz',
        title: 'Embedded Video',
        frameborder: '0',
        allowfullscreen: 'true',
        loading: 'lazy',
        referrerpolicy: 'strict-origin-when-cross-origin',
        sandbox: 'allow-scripts allow-same-origin allow-presentation',
        allow: 'encrypted-media; picture-in-picture'
      })
    ])
  })

  it('inserts video node through setVideo command', () => {
    const addCommands = Video.config.addCommands as AddCommands
    const commandFactory = addCommands.call({ name: 'video' })
    const insertContent = vi.fn().mockReturnValue(true)

    const result = commandFactory.setVideo({ src: 'https://www.youtube.com/embed/new' })({
      commands: { insertContent }
    })

    expect(insertContent).toHaveBeenCalledWith({
      type: 'video',
      attrs: { src: 'https://www.youtube.com/embed/new' }
    })
    expect(result).toBe(true)
  })
})
