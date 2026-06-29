export function isCandidateImageFile(file: File) {
  return file.type.toLowerCase().startsWith('image/')
    || /\.(jpe?g|png|gif|webp|svg)$/i.test(file.name)
}

export function hasCandidateImageFiles(files: File[]) {
  return files.some(isCandidateImageFile)
}
