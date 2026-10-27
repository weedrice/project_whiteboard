export type PostHtmlDisplay = {
  html: string
  hasViewport: boolean
  empty: boolean
}

/** Remove viewport declarations only from a display copy, never saved HTML. */
export function preparePostHtmlDisplay(html: string): PostHtmlDisplay {
  const unchanged = { html, hasViewport: false, empty: false }
  if (!html.toLowerCase().includes('data-noviis-layer') || typeof DOMParser === 'undefined') return unchanged

  const doc = new DOMParser().parseFromString(html, 'text/html')
  const declarations = doc.querySelectorAll('template[data-noviis-layer="viewport"]')
  if (!declarations.length) return unchanged
  declarations.forEach(element => element.remove())

  // Keep styles and empty layout elements: they can paint decorations or
  // pseudo-elements without text. Only a declaration-only document is empty.
  const hasStyle = doc.querySelector('style, link[rel="stylesheet"]') !== null
    || [doc.body, doc.documentElement].some(element => (
      ['style', 'class', 'id'].some(attribute => Boolean(element.getAttribute(attribute)?.trim()))
    ))
  const body = doc.body.cloneNode(true) as HTMLElement
  body.querySelectorAll('script, meta, base, link, title, template').forEach(element => element.remove())
  const empty = !hasStyle && !body.textContent?.trim() && body.children.length === 0
  return {
    html: empty ? '' : `<!doctype html>${doc.documentElement.outerHTML}`,
    hasViewport: true,
    empty,
  }
}
