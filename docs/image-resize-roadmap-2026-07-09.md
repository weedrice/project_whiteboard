# Image Resize Roadmap

## Context

The current file upload flow stores the original file and returns the original download URL through
`FileUrlResolver.resolve(fileId)`. Frontend lazy loading delays image requests, but once an image is
requested the browser still downloads the original asset. This is a bandwidth and LCP risk for image-heavy
boards.

## V1 Implemented

- Generate derived image variants at upload time for supported raster images.
- Keep the original file and existing original URL behavior for backward compatibility.
- Add two variants:
  - `thumbnail`: list cards, avatars, compact previews.
  - `medium`: post body and detail views where the original dimensions are unnecessary.
- Persist variant metadata in `file_variants`, keyed by original `file_id`.
- Keep upload response shape unchanged.
- Serve variants through `/api/v1/files/{fileId}/variants/{variantType}` with original-file fallback for legacy files.
- Delete generated variants with the original file deletion worker.

## Deferred Decisions

- Exact dimensions can be tuned after real traffic and image size data are available.
- On-demand resize with cache remains a fallback option if upload-time generation becomes too expensive.
- WebP/GIF variant generation is deferred until the runtime has explicit image writer support and animated-image policy.
