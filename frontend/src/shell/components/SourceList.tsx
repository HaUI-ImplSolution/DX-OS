import Badge from "./Badge";
import type { KnowledgeSource } from "../types";

// The documents a passage is quoted from: the title opens the publisher's page, the PDF link
// opens the original file on the publisher's server.
export default function SourceList({ sources }: { sources: KnowledgeSource[] }) {
  return (
    <div>
      <small>Nguồn:</small>
      <ul>
        {sources.map((source) => (
          <li key={source.sourceId}>
            {source.url ? (
              <a href={source.url} target="_blank" rel="noreferrer">
                {source.title}
              </a>
            ) : (
              source.title
            )}
            {source.organization && <small> — {source.organization}</small>}{" "}
            {source.documentUrl && (
              <a href={source.documentUrl} target="_blank" rel="noreferrer">
                <small>[Bản PDF gốc]</small>
              </a>
            )}{" "}
            {source.license && <Badge tone="neutral">{source.license}</Badge>}{" "}
            {source.reviewStatus === "OFFICIAL" && <Badge tone="success">Văn bản chính thức</Badge>}
            {source.reviewStatus === "IN_REVIEW" && <Badge tone="warning">Chưa chuyên gia duyệt</Badge>}
          </li>
        ))}
      </ul>
    </div>
  );
}
