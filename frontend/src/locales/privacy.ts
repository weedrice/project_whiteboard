import type { PrivacyPolicyMessages } from './types'

export const privacyPolicy: PrivacyPolicyMessages = {
  title: '개인정보 처리방침',
  lastRevised: '최종 수정일',
  sections: [
    {
      title: '제1조 (목적)',
      paragraphs: [
        'Noviis(이하 "회사")는 이용자의 개인정보를 중요시하며, 「개인정보 보호법」 등 관련 법령을 준수하고 있습니다. 본 개인정보 처리방침은 회사가 제공하는 서비스 이용 시 수집되는 개인정보의 항목, 이용 목적, 보유 및 이용 기간, 파기 절차 및 방법 등에 관한 사항을 안내합니다.',
      ],
    },
    {
      title: '제2조 (수집하는 개인정보의 항목)',
      paragraphs: ['회사는 서비스 제공을 위해 다음과 같은 개인정보를 수집합니다.'],
      groups: [
        {
          title: '회원가입 시',
          items: ['필수항목: 이메일 주소, 비밀번호, 닉네임', '선택항목: 프로필 사진'],
        },
        {
          title: '소셜 로그인 이용 시',
          items: [
            'Google: 이메일 주소, 프로필 정보(이름, 프로필 사진)',
            'Discord: 사용자 ID, 이메일 주소, 프로필 정보',
            'GitHub: 사용자 ID, 이메일 주소, 프로필 정보',
          ],
        },
        {
          title: '서비스 이용 과정에서 자동 수집되는 정보',
          items: ['IP 주소, 쿠키, 방문 일시, 서비스 이용 기록'],
        },
      ],
    },
    {
      title: '제3조 (개인정보의 수집 및 이용 목적)',
      paragraphs: ['회사는 수집한 개인정보를 다음의 목적으로 이용합니다.'],
      groups: [
        {
          title: '서비스 제공',
          items: ['회원 식별 및 인증', '스페이스 서비스 제공', '커뮤니티 활동 지원'],
        },
        {
          title: '회원 관리',
          items: ['본인 확인 및 개인 식별', '부정 이용 방지', '서비스 부정이용 기록의 확인 및 보관'],
        },
        {
          title: '서비스 개선',
          items: ['서비스 이용 통계 분석', '신규 서비스 개발 및 맞춤 서비스 제공'],
        },
      ],
    },
    {
      title: '제4조 (개인정보의 보유 및 이용 기간)',
      paragraphs: [
        '회사는 이용자의 개인정보를 회원 가입일로부터 서비스를 제공하는 기간 동안에 한하여 보유 및 이용합니다. 회원 탈퇴 시 지체없이 파기하는 것을 원칙으로 하나, 다음의 경우에는 해당 기간 동안 보관합니다.',
      ],
      groups: [
        {
          title: '관계 법령에 의한 정보 보유',
          items: [
            '계약 또는 청약철회 등에 관한 기록: 5년 (전자상거래법)',
            '대금결제 및 재화 등의 공급에 관한 기록: 5년 (전자상거래법)',
            '소비자의 불만 또는 분쟁처리에 관한 기록: 3년 (전자상거래법)',
            '서비스 이용 기록: 3개월 (통신비밀보호법)',
          ],
        },
        { title: '부정 이용 방지', items: ['부정 이용 기록: 1년'] },
      ],
    },
    {
      title: '제5조 (개인정보의 파기)',
      paragraphs: [
        '회사는 개인정보 보유 기간의 경과, 처리 목적 달성 등 개인정보가 불필요하게 되었을 때에는 지체없이 해당 개인정보를 파기합니다. 파기 절차 및 방법은 다음과 같습니다.',
      ],
      groups: [
        {
          title: '파기 절차',
          items: ['이용자가 입력한 정보는 목적 달성 후 별도의 DB로 옮겨져 내부 방침 및 기타 관련 법령에 따라 일정 기간 저장된 후 파기됩니다.'],
        },
        {
          title: '파기 방법',
          items: ['전자적 파일 형태: 복구 및 재생이 불가능한 방법으로 영구 삭제', '종이 문서: 분쇄기로 분쇄하거나 소각'],
        },
      ],
    },
    {
      title: '제6조 (이용자의 권리)',
      paragraphs: [
        '이용자는 언제든지 개인정보 열람, 정정, 삭제 및 처리 정지를 요구할 수 있습니다.',
        '권리 행사는 서비스 내 설정 메뉴를 통해 직접 하실 수 있으며, 개인정보 보호 책임자에게 서면, 전화, 이메일 등으로 연락하셔도 지체없이 조치하겠습니다.',
      ],
      groups: [
        { title: '행사할 수 있는 권리', items: ['개인정보 열람 요구', '개인정보 정정 요구', '개인정보 삭제 요구', '개인정보 처리 정지 요구'] },
      ],
    },
    {
      title: '제7조 (개인정보의 안전성 확보 조치)',
      paragraphs: ['회사는 개인정보의 안전성 확보를 위해 다음과 같은 조치를 취하고 있습니다.'],
      groups: [
        {
          title: '안전성 확보 조치',
          items: ['비밀번호 암호화 저장 및 관리', '해킹 등에 대비한 기술적 대책', '개인정보 취급 직원의 최소화 및 교육', '개인정보 보호 전담 조직의 운영'],
        },
      ],
    },
    {
      title: '제8조 (개인정보 보호책임자)',
      paragraphs: [
        '회사는 개인정보 처리에 관한 업무를 총괄해서 책임지고, 개인정보 처리와 관련한 이용자의 불만처리 및 피해구제를 위하여 개인정보 보호책임자를 지정하고 있습니다.',
        '개인정보 보호 관련 문의사항이 있으시면 서비스의 문의하기 기능을 통해 연락해 주시기 바랍니다.',
      ],
    },
    {
      title: '제9조 (개인정보 처리방침의 변경)',
      paragraphs: [
        '본 개인정보 처리방침은 법령, 정책 또는 보안기술의 변경에 따라 내용이 추가, 삭제 또는 수정될 수 있습니다. 변경 시에는 시행일의 최소 7일 전부터 서비스 내 공지사항을 통해 고지합니다.',
      ],
    },
  ],
}

