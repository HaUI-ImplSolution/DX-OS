// SPDX-License-Identifier: Apache-2.0
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const cards = [
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
];

export default function Home() {
  return (
    <Layout title="DX-OS Docs" description="Tài liệu của DX-OS: lõi dựng ứng dụng từ một tệp mô hình khai báo">
      <header className={styles.hero}>
        <div className="container">
          <Heading as="h1" className={styles.title}>DX-OS Docs</Heading>
          <p className={styles.tagline}>
            Lõi mã nguồn mở dựng bảng dữ liệu, vai, quyền, quy trình, API và giao diện từ một tệp mô hình khai báo.
          </p>
          <div className={styles.buttons}>
            <Link className="button button--primary button--lg" to="/docs/intro">Bắt đầu</Link>
            <Link className="button button--secondary button--lg" href="https://github.com/HaUI-ImplSolution/DX-OS">
              Mã nguồn trên GitHub
            </Link>
          </div>
        </div>
      </header>
      <main className="container margin-vert--lg">
        <div className="row">
          {cards.map((card) => (
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
