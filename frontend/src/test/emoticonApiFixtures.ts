export const emoticonApiData = <T>(data: T) => ({
  data: {
    data,
  },
})

export const emoticonApiSuccess = () => ({
  data: {
    success: true,
  },
})
