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
      subtitle="Hỏi về quy định sử dụng phòng máy, mượn thiết bị và cách tự xử lý sự cố thường gặp; mỗi đoạn trả lời kèm nguồn và giấy phép"
      placeholder="Ví dụ: Quy định khi sử dụng phòng máy là gì?"
      emptyHint="Chưa tìm thấy nội dung đủ liên quan trong tài liệu. Hãy hỏi cụ thể hơn, hoặc gửi một yêu cầu hỗ trợ."
    />
  );
}
