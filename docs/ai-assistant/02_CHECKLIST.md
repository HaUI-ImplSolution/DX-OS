# Checklist trợ lý nghiệp vụ động

Các mục CP theo dõi trợ lý nghiệp vụ; chỉ tick phần đã kiểm đủ theo [test plan](./03_TEST_PLAN.md).
Chưa nghiệm thu cuối hoặc chọn model phát hành.
Mục có hậu tố a/b tách phần đã kiểm và phần còn thiếu; chỉ hoàn tất tiêu chí CP tương ứng khi mọi phần đều đạt.
Dấu tick kỹ thuật không thay nghiệm thu bản phát hành.

## Quyết định và review

- [x] Người dùng chốt trợ lý nghiệp vụ nhiều domain, self-host và có xác nhận trước ghi.
- [x] Đã xác minh worktree/nhánh và fetch nhánh cha `Khuong/main` trước khi lập kế hoạch.
- [ ] Tech lead review schema nhiệm vụ, trải nghiệm trên hồ sơ, ranh giới dịch vụ và phần #210 chưa thực hiện.
- [ ] Chốt model/artifact, nguồn mở, chất lượng tiếng Việt và ngân sách từ khảo sát nhiệm vụ mới.

## P0–P1: Hợp đồng và mô hình

- [x] CP-01a Kiểm giấy phép và hồ sơ dependency mã nguồn, catalog, NOTICE, SBOM và REUSE.
- [ ] CP-01b Chốt model/artifact mặc định, provenance và cam kết OSAID trước phân phối; chưa đủ audit model/dữ liệu.
- [x] CP-02a Triển khai khai báo nhiệm vụ trong YAML, parser/schema, Console, diff/plan và model upgrade; kiểm parser, Core và build Console.
- [ ] CP-02b Kiểm đủ vòng tạo/sửa nhiệm vụ bằng form Console, load lại và apply; chưa có nghiệm thu vòng form này.
- [x] CP-03 Metadata/nhiệm vụ theo quyền người gọi; mô hình không có AI vẫn hoạt động.
- [x] CP-04 Đổi/bỏ trường, lựa chọn, nhiệm vụ hoặc nguồn làm proposal cũ mất hiệu lực.
- [x] CP-05 Chốt spec rồi cập nhật OpenAPI/schema và code cùng thay đổi; giữ envelope/status/errorCode/i18n.
- [x] CP-06 Sửa trường có `version` và `modelVersion` được Core kiểm dưới khóa/giao dịch; client cũ giữ tương thích.

## P2–P3: Provider và giao diện

- [x] CP-07 Một request suy luận cho một nhiệm vụ; schema đầu ra, lựa chọn và nguồn được server kiểm, lỗi không tự chữa thành lệnh ghi.
- [x] CP-08a Catalog nguồn có ID/license/revision, nạp bất biến và kiểm mã nguồn do model trả về.
- [ ] CP-08b Nguồn phải hỗ trợ kết luận và model từ chối đúng khi thiếu căn cứ; còn lỗi ngữ nghĩa.
- [x] CP-09a Kiểm JWT/owner/quyền đọc, giới hạn dữ liệu gửi model và endpoint cố định; model không nhận token Core.
- [ ] CP-09b Kiểm đầy đủ injection ngữ nghĩa trong hồ sơ/nguồn trên model thật; chưa có bộ nghiệm thu đủ.
- [x] CP-10 Proposal có chủ/snapshot/TTL/revision; confirm ngoài LLM, sửa nội dung phải xác nhận snapshot mới.
- [x] CP-11 Hai confirm chỉ một claim; mất quyền/đổi trạng thái/phiên bản cũ bị Core chặn; timeout sau ghi là uncertain.
- [x] CP-12a UI dùng metadata, hiển thị giá trị/nguồn/lưu ý, sửa/hủy/xác nhận, no-op và lịch sử sau lưu đã được kiểm.
- [ ] CP-12b Kiểm đồng thời hai tab và bản nháp đang sửa; chưa có bằng chứng đủ cho toàn tình huống.
- [x] CP-13a Kiểm busy, timeout/hủy, ngân sách input/output và giải phóng slot bằng transport; không có fallback cloud.
- [ ] CP-13b Kiểm trọn luồng UI khi model tắt hoặc phiên Core hết hạn; chưa đủ kiểm tích hợp các tình huống này.

## P4–P5: Triển khai và state

- [x] CP-14 Snapshot nhất quán và thử restore trước migration; lỗi nâng cấp không tạo lại key.
- [x] CP-15 Giữ owner/SSO/lịch sử/key khi migration; trạng thái chưa rõ không replay.
- [x] CP-16 Route ngoài hợp đồng không được tạo credential, gọi inference hoặc ghi Core.
- [x] CP-17 Dọn Dockerfile/Compose/env/network/APISIX/CLI/backup và CI đúng consumer; không ảnh hưởng Core hoặc dữ liệu cũ.
- [x] CP-18 Kiểm owner, claim, encryption và timeout bằng bộ kiểm hiện có; không thêm test file/framework.
- [x] CP-19 Đồng bộ license/catalog/NOTICE/DEPENDENCIES/SBOM/REUSE và docs VI/EN; không xóa mục license còn dùng chung.
- [x] CP-20a Cài từ release archive, khởi động Core và backup/restore được CI kiểm; không tải trọng số trong request/boot.
- [ ] CP-20b Kiểm nâng cấp nhiều chủ và rollback toàn bản cài với state trợ lý; chưa đủ nghiệm thu.

## P6: Tính động, tài nguyên và nghiệm thu

- [x] CP-21 Hai domain thật dùng cùng provider/UI, thay YAML không sửa mã; hồi quy ba họ workflow hiện có.
- [ ] CP-22 Đo đúng workload mới trên host thực, tách Core/provider/model và cold/warm/busy/cancel; chưa công bố giảm RAM hoặc SLA khi chưa đo.
- [x] CP-23 Kiểm build/compliance/doc refs/secret/diff đúng phần thay đổi; không để reference vận hành treo hoặc dependency vô dụng.
- [ ] CP-24 Người dùng chạy kịch bản demo, tech lead review giới hạn và giấy phép; chưa đánh dấu hoàn tất toàn bộ #210.

## Cải thiện chất lượng đa mẫu

- [x] QI-01 Giữ đối chứng 30 ca và chốt thêm ca chưa dùng để chỉnh prompt.
- [ ] QI-02 Quyết định đủ căn cứ trước giá trị; đo từ chối đúng và không làm mất các ca rõ nghĩa.
- [x] QI-03 Quan hệ số và đối chiếu danh mục lấy từ dữ liệu/metadata, không có tên ngành trong mã dùng chung.
- [ ] QI-04 Nội dung ngắn hoàn chỉnh; lỗi kiểu, nguồn và cấu trúc không thành đề xuất ghi được.
- [x] QI-05 Giữ một lượt suy luận, một slot, thời hạn và hủy; không lặp suy luận vô hạn hoặc tự ghi.
- [x] QI-06 Đã đo đối chứng và ca bổ sung trên Core/model/giao diện thật, giữ đáp án và công bố giới hạn; nghiệm thu bản tích hợp vẫn ở CP-24.

QI-02 và QI-04 còn mở ở phần chất lượng ngữ nghĩa và văn phong; các lỗi model còn lại được theo dõi tại [BACKLOG](../dynamic-core/BACKLOG.md).
Bản hiện tại giữ một lượt suy luận; chưa nghiệm thu khả năng từ chối ổn định trên các cách diễn đạt mới.
