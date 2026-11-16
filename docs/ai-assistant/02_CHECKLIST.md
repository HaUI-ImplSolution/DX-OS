# Checklist trợ lý nghiệp vụ động

Các mục CP theo dõi trợ lý nghiệp vụ; chỉ tick phần đã kiểm đủ theo [test plan](./03_TEST_PLAN.md).
Chưa nghiệm thu cuối hoặc chọn model phát hành.
Kiểm tích hợp phải chạy lại sau khi đồng bộ nhánh cha; dấu tick kỹ thuật không thay nghiệm thu bản phát hành.

## Quyết định và review

- [x] Người dùng chốt trợ lý nghiệp vụ nhiều domain, self-host và có xác nhận trước ghi.
- [x] Đã xác minh worktree/nhánh và fetch nhánh cha `Khuong/main` trước khi lập kế hoạch.
- [ ] Tech lead review schema nhiệm vụ, trải nghiệm trên hồ sơ, ranh giới dịch vụ và phần #210 chưa thực hiện.
- [ ] Chốt model/artifact, nguồn mở, chất lượng tiếng Việt và ngân sách từ khảo sát nhiệm vụ mới.

## P0–P1: Hợp đồng và mô hình

- [ ] CP-01 Audit runtime/model/artifact/bundled và nguồn dữ liệu trước manifest; không suy OSAID từ self-host hoặc Apache-2.0.
- [ ] CP-02 Chốt khai báo nhiệm vụ trung tính trong YAML, parser/schema, Console, diff/plan và model upgrade.
- [ ] CP-03 Metadata/nhiệm vụ theo quyền người gọi; mô hình không có AI vẫn hoạt động.
- [x] CP-04 Đổi/bỏ trường, lựa chọn, nhiệm vụ hoặc nguồn làm proposal cũ mất hiệu lực.
- [x] CP-05 Chốt spec rồi cập nhật OpenAPI/schema và code cùng thay đổi; giữ envelope/status/errorCode/i18n.
- [x] CP-06 Sửa trường có `version` và `modelVersion` được Core kiểm dưới khóa/giao dịch; client cũ giữ tương thích.

## P2–P3: Provider và giao diện

- [x] CP-07 Một request suy luận cho một nhiệm vụ; schema đầu ra, lựa chọn và nguồn được server kiểm, lỗi không tự chữa thành lệnh ghi.
- [ ] CP-08 Nguồn có provenance/license/revision; thiếu căn cứ thì từ chối, trích dẫn thật và liên quan.
- [ ] CP-09 Không đọc chéo người/mô hình hoặc nhận token/URL/nguồn từ chỉ dẫn trong hồ sơ; không thêm ACL theo trường.
- [x] CP-10 Proposal có chủ/snapshot/TTL/revision; confirm ngoài LLM, sửa nội dung phải xác nhận snapshot mới.
- [x] CP-11 Hai confirm chỉ một claim; mất quyền/đổi trạng thái/phiên bản cũ bị Core chặn; timeout sau ghi là uncertain.
- [ ] CP-12 UI dùng metadata trên màn hồ sơ, có giá trị hiện tại/đề xuất/nguồn và phân biệt chưa lưu/đã lưu.
- [ ] CP-13 Model off, busy, timeout, hủy hoặc output lỗi không cản thao tác nghiệp vụ và giải phóng slot.

## P4–P5: Triển khai và state

- [ ] CP-14 Snapshot nhất quán và thử restore trước migration; lỗi nâng cấp không tạo lại key.
- [x] CP-15 Giữ owner/SSO/lịch sử/key khi migration; trạng thái chưa rõ không replay.
- [x] CP-16 Route ngoài hợp đồng không được tạo credential, gọi inference hoặc ghi Core.
- [x] CP-17 Dọn Dockerfile/Compose/env/network/APISIX/CLI/backup và CI đúng consumer; không ảnh hưởng Core hoặc dữ liệu cũ.
- [x] CP-18 Kiểm owner, claim, encryption và timeout bằng bộ kiểm hiện có; không thêm test file/framework.
- [ ] CP-19 Đồng bộ license/catalog/NOTICE/DEPENDENCIES/SBOM/REUSE và docs VI/EN; không xóa mục license còn dùng chung.
- [ ] CP-20 Cài mới/nâng cấp nhiều binding/rollback được kiểm; thiếu model không chặn Compose/Core, không tự tải artifact hoặc gọi cloud.

## P6: Tính động, tài nguyên và nghiệm thu

- [x] CP-21 Hai domain thật dùng cùng provider/UI, thay YAML không sửa mã; hồi quy ba họ workflow hiện có.
- [ ] CP-22 Đo đúng workload mới trên host thực, tách Core/provider/model và cold/warm/busy/cancel; chưa công bố giảm RAM hoặc SLA khi chưa đo.
- [ ] CP-23 Kiểm build/compliance/doc refs/secret/diff đúng phần thay đổi; không để reference vận hành treo hoặc dependency vô dụng.
- [ ] CP-24 Người dùng chạy kịch bản demo, tech lead review giới hạn và giấy phép; chưa đánh dấu hoàn tất toàn bộ #210.

## Cải thiện chất lượng đa mẫu

- [x] QI-01 Giữ đối chứng 30 ca và chốt thêm ca chưa dùng để chỉnh prompt.
- [ ] QI-02 Quyết định đủ căn cứ trước giá trị; đo từ chối đúng và không làm mất các ca rõ nghĩa.
- [x] QI-03 Quan hệ số và đối chiếu danh mục lấy từ dữ liệu/metadata, không có tên ngành trong mã dùng chung.
- [ ] QI-04 Nội dung ngắn hoàn chỉnh; lỗi kiểu, nguồn và cấu trúc không thành đề xuất ghi được.
- [x] QI-05 Giữ một lượt suy luận, một slot, thời hạn và hủy; không lặp suy luận vô hạn hoặc tự ghi.
- [ ] QI-06 Kiểm hồi quy và đối chiếu trước/sau với Core/model/giao diện thật; công bố giới hạn còn lại.

QI-02 và QI-04 còn mở ở phần chất lượng ngữ nghĩa và văn phong; các lỗi model còn lại được theo dõi tại [BACKLOG](../dynamic-core/BACKLOG.md).
Bản hiện tại giữ một lượt suy luận; chưa nghiệm thu khả năng từ chối ổn định trên các cách diễn đạt mới.
