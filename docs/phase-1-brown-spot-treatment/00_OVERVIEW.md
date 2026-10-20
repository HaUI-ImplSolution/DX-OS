# Phase 1: Mở phác đồ đốm nâu và bổ sung nguồn tiếng Việt

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã triển khai và qua kiểm thử dữ liệu/Java; chờ kiểm tay giao diện và khởi động toàn stack |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | DX-Farm; dữ liệu thuốc, seed và danh mục nguồn |
| Ước lượng | 1 ngày sau khi đủ hồ sơ duyệt; chưa gồm thời gian chờ thẩm định |
| Người phụ trách | Người triển khai; chủ dự án yêu cầu gán trạng thái duyệt ngày 20/10/2026 |
| Bắt đầu, dự kiến xong | 20/10/2026; hoàn thành theo kết quả kiểm thử ghi trong checklist |
| Phụ thuộc | Nguồn ADC, đăng ký hiện hành và xác nhận phác đồ đốm nâu |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu người dùng ngày 20/10/2026 | Lập kế hoạch mở đốm nâu, bổ sung link tiếng Việt cho bệnh còn lại | Quan trọng | Toàn bộ |
| `docs/BACKLOG.md`, mục 6 | Rule đốm nâu với Help 400SC đang chờ duyệt | Quan trọng | Chỉ rule đốm nâu |
| [01_PLAN.md, WP1-01 và WP1-03](01_PLAN.md) | Nguồn gốc, thông số và giới hạn sử dụng theo bệnh | Tham khảo | Chỉ các nguồn được chọn trong kế hoạch |

## 2. Lý do

Đốm nâu đã có ứng viên Help 400SC và hướng dẫn nhà sản xuất đủ liều, lượng nước, thời điểm và PHI nhưng chưa đủ điều kiện seed.
Người dùng cần mở đúng phác đồ này qua luồng hiện có và có thêm đường dẫn tiếng Việt để kiểm tra căn cứ.

## 3. Tổng quan sửa đổi

