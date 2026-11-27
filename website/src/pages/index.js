// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const content = {
  vi: {
    description: 'Tài liệu của DX-OS: lõi dựng ứng dụng từ một tệp mô hình khai báo',
    tagline: 'Lõi mã nguồn mở dựng bảng dữ liệu, vai, quyền, quy trình, API và giao diện từ một tệp mô hình khai báo.',
    start: 'Bắt đầu',
    source: 'Mã nguồn trên GitHub',
    cards: [
      {
        title: 'Bằng chứng và rà soát',
        text: 'Ma trận bằng chứng cho từng tính năng của lõi và bảng tự chấm PoF theo đề thi và phiếu chấm.',
        to: '/docs/evidence-matrix',
      },
      {
        title: 'Hỏi đáp và thành phần',
        text: 'Bộ trả lời câu hỏi hội đồng, và lý do chọn hay không chọn từng thành phần mã nguồn mở.',
        to: '/docs/council-qa',
      },
      {
        title: 'Mẫu tài liệu',
        text: 'Mẫu tổng quan, kế hoạch, danh sách kiểm, kế hoạch kiểm thử và đánh đổi theo từng phase.',
        to: '/docs/templates/',
      },
    ],
  },
  en: {
    description: 'DX-OS documentation: a core that builds applications from one declarative model file',
    tagline: 'An open-source core that builds data tables, roles, permissions, workflows, an API and screens from one declarative model file.',
    start: 'Get started',
    source: 'Source code on GitHub',
    cards: [
      {
        title: 'Quick start',
        text: 'From a clean machine to a working process, with a path without code and a path with code.',
        to: '/docs/tai-lieu/bat-dau-nhanh',
      },
      {
        title: 'Write a model file',
        text: 'Fields, constraints, permissions, workflows and printable documents, all declared in one file.',
        to: '/docs/tai-lieu/viet-mo-hinh',
      },
      {
        title: 'API',
        text: 'The common API, its paths and its errors, and the OpenAPI specification of each model.',
        to: '/docs/tai-lieu/api',
      },
    ],
  },
};

export default function Home() {
  const {i18n} = useDocusaurusContext();
  const text = content[i18n.currentLocale] ?? content.vi;
  return (
    <Layout title="DX-OS Docs" description={text.description}>
      <header className={styles.hero}>
        <div className="container">
          <Heading as="h1" className={styles.title}>DX-OS Docs</Heading>
          <p className={styles.tagline}>
            {text.tagline}
          </p>
          <div className={styles.buttons}>
            <Link className="button button--primary button--lg" to="/docs/intro">{text.start}</Link>
            <Link className="button button--secondary button--lg" href="https://github.com/HaUI-ImplSolution/DX-OS">
              {text.source}
            </Link>
          </div>
        </div>
      </header>
      <main className="container margin-vert--lg">
        <div className="row">
          {text.cards.map((card) => (
            <div key={card.title} className="col col--4 margin-bottom--md">
              <Link to={card.to} className={styles.card}>
                <Heading as="h3">{card.title}</Heading>
                <p>{card.text}</p>
              </Link>
            </div>
          ))}
        </div>
      </main>
    </Layout>
  );
}
