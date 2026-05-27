export interface ImageFileValidationOptions {
  allowedMimeTypes?: ReadonlySet<string>
  allowedExtensions?: ReadonlySet<string>
  maxSizeBytes?: number
}

export function getFileExtension(fileName: string): string {
  const lastDotIndex = fileName.lastIndexOf('.')
  return lastDotIndex >= 0 ? fileName.slice(lastDotIndex).toLowerCase() : ''
}

export function validateImageFile(file: File, options: ImageFileValidationOptions): 'type' | 'size' | null {
  const mimeType = file.type.toLowerCase()
  const extension = getFileExtension(file.name)

  if (options.allowedMimeTypes && !options.allowedMimeTypes.has(mimeType)) {
    return 'type'
  }

  if (options.allowedExtensions && !options.allowedExtensions.has(extension)) {
    return 'type'
  }

  if (options.maxSizeBytes != null && file.size > options.maxSizeBytes) {
    return 'size'
  }

  return null
}

export function revokeBlobUrlIfNeeded(url: string | null | undefined): void {
  if (url?.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

export function readBlobAsArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer()
  }
  return new Response(blob).arrayBuffer()
}

export async function cloneBlobWithType(blob: Blob, type: string): Promise<Blob> {
  const buffer = await readBlobAsArrayBuffer(blob)
  return new Blob([buffer], { type })
}

function loadImageFromObjectUrl(file: File): Promise<{ image: HTMLImageElement; objectUrl: string }> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    const objectUrl = URL.createObjectURL(file)

    image.onload = () => {
      resolve({ image, objectUrl })
    }

    image.onerror = () => {
      revokeBlobUrlIfNeeded(objectUrl)
      reject(new Error('Image load failed'))
    }

    image.src = objectUrl
  })
}

function calculateBoundedSize(width: number, height: number, maxWidth: number, maxHeight: number) {
  let nextWidth = width
  let nextHeight = height

  if (nextWidth > nextHeight) {
    if (nextWidth > maxWidth) {
      nextHeight *= maxWidth / nextWidth
      nextWidth = maxWidth
    }
  } else if (nextHeight > maxHeight) {
    nextWidth *= maxHeight / nextHeight
    nextHeight = maxHeight
  }

  return {
    width: nextWidth,
    height: nextHeight,
  }
}

export async function resizeImageToBoundsFile(file: File, maxWidth: number, maxHeight: number): Promise<File> {
  const { image, objectUrl } = await loadImageFromObjectUrl(file)

  try {
    const { width, height } = calculateBoundedSize(image.width, image.height, maxWidth, maxHeight)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Could not get canvas context')
    }

    context.drawImage(image, 0, 0, width, height)

    return await new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(new File([blob], file.name, { type: file.type }))
          return
        }
        reject(new Error('Canvas to Blob failed'))
      }, file.type)
    })
  } finally {
    revokeBlobUrlIfNeeded(objectUrl)
  }
}

export async function resizeImageToMaxDimensionBlob(file: File, maxSize: number): Promise<Blob> {
  const type = file.type || 'image/png'

  if (file.type === 'image/gif') {
    return cloneBlobWithType(file, 'image/gif')
  }

  const { image, objectUrl } = await loadImageFromObjectUrl(file)

  try {
    let { width, height } = image

    if (width <= maxSize && height <= maxSize) {
      return cloneBlobWithType(file, type)
    }

    if (width > height) {
      height = Math.round((height * maxSize) / width)
      width = maxSize
    } else {
      width = Math.round((width * maxSize) / height)
      height = maxSize
    }

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Canvas context not available')
    }

    context.drawImage(image, 0, 0, width, height)

    return await new Promise((resolve, reject) => {
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(blob)
          } else {
            reject(new Error('Failed to create blob'))
          }
        },
        type,
        0.9
      )
    })
  } finally {
    revokeBlobUrlIfNeeded(objectUrl)
  }
}
