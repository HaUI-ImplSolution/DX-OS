// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Knowledge as KnowledgePage } from "../../../shell";
import { listDiseases } from "../api";
import type { Disease } from "../types";

interface KnowledgeProps {
  onError: (message: string) => void;
}

// Rice disease questions; the question can be narrowed to one disease.
export default function Knowledge({ onError }: KnowledgeProps) {
  const [diseases, setDiseases] = useState<Disease[]>([]);

  useEffect(() => {
    listDiseases()
      .then(setDiseases)
      .catch((err) => onError(err.message));
  }, [onError]);

  return (
    <KnowledgePage
      onError={onError}
      title="Tài liệu và hỏi đáp"
      subtitle="Hỏi về triệu chứng, điều kiện phát sinh và phòng ngừa bệnh lúa; mỗi đoạn trả lời kèm nguồn trích dẫn"
      placeholder="Ví dụ: Làm sao nhận biết lúa bị khô vằn?"
      emptyHint="Chưa tìm thấy nội dung đủ liên quan trong tài liệu. Hãy hỏi cụ thể hơn về một bệnh lúa."
      scope={{
        label: "Giới hạn theo bệnh (không bắt buộc)",
        allLabel: "Tất cả bệnh",
        param: "diseaseCode",
        options: diseases.map((disease) => ({ value: disease.code, label: disease.name })),
      }}
    />
  );
}
