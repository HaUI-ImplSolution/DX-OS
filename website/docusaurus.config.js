// @ts-check
// SPDX-License-Identifier: Apache-2.0
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'DX-OS Docs',
  tagline: 'Tài liệu của DX-OS: lõi dựng ứng dụng từ một tệp mô hình khai báo',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: 'https://haui-implsolution.github.io',
  baseUrl: '/DX-OS/',
  organizationName: 'HaUI-ImplSolution',
  projectName: 'DX-OS',

  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',
  markdown: {
    format: 'detect',
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },

  i18n: {
    defaultLocale: 'vi',
    locales: ['vi'],
  },

  themes: ['@docusaurus/theme-mermaid'],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          routeBasePath: 'docs',
          editUrl: 'https://github.com/HaUI-ImplSolution/DX-OS/edit/Khuong/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      colorMode: {
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'DX-OS Docs',
        logo: {alt: 'DX-OS', src: 'img/logo.svg'},
        items: [
          {type: 'docSidebar', sidebarId: 'docs', position: 'left', label: 'Tài liệu'},
          {href: 'https://github.com/HaUI-ImplSolution/DX-OS', label: 'Mã nguồn', position: 'right'},
          {href: 'https://github.com/HaUI-ImplSolution/DX-OS/releases', label: 'Releases', position: 'right'},
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Dự án',
            items: [
              {label: 'Repository', href: 'https://github.com/HaUI-ImplSolution/DX-OS'},
              {label: 'Releases', href: 'https://github.com/HaUI-ImplSolution/DX-OS/releases'},
              {label: 'Issues', href: 'https://github.com/HaUI-ImplSolution/DX-OS/issues'},
            ],
          },
          {
            title: 'Trong repository',
            items: [
              {label: 'README', href: 'https://github.com/HaUI-ImplSolution/DX-OS#readme'},
              {label: 'ARCHITECTURE', href: 'https://github.com/HaUI-ImplSolution/DX-OS/blob/main/ARCHITECTURE.md'},
              {label: 'CHANGELOG', href: 'https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CHANGELOG.md'},
            ],
          },
        ],
        copyright: 'DX-OS, giấy phép Apache-2.0. Trường CNTT và Truyền thông, Đại học Công nghiệp Hà Nội.',
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['bash', 'java', 'yaml', 'sql', 'json'],
      },
    }),
};

export default config;