Hoàn thiện bằng chứng và ghi nhận quyết định duyệt trước khi đổi eligibility.
Cho phép seed Help 400SC và rule đốm nâu bằng generator hiện có.
Bổ sung metadata đường dẫn tiếng Việt phù hợp vào danh mục nguồn Farm.
Cập nhật các kiểm thử đang giả định chỉ có hai rule và tài liệu ghi trạng thái cũ.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api`, `dx-core` | Không | Giữ interface và phân quyền |
| `dx-host` | Chỉ kiểm thử | Cập nhật số rule trong test seed, thêm luồng đốm nâu vào test tích hợp có sẵn |
| `app-farm` | Chỉ resource | `farm/data.sql`, `knowledge-sources.json`; không sửa Java |
| `app-lab` | Không | Giữ dữ liệu và luồng Lab |
| Frontend, Mobile | Không | Dùng phác đồ và nguồn theo hợp đồng hiện có |
| AI | Không | Giữ model, ngưỡng, truy xuất và contract |
| Data / Seed / Migration | Có / Có / Không | Sửa JSON nguồn và sinh lại SQL; không thêm bảng hoặc cột |
| Infra / Deploy / CI | Không | Giữ cách chạy hiện tại |
| Docs | Có | Chỉ nguồn, kế hoạch và những câu thay đổi trạng thái sau triển khai |

## 4. Phạm vi

Trong phạm vi: một thuốc ứng viên `P-RICE-002`, một rule `TR-RICE-003`, nguồn tiếng Việt liên quan bệnh lúa và kiểm tra các luồng bị tác động.
Nguồn mới chỉ bổ sung đường dẫn và metadata; không tự nạp toàn văn vào RAG hoặc đổi trạng thái chuyên môn của bệnh khác.
Ngoài phạm vi: sửa hai rule Newtec, mở thuốc cho Tungro/than lá, duyệt policy quản lý, viết lại corpus, sửa retrieval/prompt, thêm hiển thị lượng nước/thời điểm, nâng model, DX-Lab, Docker, env và CI.
Giả định cần xác minh trước triển khai: hồ sơ thuốc còn phù hợp đăng ký hiện hành và người duyệt xác nhận đúng bệnh/đối tượng.
Ranh giới Core/App: toàn bộ thay đổi thuộc dữ liệu Farm; không thêm interface hoặc dependency.

## 5. Ưu điểm và hạn chế

Tận dụng lookup, kỹ sư duyệt, khai phun và khóa PHI hiện có.
Đủ bốn thông số trong hồ sơ không đồng nghĩa cả bốn đã có cột riêng trên giao diện.
Lượng nước và thời điểm vẫn nằm trong `TreatmentRule.notes`; cải tiến giao diện không thuộc phase này.
Rủi ro phê duyệt và rollback được ghi ở [04_TRADEOFFS_RISKS.md](04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Có căn cứ cho Help 400SC/đốm nâu/lúa, bốn thông số và ghi nhận đúng yêu cầu gán `EXPERT_APPROVED` của chủ dự án; không bổ sung danh tính hoặc văn bản chuyên gia không có thật.
- Sau khi đủ điều kiện: có 2 thuốc, 3 treatment rule active; hai rule Newtec và 6 lớp bệnh giữ nguyên.
- Chẩn đoán đốm nâu mới lấy rule từ Backend; vẫn cần kỹ sư duyệt trước bước áp dụng.
- Khai phun đốm nâu đúng liều lưu PHI 14 ngày và khóa thu hoạch theo cách tính hiện có.
- Có link ADC trong danh mục nguồn Farm; thêm link tiếng Việt đã xác minh cho đạo ôn, khô vằn và Tungro theo phạm vi nguồn.
- Than lá chưa có nguồn phù hợp thì ghi rõ khoảng trống, không tạo thuốc hoặc gán nguồn bệnh khác.
- Không đổi API, schema DB, code AI/web/mobile, hạ tầng hay cách chạy theo README.
- Checklist và kiểm thử áp dụng có kết quả thực; phần chưa chạy ghi rõ, không đóng phase khi thiếu bằng chứng.

## 7. Phụ thuộc và thứ tự

WP1-01: hồ sơ và quyết định duyệt → WP1-02: dữ liệu và seed → WP1-04: kiểm tra, cập nhật trạng thái.
WP1-03: nguồn tiếng Việt có thể chuẩn bị độc lập; metadata vẫn ghi trạng thái thật.
Chưa có quyết định duyệt thì WP1-02 chưa được đổi eligibility hoặc xuất SQL kích hoạt thuốc.

## 8. Ảnh hưởng đến thi

Bổ sung tình huống Farm từ đốm nâu đến khai phun và khóa PHI có căn cứ.
Không thay đổi rubric hoặc nhận thêm chức năng ngoài yêu cầu.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Ai xác nhận phác đồ và hồ sơ duyệt lưu ở đâu? | Ghi đúng người yêu cầu và căn cứ thực tế | 20/10/2026 | Chủ dự án yêu cầu gán `EXPERT_APPROVED` và mở rule; ghi `authorized_by=project_owner`, ngày và căn cứ trong `seed_authorization`, chưa kèm văn bản chuyên môn riêng |
| Q2 | Phạm vi bổ sung nguồn là gì? | Chỉ link và metadata; không viết lại RAG hoặc mở thuốc bệnh khác | Đã xác định theo yêu cầu | Kế hoạch giữ phạm vi này |

## 10. Tài liệu cần cập nhật khi xong

- [x] `data/README.md`, `docs/COUNCIL_QA.md`: sửa đúng câu về số rule và trạng thái đốm nâu.
- [x] `docs/BACKLOG.md`, `CHANGELOG.md`: chỉ ghi kết quả thực; giữ toàn bộ log cũ.
- [x] Đối chiếu danh mục nguồn Farm với các đường dẫn và phạm vi ghi tại `01_PLAN.md`, WP1-03.
- Swagger, kiến trúc, license dependency và env không có thay đổi dự kiến.
