import type { PrivacyPolicyMessages } from './types'

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
