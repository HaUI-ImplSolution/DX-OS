import { useState, type FormEvent } from "react";
import { askKnowledge } from "../api";
import GeneratedAnswerCard from "../components/GeneratedAnswerCard";
import PassageText from "../components/PassageText";
import Section from "../components/Section";
import SourceList from "../components/SourceList";
import type { KnowledgeAnswer } from "../types";

export interface KnowledgeScope {
  label: string;
  allLabel: string;
  // Body field the selected value is sent in.
  param: string;
  options: { value: string; label: string }[];
}

interface KnowledgeProps {
  onError: (message: string) => void;
  title: string;
  subtitle: string;
  placeholder: string;
  emptyHint: string;
  scope?: KnowledgeScope;
}

// Generated wording and original evidence stay separate.
export default function Knowledge({
  onError,
  title,
  subtitle,
  placeholder,
  emptyHint,
  scope,
}: KnowledgeProps) {
  const [question, setQuestion] = useState("");
  const [scopeValue, setScopeValue] = useState("");
  const [answer, setAnswer] = useState<KnowledgeAnswer | null>(null);
  const [asking, setAsking] = useState(false);

  const ask = (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim()) return;
    setAnswer(null);
    setAsking(true);
    askKnowledge(question.trim(), scope ? { [scope.param]: scopeValue || null } : {})
      .then(setAnswer)
      .catch((err) => onError(err.message))
      .finally(() => setAsking(false));
  };

  return (
    <Section step="📚" title={title} subtitle={subtitle}>
      <form onSubmit={ask} className="harvest-box">
        <label className="field-label">
          Câu hỏi
          <textarea
            value={question}
            maxLength={1000}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder={placeholder}
            required
          />
        </label>
        {scope && (
          <label className="field-label">
            {scope.label}
            <select value={scopeValue} onChange={(e) => setScopeValue(e.target.value)}>
              <option value="">{scope.allLabel}</option>
              {scope.options.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        )}
        <div className="form-actions">
          <button className="btn btn-primary" type="submit" disabled={asking || !question.trim()}>
            {asking ? "Đang tìm…" : "Hỏi"}
          </button>
        </div>
      </form>

      {answer?.scopeStatus === "OUT_OF_SCOPE" ? (
        <p className="empty-hint">Câu hỏi nằm ngoài phạm vi kiến thức của ứng dụng này.</p>
      ) : answer?.scopeStatus === "IN_SCOPE_NO_EVIDENCE" && !answer.answer ? (
        <p className="empty-hint">{emptyHint}</p>
      ) : answer && <GeneratedAnswerCard answer={answer.answer} scope={scope?.param === "diseaseCode" ? "farm" : "lab"} />}

      {answer && answer.passages.length > 0 && (
        <details
          className="knowledge-references"
        >
          <summary>Tài liệu tham khảo ({answer.passages.length})</summary>
          {answer.passages.map((passage, index) => (
            <article key={index} className="harvest-box">
              <strong>{passage.documentTitle}</strong>
              <PassageText content={passage.content} />
              <small>Điểm tương đồng cosine: {passage.score.toFixed(2)}</small>
              {passage.corpusVersion && <small> · Corpus {passage.corpusVersion}</small>}
              <SourceList sources={passage.sources} />
            </article>
          ))}
        </details>
      )}
    </Section>
  );
}
