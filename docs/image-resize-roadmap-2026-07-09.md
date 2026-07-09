# Image Resize Roadmap

## Context

The current file upload flow stores the original file and returns the original download URL through
`FileUrlResolver.resolve(fileId)`. Frontend lazy loading delays image requests, but once an image is
requested the browser still downloads the original asset. This is a bandwidth and LCP risk for image-heavy
boards.

## V1 Direction

- Generate derived image variants at upload time for supported raster images.
- Keep the original file and existing original URL behavior for backward compatibility.
- Add at least two variants:
  - `thumbnail`: list cards, avatars, compact previews.
  - `medium`: post body and detail views where the original dimensions are unnecessary.
- Persist variant metadata separately from the original file record or in a child table keyed by original `file_id`.
- Return variant URLs from read APIs after the storage shape is in place; do not change upload response shape in the first step.

## Deferred Decisions

- Exact dimensions and formats should be chosen after real traffic and image size data are available.
- On-demand resize with cache remains a fallback option, but upload-time generation is the preferred first implementation because the file domain already owns upload validation and storage.
