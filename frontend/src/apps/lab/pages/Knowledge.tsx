import { Knowledge as KnowledgePage } from "../../../shell";

interface KnowledgeProps {
  onError: (message: string) => void;
}

// Questions on lab regulations and guides, answered from the knowledge base with quoted sources.
export default function Knowledge({ onError }: KnowledgeProps) {
  return (
    <KnowledgePage
      onError={onError}
      title="Quy chế và hướng dẫn"
      subtitle="Hỏi về nội quy thư viện, Wi-Fi, email, máy chiếu, an toàn thông tin, sửa chữa thiết bị và quy định thi; mỗi đoạn trích từ văn bản của trường, bấm nguồn để mở trang gốc hoặc bản PDF"
      placeholder="Ví dụ: Quy định sử dụng máy tính trong thư viện là gì?"
      emptyHint="Chưa tìm thấy nội dung đủ liên quan trong tài liệu. Hãy hỏi cụ thể hơn, hoặc gửi một yêu cầu hỗ trợ."
    />
  );
}
