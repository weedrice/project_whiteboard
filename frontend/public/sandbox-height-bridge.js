(function () {
  'use strict'

  var script = document.currentScript
  var frameId = script ? script.getAttribute('data-frame-id') : ''
  var lastHeight = 0
  var responsiveStackAttribute = 'data-noviis-responsive-stack'
  var imageErrorLabel = script ? script.getAttribute('data-image-error-label') : ''
  var imageRecords = new WeakMap()
  var heightFrame = null
  var disposed = true

  if (!frameId) return

  function setImageState(image, state) {
    image.setAttribute('data-image-state', state)
    if (state === 'loading') image.setAttribute('aria-busy', 'true')
    else image.removeAttribute('aria-busy')
    var record = imageRecords.get(image)
    if (state === 'error' && imageErrorLabel) {
      image.setAttribute('aria-label', imageErrorLabel)
    } else if (record) {
      if (record.label === null) image.removeAttribute('aria-label')
      else image.setAttribute('aria-label', record.label)
    }
  }

  function settleImage(image) {
    if (!image.complete) return
    setImageState(image, image.naturalWidth > 0 ? 'loaded' : 'error')
    scheduleHeight()
  }

  function watchImage(image) {
    if (!imageRecords.has(image)) {
      imageRecords.set(image, { label: image.getAttribute('aria-label') })
      image.addEventListener('load', function () { settleImage(image) })
      image.addEventListener('error', function () {
        setImageState(image, 'error')
        scheduleHeight()
      })
    }
    setImageState(image, 'loading')
    settleImage(image)
  }

  function watchImages(root) {
    if (root.nodeType !== 1) return
    if (root.tagName === 'IMG') watchImage(root)
    var images = root.querySelectorAll('img')
    for (var index = 0; index < images.length; index += 1) watchImage(images[index])
  }

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

  function scheduleHeight() {
    if (disposed || heightFrame !== null) return
    heightFrame = window.requestAnimationFrame(function () {
      heightFrame = null
      if (!disposed) postHeight()
    })
  }

  var imageObserver = new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach(watchImages)
      } else if (mutation.target.tagName === 'IMG') {
        watchImage(mutation.target)
      } else if (mutation.target.tagName === 'SOURCE' && mutation.target.parentElement) {
        watchImages(mutation.target.parentElement)
      }
    })
  })
  var resizeObserver = null
  var intervalId = null

  function resume() {
    if (!disposed) return
    disposed = false
    lastHeight = 0
    window.addEventListener('load', scheduleHeight)
    window.addEventListener('resize', scheduleHeight)
    watchImages(document.body)
    imageObserver.observe(document.body, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['src', 'srcset', 'sizes'],
    })
    if (typeof ResizeObserver === 'function') {
      resizeObserver = new ResizeObserver(scheduleHeight)
      resizeObserver.observe(document.body)
    }
    intervalId = window.setInterval(scheduleHeight, 500)
    scheduleHeight()
  }

  function cleanup() {
    disposed = true
    if (heightFrame !== null) {
      window.cancelAnimationFrame(heightFrame)
      heightFrame = null
    }
    window.removeEventListener('load', scheduleHeight)
    window.removeEventListener('resize', scheduleHeight)
    imageObserver.disconnect()
    if (intervalId !== null) {
      window.clearInterval(intervalId)
      intervalId = null
    }
    if (resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver = null
    }
  }

  // A cached document returns without rerunning this script. Pause only once
  // navigation commits, then restore observers and listeners on every return.
  window.addEventListener('pagehide', cleanup)
  window.addEventListener('pageshow', function (event) {
    if (event.persisted) resume()
  })
  resume()
}())
