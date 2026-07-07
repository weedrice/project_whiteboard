export type HtmlDocumentTransformer = (doc: Document) => void

export function transformHtmlDocument(html: string, transformers: HtmlDocumentTransformer[]): string {
  if (typeof DOMParser === 'undefined' || transformers.length === 0) {
    return html
  }

  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  transformers.forEach((transformer) => transformer(doc))
  return doc.body.innerHTML
}

