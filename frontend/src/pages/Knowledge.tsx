import { useEffect, useState, type FormEvent } from "react";
import { askKnowledge, listDiseases } from "../api";
import Badge from "../components/Badge";
import Section from "../components/Section";
import type { Disease, KnowledgeAnswer } from "../types";

interface KnowledgeProps {
  onError: (message: string) => void;
}

// Questions about rice diseases answered from the knowledge base: passages are quoted from the
// documents, each with the sources it is based on.
export default function Knowledge({ onError }: KnowledgeProps) {
  const [question, setQuestion] = useState("");
  const [diseaseCode, setDiseaseCode] = useState("");
  const [diseases, setDiseases] = useState<Disease[]>([]);
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null);
  const [asking, setAsking] = useState(false);

  useEffect(() => {
    listDiseases()
      .then(setDiseases)
      .catch((err) => onError(err.message));
  }, [onError]);

  const ask = (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim()) return;
    setAsking(true);
    askKnowledge(question.trim(), diseaseCode)
      .then(setAnswer)
      .catch((err) => onError(err.message))
      .finally(() => setAsking(false));
  };

  return (
    <Section
      step="📚"
      title="Tài liệu và hỏi đáp"
      subtitle="Hỏi về triệu chứng, điều kiện phát sinh và phòng ngừa bệnh lúa; mỗi đoạn trả lời kèm nguồn trích dẫn"
    >
      <form onSubmit={ask} className="harvest-box">
        <label className="field-label">
          Câu hỏi
          <textarea
            value={question}
            maxLength={1000}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ví dụ: Làm sao nhận biết lúa bị khô vằn?"
            required
          />
        </label>
        <label className="field-label">
          Giới hạn theo bệnh (không bắt buộc)
          <select value={diseaseCode} onChange={(e) => setDiseaseCode(e.target.value)}>
            <option value="">Tất cả bệnh</option>
            {diseases.map((disease) => (
              <option key={disease.code} value={disease.code}>
                {disease.name}
              </option>
            ))}
          </select>
        </label>
        <div className="form-actions">
          <button className="btn btn-primary" type="submit" disabled={asking || !question.trim()}>
            {asking ? "Đang tìm…" : "Hỏi"}
          </button>
        </div>
      </form>

      {answer && answer.passages.length === 0 && (
        <p className="empty-hint">
          Chưa tìm thấy nội dung đủ liên quan trong tài liệu. Hãy hỏi cụ thể hơn về một bệnh lúa.
        </p>
      )}

      {answer?.passages.map((passage, index) => (
        <article key={index} className="harvest-box">
          <strong>{passage.documentTitle}</strong>
          <p style={{ whiteSpace: "pre-line" }}>{passage.content}</p>
          <small>Độ liên quan {Math.round(passage.score * 100)}%</small>
          <div>
            <small>Nguồn:</small>
            <ul>
              {passage.sources.map((source) => (
                <li key={source.sourceId}>
                  {source.url ? (
                    <a href={source.url} target="_blank" rel="noreferrer">
                      {source.title}
                    </a>
                  ) : (
                    source.title
                  )}
                  {source.organization && <small> — {source.organization}</small>}{" "}
                  {source.license && <Badge tone="neutral">{source.license}</Badge>}{" "}
                  {source.reviewStatus === "IN_REVIEW" && <Badge tone="warning">Chưa chuyên gia duyệt</Badge>}
                </li>
              ))}
            </ul>
          </div>
        </article>
      ))}
    </Section>
  );
}
