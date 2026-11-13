// @ts-check
// SPDX-License-Identifier: Apache-2.0

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'intro',
    'hinh-anh',
    {
      type: 'category',
      label: 'Hướng dẫn sử dụng',
      collapsed: false,
      items: ['huong-dan/nguoi-nop', 'huong-dan/nguoi-xu-ly', 'huong-dan/nguoi-trien-khai'],
    },
    {
      type: 'category',
      label: 'Hồ sơ dự thi',
      collapsed: false,
      items: ['evidence-matrix', 'pof-audit', 'council-qa', 'dependencies'],
    },
    {
      type: 'category',
      label: 'Mẫu tài liệu theo phase',
      collapsed: true,
      items: [
        'templates/index',
        'templates/overview',
        'templates/plan',
        'templates/checklist',
        'templates/test-plan',
        'templates/tradeoffs-risks',
      ],
    },
  ],
};

export default sidebars;
