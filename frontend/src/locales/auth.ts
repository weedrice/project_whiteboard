import type { AuthMessages } from './types'

export const auth: AuthMessages = {
  createAccount: '계정 만들기',
  socialLogin: '소셜 로그인',
  createAccountTitle: '계정 만들기',
  signingIn: '로그인 중...',
  loginFailed: '로그인에 실패했습니다.',
  alreadyHaveAccount: '이미 계정이 있으신가요?',
  creatingAccount: '계정 생성 중...',
  signup: '회원가입',
  signupSuccess: '회원가입이 완료되었습니다. 로그인해주세요.',
  signupFailed: '회원가입에 실패했습니다.',
  findAccount: 'ID/비밀번호 찾기',
  findId: '아이디 찾기',
  findPassword: '비밀번호 찾기',
  sendCode: '인증 코드 발송',
  resendCode: '인증 코드 재발송',
  verifyCode: '인증하기',
  codeSent: '인증 코드가 이메일로 발송되었습니다.',
  codeExpired: '인증 시간이 만료되었습니다. 다시 발송해주세요.',
  codeVerified: '인증되었습니다.',
  codePlaceholder: '인증 코드 6자리',
  yourIdIs: '회원님의 아이디는 {id} 입니다.',
  resetPassword: '비밀번호 재설정',
  newPassword: '새 비밀번호',
  newPasswordConfirm: '새 비밀번호 확인',
  passwordResetSuccess: '비밀번호가 성공적으로 재설정되었습니다.',
  findIdPassword: 'ID/비밀번호를 잊으셨나요?',
  placeholders: {
    loginId: '아이디를 입력하세요',
    password: '비밀번호를 입력하세요',
    email: '회원가입 시 입력한 이메일을 입력하세요',
    displayName: '닉네임을 입력하세요',
    newEmail: '이메일을 입력하세요'
  },
  email: '이메일',
  login: '로그인',
  emailNotVerified: '이메일 인증이 필요합니다.',
  passwordMismatch: '비밀번호가 일치하지 않습니다.',
  verificationFailed: '인증에 실패했습니다.',
  validation: {
    passwordStrength: '비밀번호는 영문, 숫자를 포함하여 8자 이상이어야 합니다.',
    loginIdFormat: '아이디 형식이 올바르지 않습니다.',
    emailFormat: '이메일 형식이 올바르지 않습니다.'
  }
}
