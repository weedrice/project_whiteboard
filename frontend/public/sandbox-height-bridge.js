(function () {
  'use strict'

  var script = document.currentScript
  var frameId = script ? script.getAttribute('data-frame-id') : ''
  var lastHeight = 0
  var responsiveStackAttribute = 'data-noviis-responsive-stack'

  if (!frameId) return

  function enforceScrollableDocument() {
    var roots = [document.documentElement, document.body]
    for (var index = 0; index < roots.length; index += 1) {
      var root = roots[index]
      if (!root) continue
      root.style.setProperty('overflow-x', 'hidden', 'important')
      root.style.setProperty('overflow-y', 'auto', 'important')
    }
  }

  function hasHorizontalOverflow(element) {
    return element.clientWidth > 0 && element.scrollWidth > element.clientWidth + 1
  }

  function hasOverflowingDescendant(element) {
    var descendants = element.querySelectorAll('*')
    for (var index = 0; index < descendants.length; index += 1) {
      if (hasHorizontalOverflow(descendants[index])) return true
    }
    return false
  }

  function repairResponsiveGrids() {
    var isNarrow = document.documentElement.clientWidth <= 480
    var grids = document.querySelectorAll('.grid')
    for (var index = 0; index < grids.length; index += 1) {
      var grid = grids[index]
      if (!isNarrow) {
        grid.removeAttribute(responsiveStackAttribute)
        continue
      }
      if (grid.hasAttribute(responsiveStackAttribute)) continue
      var display = window.getComputedStyle(grid).display
      if (display !== 'grid' && display !== 'inline-grid') continue
      if (hasHorizontalOverflow(grid) || hasOverflowingDescendant(grid)) {
        grid.setAttribute(responsiveStackAttribute, '')
      }
    }
  }

  function cssPixels(value) {
    return parseFloat(value) || 0
  }

  function measure() {
    var body = document.body
    if (!body) return 0
    var bodyRect = body.getBoundingClientRect()
    var bottom = bodyRect.bottom
    var descendants = body.querySelectorAll('*')
    var fixedSubtree = new WeakSet()
    for (var index = 0; index < descendants.length; index += 1) {
      var element = descendants[index]
      var parentElement = element.parentElement
      if (
        (parentElement && fixedSubtree.has(parentElement))
        || window.getComputedStyle(element).position === 'fixed'
      ) {
        fixedSubtree.add(element)
        continue
      }
      bottom = Math.max(bottom, element.getBoundingClientRect().bottom)
    }
    var bodyStyle = window.getComputedStyle(body)
    var rootStyle = window.getComputedStyle(document.documentElement)
    var bodyMargins = cssPixels(bodyStyle.marginTop) + cssPixels(bodyStyle.marginBottom)
    var rootInsets = cssPixels(rootStyle.paddingTop) + cssPixels(rootStyle.paddingBottom)
      + cssPixels(rootStyle.borderTopWidth) + cssPixels(rootStyle.borderBottomWidth)
    return Math.max(0, Math.ceil(
      Math.max(body.offsetHeight, bottom - bodyRect.top) + bodyMargins + rootInsets,
    ))
  }

  function postHeight() {
    enforceScrollableDocument()
    repairResponsiveGrids()
    var height = measure()
    if (Math.abs(height - lastHeight) < 2) return
    lastHeight = height
    parent.postMessage({
      type: 'noviis-post-html-height',
      channel: 'noviis-post-html-sandbox',
      id: frameId,
      height: height,
    }, '*')
  }

  window.addEventListener('load', postHeight)
  window.addEventListener('resize', postHeight)
  var resizeObserver = null
  if (typeof ResizeObserver === 'function') {
    resizeObserver = new ResizeObserver(postHeight)
    resizeObserver.observe(document.body)
  }
  var intervalId = window.setInterval(postHeight, 500)

  function cleanup() {
    if (intervalId !== null) {
      window.clearInterval(intervalId)
      intervalId = null
    }
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  }

  window.addEventListener('pagehide', cleanup, { once: true })
  window.addEventListener('beforeunload', cleanup, { once: true })
  postHeight()
}())
