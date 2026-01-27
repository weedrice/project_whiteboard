import type { EmoticonMessages } from './types'

export const emoticon: EmoticonMessages = {
  purchase: {
    success: '노비콘을 구매했습니다!',
    failed: '구매에 실패했습니다.',
    confirm: '100포인트를 사용하여 이 노비콘을 구매하시겠습니까?',
    purchasing: '구매 중...',
    button: {
      loginRequired: '로그인 필요',
      purchased: '구매 완료',
      myEmoticon: '내가 등록한 노비콘',
      buyWithPrice: '{price}P로 구매하기',
    },
  },
  edit: {
    noPermission: '수정 권한이 없습니다.',
    updated: '노비콘이 수정되었습니다!',
    failed: '수정에 실패했습니다.',
  },
  register: {
    created: '노비콘이 등록되었습니다!',
    failed: '등록에 실패했습니다.',
  },
  validation: {
    imageOnly: '이미지 파일만 업로드 가능합니다.',
    imageLoadFailed: '이미지를 로드할 수 없습니다.',
    imageSizeExceeded: '이미지 크기가 500x500px를 초과합니다. ({width}x{height})',
    imageSizeExceededNamed: '{name}의 크기가 500x500px를 초과합니다. ({width}x{height})',
    notImage: '{name}은(는) 이미지 파일이 아닙니다.',
    loadFailedNamed: '{name}을(를) 로드할 수 없습니다.',
    maxImages: '최대 100개까지 업로드 가능합니다.',
    maxTags: '태그는 최대 10개까지 추가 가능합니다.',
  },
}