export const privacyPolicyEn: PrivacyPolicyMessages = {
  title: 'Privacy Policy',
  lastRevised: 'Last revised',
  sections: [
    {
      title: 'Article 1 (Purpose)',
      paragraphs: [
        'Noviis (the "Company") values user privacy and complies with applicable privacy laws. This Privacy Policy explains what personal information is collected when using the service, why it is used, how long it is retained, and how it is deleted.',
      ],
    },
    {
      title: 'Article 2 (Personal Information We Collect)',
      paragraphs: ['The Company collects the following information to provide the service.'],
      groups: [
        { title: 'When you register', items: ['Required: email address, password, and display name', 'Optional: profile photo'] },
        {
          title: 'When you use social login',
          items: [
            'Google: email address and profile information (name and profile photo)',
            'Discord: user ID, email address, and profile information',
            'GitHub: user ID, email address, and profile information',
          ],
        },
        { title: 'Automatically collected while using the service', items: ['IP address, cookies, visit times, and service usage records'] },
      ],
    },
    {
      title: 'Article 3 (How We Use Personal Information)',
      paragraphs: ['The Company uses collected personal information for the following purposes.'],
      groups: [
        { title: 'Providing the service', items: ['Member identification and authentication', 'Providing space features', 'Supporting community activity'] },
        { title: 'Managing members', items: ['Identity verification', 'Preventing misuse', 'Reviewing and retaining misuse records'] },
        { title: 'Improving the service', items: ['Analyzing service usage statistics', 'Developing new and personalized services'] },
      ],
    },
    {
      title: 'Article 4 (Retention Period)',
      paragraphs: [
        'The Company retains and uses personal information while providing the service. Information is generally deleted promptly when a member closes their account, except when retention is required as described below.',
      ],
      groups: [
        {
          title: 'Records retained under applicable law',
          items: [
            'Contract and withdrawal records: 5 years',
            'Payment and supply records: 5 years',
            'Consumer complaint and dispute records: 3 years',
            'Service usage records: 3 months',
          ],
        },
        { title: 'Preventing misuse', items: ['Misuse records: 1 year'] },
      ],
    },
    {
      title: 'Article 5 (Deletion of Personal Information)',
      paragraphs: ['The Company promptly deletes personal information when it is no longer needed because the retention period has expired or the processing purpose has been fulfilled.'],
      groups: [
        { title: 'Deletion procedure', items: ['Information is moved to a separate database after its purpose is fulfilled, retained for any required period, and then deleted.'] },
        { title: 'Deletion method', items: ['Electronic files are permanently deleted using methods that prevent recovery.', 'Paper documents are shredded or incinerated.'] },
      ],
    },
    {
      title: 'Article 6 (Your Rights)',
      paragraphs: [
        'You may request access to, correction of, deletion of, or suspension of processing of your personal information at any time.',
        'You can exercise these rights through service settings or by contacting the privacy officer in writing, by telephone, or by email. The Company will respond without undue delay.',
      ],
      groups: [
        { title: 'Available requests', items: ['Access personal information', 'Correct personal information', 'Delete personal information', 'Suspend processing'] },
      ],
    },
    {
      title: 'Article 7 (Security Measures)',
      paragraphs: ['The Company takes the following measures to protect personal information.'],
      groups: [
        { title: 'Security measures', items: ['Encrypted password storage and management', 'Technical safeguards against unauthorized access', 'Minimized access and staff training', 'Operation of a dedicated privacy function'] },
      ],
    },
    {
      title: 'Article 8 (Privacy Officer)',
      paragraphs: [
        'The Company designates a privacy officer to oversee personal information processing and handle privacy complaints and remedies.',
        'For privacy-related questions, please contact us through the service inquiry feature.',
      ],
    },
    {
      title: 'Article 9 (Changes to This Policy)',
      paragraphs: ['This Privacy Policy may be added to, removed from, or revised due to changes in law, policy, or security technology. Changes will be announced through the service at least seven days before taking effect.'],
    },
  ],
}
