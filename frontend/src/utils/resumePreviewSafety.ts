const allowedTags = new Set([
  'HTML', 'HEAD', 'BODY', 'TITLE', 'STYLE', 'MAIN', 'SECTION', 'HEADER', 'FOOTER', 'DIV', 'P', 'SPAN',
  'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'UL', 'OL', 'LI', 'STRONG', 'EM', 'B', 'I', 'BR', 'HR',
])

function safeCss(value: string) {
  return value
    .replace(/@import[^;]*(?:;|$)/gi, '')
    .replace(/url\s*\([^)]*\)/gi, '')
    .replace(/expression\s*\([^)]*\)/gi, '')
    .replace(/-moz-binding\s*:[^;]*(?:;|$)/gi, '')
}

export function sanitizeResumePreview(html: string) {
  const source = new DOMParser().parseFromString(html, 'text/html')
  for (const element of Array.from(source.querySelectorAll('*'))) {
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      continue
    }
    for (const attribute of Array.from(element.attributes)) {
      if (attribute.name === 'class') continue
      if (attribute.name === 'style') {
        element.setAttribute('style', safeCss(attribute.value))
        continue
      }
      element.removeAttribute(attribute.name)
    }
    if (element.tagName === 'STYLE') element.textContent = safeCss(element.textContent ?? '')
  }
  const csp = '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:">'
  return `<!doctype html><html><head>${csp}${source.head.innerHTML}</head><body>${source.body.innerHTML}</body></html>`
}
