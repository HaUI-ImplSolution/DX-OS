// SPDX-License-Identifier: Apache-2.0
import type { GeneratedAnswer } from "../types";

export default function GeneratedAnswerCard({ answer, diagnosis = false, scope }: {
  answer?: GeneratedAnswer | null;
  diagnosis?: boolean;
  scope?: "farm" | "lab";
}) {
  const generated = answer?.mode === "generated" && !answer.reason && answer.summary.trim();
  if (!generated) {
    if (diagnosis) return null;
    if (answer?.mode === "extractive" && !answer.reason) {
      return <p className="empty-hint" role="status">Câu trả lời nằm trong các tài liệu tham khảo bên dưới.</p>;
    }
    const notices = {
      insufficient_evidence: "Chưa có đủ tài liệu phù hợp để trả lời. Bạn vui lòng làm rõ câu hỏi.",
      out_of_scope: `Câu hỏi này nằm ngoài phạm vi hỗ trợ của ${scope === "farm" ? "DX-Farm" : "DX-Lab"}.`,
      requires_authority: scope === "farm"
        ? "Nội dung này cần kỹ sư xác nhận theo phác đồ được duyệt."
        : "Nội dung này cần người phụ trách xác nhận theo quy định.",
      generation_failed: "Hiện chưa thể trả lời câu hỏi này. Bạn vui lòng thử lại sau.",
      conflicting_output: "Hiện chưa thể trả lời câu hỏi này. Bạn vui lòng thử lại sau.",
    };
    return <p className="empty-hint" role="status">{notices[answer?.reason ?? "generation_failed"] ?? notices.generation_failed}</p>;
  }
  return (
    <section className="generated-answer" aria-label={diagnosis ? "Giải thích chẩn đoán" : "Câu trả lời"}>
      <header>
        <h4>{diagnosis ? "Giải thích kết quả nhận diện" : "Câu trả lời"}</h4>
        <span className="generated-answer-mode">AI tổng hợp từ tài liệu</span>
      </header>
      <p>{answer.summary}</p>
      {answer.steps.length > 0 && (
        <div>
          <strong>{diagnosis ? "Việc cần theo dõi" : "Thông tin liên quan / hướng dẫn"}</strong>
          <ul>{answer.steps.map((step, index) => <li key={index}>{step}</li>)}</ul>
        </div>
      )}
      {answer.note && <p className="generated-answer-note"><strong>{diagnosis ? "Giới hạn cần lưu ý" : "Lưu ý"}:</strong> {answer.note}</p>}
    </section>
  );
}
