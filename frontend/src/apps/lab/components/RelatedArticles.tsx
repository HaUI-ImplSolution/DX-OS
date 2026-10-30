// SPDX-License-Identifier: Apache-2.0
import type { ReactNode } from "react";
import { SourceList } from "../../../shell";
import type { KnowledgeSource } from "../../../shell";
import type { RelatedPassage } from "../types";

const NUMBERED = /^\d+\.\s+/;

// One article's passages as text: numbered lines become a list, other lines stay paragraphs.
function ArticleBody({ passages, title }: { passages: string[]; title: string }) {
  const lines = passages
    .flatMap((content) => content.split("\n"))
    .map((line) => line.trim())
    .filter((line, index, all) => line && line !== title && all.indexOf(line) === index);

  const blocks: ReactNode[] = [];
  let items: string[] = [];
  const flush = () => {
    if (items.length === 0) return;
    blocks.push(
      <ol key={`list-${blocks.length}`} className="kb-steps">
        {items.map((item) => (
          <li key={item}>{item.replace(NUMBERED, "")}</li>
        ))}
      </ol>,
    );
    items = [];
  };
  lines.forEach((line) => {
    if (NUMBERED.test(line)) {
      items.push(line);
      return;
    }
    flush();
    blocks.push(
      <p key={`text-${blocks.length}`} className="kb-text">
        {line}
      </p>,
    );
  });
  flush();
  return <div className="kb-body">{blocks}</div>;
}

// Passages of the same article are shown once, under the article's title, with its sources.
export default function RelatedArticles({ passages }: { passages: RelatedPassage[] }) {
  const articles = new Map<string, string[]>();
  const sources = new Map<string, Map<string, KnowledgeSource>>();
  passages.forEach((passage) => {
    articles.set(passage.documentTitle, [...(articles.get(passage.documentTitle) ?? []), passage.content]);
    const known = sources.get(passage.documentTitle) ?? new Map<string, KnowledgeSource>();
    passage.sources.forEach((source) => known.set(source.sourceId, source));
    sources.set(passage.documentTitle, known);
  });

  return (
    <div className="related-articles">
      <strong>Có thể bạn tự xử lý được:</strong>
      {[...articles].map(([title, contents]) => (
        <article key={title} className="kb-card">
          <header className="kb-card-head">
            <h3>{title}</h3>
          </header>
          <ArticleBody passages={contents} title={title} />
          <SourceList sources={[...(sources.get(title)?.values() ?? [])]} />
        </article>
      ))}
    </div>
  );
}
