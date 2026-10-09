# Kế hoạch chia phase

Chia các vấn đề trong [`PROJECT_IMPROVEMENTS.md`](./PROJECT_IMPROVEMENTS.md) thành các phase theo mức độ nghiêm trọng và phụ thuộc kỹ thuật.
Mỗi phase khi bắt đầu làm sẽ có một folder riêng sinh từ [`_template/`](./_template/) (xem [`README.md`](./README.md)).
Số ngày là ước lượng người-ngày, chưa tính thời gian chờ chuyên gia hay duyệt.

## 1. Tổng quan

| Phase | Tên | Mức độ | Nhóm | Ước lượng | Phụ thuộc | Mục tham chiếu |
|---|---|---|---|---|---|---|
| P0 | Chốt quyết định và phạm vi | Nghiêm trọng | A | 1-2 ngày | không | 22, 23 |
| P1 | Gia cố an toàn, poka-yoke và nền dựng sạch | Nghiêm trọng | A | 3-4 ngày | P0 | 9, 10, 17 |
| P2 | Danh tính nông hộ, phân quyền, migration | Nghiêm trọng | A | 4-5 ngày | P1 | 1, 6, 9, 16 |
| P3 | Open-Core: workflow, gateway, storage, sơ đồ | Nghiêm trọng | A | 5-7 ngày | P2 | 7, 8, 17 |
| P4 | Duyệt thửa/vụ, mã chuẩn, GPS, trang quản lý | Quan trọng | B | 6-8 ngày | P3 | 2, 3, 12, 13, 15, 16 |
| P5 | Vòng đời vụ mùa và thông báo cho farmer | Quan trọng | B | 4-5 ngày | P4 | 4, 10, 13, 15 |
| P6 | AI: kiểm tra ảnh, chẩn đoán | Quan trọng | B | 5-6 ngày | P1 (song song được với P4) | 5, 11 |
| P7 | Dữ liệu, BI và tri thức | Quan trọng | B | 4-5 ngày | P4, P6 | 14, 21 |
| P8 | Kiểm thử, dữ liệu demo, hồ sơ nộp bài | Nghiêm trọng (hạn nộp) | A | 5-6 ngày | làm xuyên suốt, chốt sau cùng | 17, 18, 19, 20 |
| P9 | _(Đã loại bỏ, xem ghi chú dưới bảng)_ | - | - | - | - | - |
| P10 | Để sau | Theo dõi | C | không tính | | 24, 15 (phần hoàn thiện) |

P9 đã loại bỏ: D1 (phase 0, chốt 05/10/2026) xác nhận với giảng viên hướng dẫn/BTC rằng đề tài Agri-Carbon hợp lệ ở vòng HaUI, không cần ứng dụng minh họa thứ hai.

Ánh xạ 24 mục ban đầu: 1→P2, 2→P4, 3→P4, 4→P5, 5→P6, 6→P2, 7→P3, 8→P3, 9→P1, 10→P1+P5, 11→P6, 12→P4, 13→P4+P5, 14→P7, 15→P4+P5+P10, 16→P2+P4+P8, 17→P1+P3+P8, 18→P8, 19→P8, 20→P8, 21→P7, 22→P0, 23→P0, 24→P10.

## 2. Thứ tự và phụ thuộc

```
P0 → P1 → P2 → P3 ─┬→ P4 → P5 ─┐
                   └→ P6 ──────┴→ P7 → P8
```

- P1 làm sớm vì nhỏ, rủi ro thấp, và là lỗi có thể bị trừ điểm nặng (secret, khóa PHI).
- P2 và P3 là móng của mọi thứ sau, nên xong trước khi làm tính năng.
- P6 chỉ cần P1 nên làm song song với P4 nếu có người rảnh.
- P8 chạy xuyên suốt (test, tài liệu, seed demo cập nhật theo từng phase), chốt ở cuối.

## 3. Lộ trình rút gọn (dự phòng, hiện không áp dụng)

**Đã chốt ở phase 0 (DP0-04, 05/10/2026): không bị ràng buộc mốc 14 ngày, làm đầy đủ P0-P8 + P10 theo ước lượng thật (~40 người-ngày), không cắt work package nào vì thời gian.**
Giữ bảng dưới làm phương án dự phòng — chỉ áp dụng lại nếu sau này có mốc thời gian mới ép buộc; đề thi gốc cho 14 ngày kể từ ngày công bố nội bộ, trong khi cộng đầy đủ các phase là khoảng 40 người-ngày, với tối đa 3 người làm song song, giữ nhóm A và cắt phần còn lại:

| Phase | Giữ lại | Cắt hoặc hoãn |
|---|---|---|
| P0 | Toàn bộ | |
| P1 | Toàn bộ | |
| P2 | Household gắn `sub`, ma trận quyền, chặn farmer ở web và Superset | Onboarding qua Keycloak Admin API, xóa mềm |
| P3 | Workflow dùng chung có lịch sử + `assignedTo` + danh sách chờ (tự làm, theo D2), cửa API bằng APISIX standalone (theo D3), MinIO (theo D4), sơ đồ 3 tầng | Flowable (đã loại theo D2) |
| P4 | Duyệt thửa/vụ qua workflow, trang quản lý tối thiểu cho admin và engineer | GPS nâng cao, danh mục giống đầy đủ, định tuyến Mattermost |
| P6 | Precheck ảnh (chất lượng, nhận diện lúa) | Vòng phản hồi nhãn, nhập triệu chứng bằng text |
| P8 | Toàn bộ | |
| P5, P7, P10 | | Hoãn |

## 4. Quyết định cần chốt ở P0

| # | Quyết định | Lựa chọn | Ảnh hưởng |
|---|---|---|---|
| D1 | Đề tài nông nghiệp có được chấp nhận ở vòng HaUI, có làm ứng dụng thứ hai không | Chấp nhận / thêm app trường học | P9, thuyết trình |
| D2 | Workflow engine | Flowable / tự làm | P3, P4, P5 |
| D3 | Cửa API | APISIX standalone / Traefik / Nginx | P3 |
| D4 | Lưu ảnh | MinIO / storage service tự bọc | P3 |
| D5 | Thông báo cho farmer | Hộp thông báo trong app trước, push sau | P5 |
| D6 | Viết test cho phần lõi | Bật lại (đề yêu cầu) | P1, P8 |
| D7 | Phạm vi AI | Chỉ 5 bệnh lá, hay thêm đường "khác/không rõ" cho kỹ sư | P6 |
| D8 | Danh mục giống và nguồn số ngày sinh trưởng | Lấy từ nguồn nào, ai xác nhận | P4 |
| D9 | Ma trận quyền engineer | Quyền gì, phạm vi theo khu vực hay không | P2 |
| D10 | Nguồn dữ liệu mở | Thời tiết (Open-Meteo) và nguồn thứ hai | P7 |

Tất cả D1-D10 đã chốt ngày 05/10/2026, xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` mục "Quyết định thiết kế đã chốt" (D1=A, D2=tự làm, D3=APISIX standalone, D4=MinIO, D5=hộp thông báo GET+PATCH, D6=bật test case crash/logic, D7=giữ 5 bệnh lá, D8=tự biên soạn danh mục giống, D9=theo thao tác, D10=Open-Meteo). Còn mở: ngày công bố đề nội bộ và ngày hạn nộp thật.

## P0. Chốt quyết định và phạm vi

- Mức độ: Nghiêm trọng. Nhóm A. Ước lượng 1-2 ngày. Mục tham chiếu: 22, 23.
- Thay đổi tổng quát: không code, chỉ chốt các quyết định D1 đến D10, xác định ngày hạn thật, cập nhật lại plan này theo kết quả.
- Phạm vi: tài liệu, họp với giảng viên hướng dẫn.
- Hành vi thay đổi: không có.
- Trade-off: tốn 1-2 ngày không ra tính năng, đổi lại tránh làm sai hướng cả phase lớn (đặc biệt D1 và D2).
- Kết quả: D1-D10 đã chốt (xem mục 4 ở trên và `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md`). Còn mở: ngày hạn nộp thật, phân việc cho từng người.

## P1. Gia cố an toàn, poka-yoke và nền dựng sạch

- Mức độ: Nghiêm trọng. Nhóm A. Ước lượng 3-4 ngày. Mục tham chiếu: 9, 10, 17.
- Thay đổi tổng quát:
  - Loại secret hard-code và mật khẩu mặc định, thêm quét secret.
  - Đóng lỗ hổng khóa PHI: ngày phun, múi giờ, báo cáo trên vụ đã thu hoạch, ràng buộc vụ mùa, khóa lạc quan.
  - Xác thực cho mọi endpoint AI, kiểm tra upload thật, xóa EXIF.
  - Script reset về máy mới, mở rộng CI (test, build web/mobile/AI), chốt log theo profile.
- Phạm vi: backend (service, dto, config), AI (auth), infra (compose, `.env.example`, realm), CI, docs.

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Ghi nhận phun với `appliedAt` lùi xa | Nhận, khóa thu hoạch vô hiệu | Từ chối, giới hạn ngày hợp lệ |
| Tính ngày hết cách ly | Theo múi giờ container | Theo `Asia/Ho_Chi_Minh` |
| Tạo báo cáo trên vụ đã thu hoạch | Nhận | Từ chối |
| Quên điền biến secret | Chạy với mật khẩu yếu | Dừng và báo tên biến |
| Gọi AI `/predict` không key | Nhận | 401 |
| Upload file giả ảnh, ảnh có GPS | Nhận, giữ EXIF | Từ chối, xóa EXIF |

- Trade-off: siết validation và bỏ giá trị mặc định làm dữ liệu demo cũ và cách dựng khác đi, cần cập nhật seed và README. Ràng buộc một vụ ACTIVE mỗi thửa cần dọn dữ liệu cũ.
- Rủi ro: thấp, các thay đổi nhỏ và độc lập.
- Kết quả mong muốn: quét secret sạch, clone sạch dựng lên đủ dịch vụ healthy bằng một script, CI xanh có chạy test, mỗi lỗ hổng ở mục 10 có test chứng minh đã đóng.

## P2. Danh tính nông hộ, phân quyền, migration

- Mức độ: Nghiêm trọng. Nhóm A. Ước lượng 4-5 ngày. Mục tham chiếu: 1, 6, 9, 16.
- Thay đổi tổng quát:
  - Chuyển sang migration thật (Flyway), dựng bảng Nông hộ gắn `sub`, thửa trỏ vào hộ.
  - Audit lưu id người dùng thay vì tên, thêm xóa mềm.
  - Ma trận quyền role x hành động, `@PreAuthorize` cho thửa, vụ, thu hoạch.
  - Chặn farmer ở web và Superset, thêm `aud` cho API.
  - Onboarding: admin tạo hộ và tài khoản (qua Keycloak Admin API).
- Phạm vi: backend, SSO (realm), Superset (map role), web (ẩn và chặn farmer), migration dữ liệu.

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Ai thấy thửa nào | So khớp chuỗi tên | Theo id tài khoản qua nông hộ |
| Hai farmer trùng tên | Thấy chung thửa | Tách riêng |
| Engineer tạo thửa/vụ | Được | 403 |
| Farmer vào web hoặc Superset | Được | Bị chặn thật |
| `reportedBy`, `decidedBy` | Tên hiển thị | Có id người dùng |

- Trade-off: đổi mô hình dữ liệu cốt lõi, phá tương thích request tạo thửa, cần migrate dữ liệu cũ (ghép tên sang hộ). Keycloak Admin API thêm secret và phụ thuộc, có thể cắt khỏi bản rút gọn.
- Rủi ro: migrate sai làm mất quyền xem thửa của farmer. Giảm thiểu bằng báo cáo ghép trước khi chạy, chạy trên bản sao.
- Kết quả mong muốn: farmer chỉ thao tác được qua mobile, quyền engineer đúng bảng đã chốt, dữ liệu cũ vẫn truy cập được, mọi bản ghi mới có id người tạo.

## P3. Open-Core: workflow, gateway, storage, sơ đồ

- Mức độ: Nghiêm trọng. Nhóm A. Ước lượng 5-7 ngày. Mục tham chiếu: 7, 8, 17.
- Thay đổi tổng quát:
  - Vạch ranh giới Core và DX-Farm trong code.
  - Workflow dùng chung: lịch sử không ghi đè, `assignedTo`, danh sách việc chờ, timeline (theo D2).
  - Cửa API chung: JWT, rate limit, routing, ẩn AI và internal API (theo D3), reverse proxy và HTTPS.
  - Storage service cho ảnh có metadata và link có hạn (theo D4).
  - Sơ đồ 3 tầng và sơ đồ luồng request.
- Phạm vi: backend (tách module), infra (gateway, proxy, storage), docs. Frontend và mobile chỉ đổi địa chỉ gọi API và cách lấy ảnh.

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Duyệt, từ chối, áp dụng | Đổi enum, ghi đè | Ghi sự kiện, có người xử lý, xem lại được lịch sử |
| Danh sách việc chờ | Không có | API và dữ liệu cho trang quản lý |
| Client gọi dịch vụ | Thẳng cổng 8081, 8000 | Qua một cửa chung |
| Xem ảnh | `/uploads` công khai | Link có hạn |

- Trade-off: Flowable có sẵn lịch sử và task nhưng nặng và tốn thời gian học, tự làm gọn hơn nhưng ít chuẩn. Gateway thêm một chặng và cấu hình. MinIO thêm một dịch vụ tốn RAM. Refactor lớn dễ gây hồi quy.
- Rủi ro: cao nhất của dự án. Giảm thiểu bằng cách giữ API cũ chạy song song, chuyển từng luồng một, có test hồi quy ở mỗi bước.
- Kết quả mong muốn: một quy trình chạy hoàn toàn trên workflow dùng chung (ít nhất chẩn đoán), có sơ đồ, nói được thành phần nào giữ nguyên khi đổi ứng dụng.

## P4. Duyệt thửa/vụ, mã chuẩn, GPS, trang quản lý

- Mức độ: Quan trọng. Nhóm B (phần duyệt và trang quản lý nên giữ). Ước lượng 6-8 ngày. Mục tham chiếu: 2, 3, 12, 13, 15, 16.
- Thay đổi tổng quát:
  - Farmer tạo thửa và vụ ngay trên mobile (hàng đợi offline, GPS), mã thửa và mã vụ tự sinh, chọn giống từ danh mục kèm ngày thu hoạch gợi ý.
  - Thửa và vụ chờ admin duyệt qua workflow, bị từ chối thì sửa và gửi lại.
  - Trang quản lý cho admin và engineer: router, danh sách có phân trang và lọc, chi tiết, timeline.
  - Thông báo Mattermost cho yêu cầu mới, định tuyến theo loại sự kiện.
- Phạm vi: backend (API danh sách, trạng thái, mã), web (router, trang quản lý), mobile (màn hình tạo, GPS), Mattermost.

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Tạo thửa, vụ | Web, nhập tay mã, lưu ngay | Mobile, mã tự sinh, chờ admin duyệt |
| Vị trí thửa | Tùy chọn | Bắt buộc, lấy từ GPS |
| Ngày dự kiến thu hoạch | Nhập tùy ý | Gợi ý theo giống, lệch nhiều phải nêu lý do |
| Xem yêu cầu và báo cáo | Chọn tuần tự thửa, vụ | Danh sách tập trung có lọc |

- Trade-off: thêm bước duyệt làm chậm nông dân, đổi lại dữ liệu sạch. Bắt GPS bắt buộc có thể vướng vùng sóng yếu, cần đường dự phòng admin nhập hộ. Danh mục giống cần nguồn số liệu có xác nhận.
- Rủi ro: khối lượng frontend lớn nhất (cả web lẫn mobile), dễ trễ. Giảm thiểu bằng trang quản lý tối thiểu trước, nâng cấp sau.
- Kết quả mong muốn: farmer tạo thửa và vụ trên mobile, admin duyệt trên web, thấy yêu cầu chờ kèm thông tin hộ, mọi bước có lịch sử.

## P5. Vòng đời vụ mùa và thông báo cho farmer

- Mức độ: Quan trọng. Nhóm B. Ước lượng 4-5 ngày. Mục tham chiếu: 4, 10, 13, 15.
- Thay đổi tổng quát:
  - Khai báo thu hoạch có cảnh báo và cờ xác nhận ở backend, ghi ngày thực tế, sản lượng, diện tích.
  - Job định kỳ nhắc đến hạn, farmer chốt lại ngày dự kiến và có lịch sử gia hạn.
  - Hộp thông báo trong app cho farmer (kết quả duyệt, yêu cầu chụp lại, nhắc thu hoạch).
  - Farmer tự khai "đã phun", kỹ sư xác nhận lại. Chặn hoặc cảnh báo thu hoạch khi còn khuyến nghị chưa xử lý.
  - Admin làm thay farmer được ghi nhận rõ.
- Phạm vi: backend (scheduler, thông báo), mobile (màn hình thu hoạch, hộp thông báo), web (cảnh báo cho admin).

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Bấm thu hoạch sớm | Qua ngay | Phải xác nhận kèm lý do, backend kiểm |
| Đến ngày dự kiến chưa thu hoạch | Im lặng | Thông báo, farmer chốt ngày mới |
| Xác nhận đã phun | Kỹ sư | Farmer khai, kỹ sư xác nhận |
| Farmer biết kết quả | Mở app xem | Nhận thông báo trong app |

- Trade-off: hộp thông báo kéo về khi mở app, không tức thì như push, nhưng không phụ thuộc dịch vụ đóng. Đổi quy trình phun làm thay đổi trách nhiệm, cần chốt với kỹ sư.
- Rủi ro: scheduler chạy sai múi giờ, đã được xử lý ở P1.
- Kết quả mong muốn: vụ mùa đi hết vòng từ tạo đến thu hoạch có kiểm soát, farmer nhận được thông báo ở mọi mốc quan trọng.

## P6. AI: kiểm tra ảnh, chẩn đoán

- Mức độ: Quan trọng. Nhóm B. Ước lượng 5-6 ngày. Mục tham chiếu: 5, 11.
- Thay đổi tổng quát:
  - Endpoint precheck ảnh qua backend, mobile kiểm kích thước và dung lượng, server kiểm độ sáng, độ nét và nhận diện lúa bằng crop classifier.
  - Báo cáo có trạng thái "cần chụp lại" riêng, tách khỏi từ chối chẩn đoán, farmer chụp lại được liên kết với báo cáo cũ.
  - Nhập triệu chứng thay luồng nhập kết quả tay: truy xuất RAG xếp hạng bệnh trong danh mục, kỹ sư chốt.
  - Gọi AI ngoài transaction, cho phép chẩn đoán lại, lưu nhãn kỹ sư chốt làm dữ liệu huấn luyện.
  - Giữ ngưỡng confidence 0.70 cho detector bệnh; crop classifier cho qua khi Rice ≥ 0.80 và chênh lớp khác ≥ 0.20, còn nhận nhầm ngô, cỏ, lúa mì.
- Phạm vi: AI service, backend (trạng thái báo cáo, luồng chẩn đoán), mobile (màn chụp), web (màn kỹ sư).

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Ảnh mờ/nhỏ/tối/chói | Chỉ kiểm khi chẩn đoán | Precheck trước khi gửi và kiểm lại ở server |
| Ảnh không phải lúa | Không có lớp lọc riêng | Crop classifier chặn ở precheck và khi nộp; vẫn là giới hạn với cây gần giống lúa |
| Kỹ sư cần ảnh khác | Không có đường | Yêu cầu chụp lại kèm lý do, farmer được báo |
| AI nhận không ra | Nhập tay (lỗi trên DB sạch) | Nhập triệu chứng, hệ thống gợi ý trong danh mục |
| Kỹ sư không đồng ý AI | Không chạy lại được | Chẩn đoán lại có lịch sử |

- Trade-off: precheck và crop classifier thêm độ trễ, thêm model phải nạp, và có thể từ chối ảnh dùng được. Giữ kỹ sư chốt bệnh và điều trị; sâu hại chưa có mô hình.
- Rủi ro: AI không hoạt động khiến server chưa thể nhận ảnh. Mobile giữ ảnh trong hàng đợi để gửi lại khi dịch vụ phục hồi.
- Kết quả mong muốn: precheck, chụp lại, gợi ý bệnh qua triệu chứng và chẩn đoán lại hoạt động; nhãn kỹ sư được lưu có cấu trúc.

## P7. Dữ liệu, BI và tri thức

- Mức độ: Quan trọng, một phần Theo dõi. Nhóm B. Ước lượng 4-5 ngày. Mục tham chiếu: 14, 21.
- Thay đổi tổng quát:
  - Superset chỉ đọc VIEW của bản ghi đã duyệt, bỏ SELECT trên bảng gốc.
  - Thêm chart: báo cáo theo bệnh theo thời gian, điểm nóng, thửa đang cách ly, lịch thu hoạch, tỷ lệ AI đúng.
  - Chốt giải pháp nền bản đồ.
  - Tích hợp thêm nguồn dữ liệu mở (thời tiết) kèm snapshot dự phòng.
  - Trang tài liệu (knowledge hub) và màn hỏi đáp có trích nguồn từ RAG.
- Phạm vi: Postgres (view, quyền), Superset (dashboard, export), backend hoặc AI (nguồn ngoài, snapshot), web (trang tài liệu, hỏi đáp).

| Tình huống | Hiện tại | Sau phase |
|---|---|---|
| Dữ liệu lên dashboard | Mọi bản ghi kể cả chưa duyệt | Chỉ bản ghi đã duyệt |
| Hỏi đáp tài liệu | Chỉ qua API kỹ thuật | Có màn hình, có trích nguồn |
| Nguồn ngoài ngừng | Không có | Dùng snapshot |

- Trade-off: view và đổi quyền làm dashboard cũ phải sửa dataset. Nguồn dữ liệu ngoài thêm điểm có thể hỏng. Tự host tile bản đồ là một việc riêng, có thể chấp nhận nền trắng.
- Rủi ro: Superset 3.1.3 cũ, export dashboard dễ lệch. Giảm thiểu bằng cách export lại và kiểm tra dựng từ đầu.
- Kết quả mong muốn: dashboard chỉ hiện dữ liệu đã duyệt, có ít nhất hai nguồn dữ liệu mở có nguồn gốc và giấy phép, hỏi đáp tài liệu chạy được.

## P8. Kiểm thử, dữ liệu demo, hồ sơ nộp bài

- Mức độ: Nghiêm trọng về hạn nộp. Nhóm A. Ước lượng 5-6 ngày, làm xuyên suốt. Mục tham chiếu: 17, 18, 19, 20 (và i18n lỗi ở mục 16).
- Thay đổi tổng quát:
  - Test cho phần lõi (state machine, khóa PHI, phạm vi dữ liệu, idempotency, workflow) và E2E tự động trên môi trường sạch, bật trong CI.
  - Dữ liệu demo: ít nhất một rule thuốc được duyệt (hoặc demo trung thực nhánh `review_required`), hộ, thửa, vụ, báo cáo mẫu có tọa độ ĐBSCL.
  - Hồ sơ PoF: repo public, release và tag, README theo yêu cầu đề, CHANGELOG, CONTRIBUTING, issue tracker, bảng license, SBOM, lockfile, SPDX header.
  - Portal hiện trạng thái dịch vụ, chuẩn hóa thông báo lỗi tiếng Việt.
  - Kịch bản demo 10 phút, báo cáo 8-10 trang, slide tối đa 12, video 3-5 phút, bộ trả lời câu hỏi hội đồng.
- Phạm vi: toàn repo, chủ yếu docs, CI, seed.
- Hành vi thay đổi: không đổi nghiệp vụ, đổi cách chứng minh và đóng gói.
- Trade-off: dành nhiều ngày cho thứ không thêm tính năng, nhưng đây là phần có điểm chắc chắn (50 điểm PoF) và là điều kiện không bị loại.
- Rủi ro: dồn hết vào cuối. Giảm thiểu bằng cách cập nhật tài liệu và seed theo từng phase, chừa tối thiểu 2 ngày cuối cho rehearsal.
- Kết quả mong muốn: clone sạch chạy được theo README, E2E xanh, repo public có tag, đủ báo cáo, slide, video, và kịch bản demo đã tập.

## P9. (Đã loại bỏ)

- D1 đã chốt (phase 0, 05/10/2026): đề tài Agri-Carbon được giảng viên hướng dẫn/BTC xác nhận hợp lệ ở vòng HaUI, không cần ứng dụng minh họa thứ hai.
- Bằng chứng "đổi ứng dụng, Core có đổi không" (câu hỏi hội đồng số 1) chuyển sang dựa vào sơ đồ 3 tầng và ranh giới module rõ ràng ở P3, không có app thứ hai minh họa trực tiếp.

## P10. Để sau

- Mức độ: Theo dõi. Nhóm C.
- Gồm: polygon ruộng và PostGIS, push thật (FCM, UnifiedPush), carbon và IoT, GraphRAG và multi-agent, low-code frontend, Mattermost hai chiều, scale thật, icon và splash mobile, domain và HTTPS cho APK phát hành.
- Trade-off: hoãn nghĩa là chấp nhận các giới hạn đã ghi ở mục 24 và mục 15 khi trả lời hội đồng, nên cần câu trả lời chuẩn bị sẵn.

## 5. Gợi ý bổ sung

- Phân việc song song cho 3 người: một người lo Core (P1, P3), một người lo luồng nghiệp vụ và frontend/mobile (P2, P4, P5), một người lo AI và dữ liệu (P6, P7), cả nhóm chia P8.
- Mỗi phase kết thúc bằng một buổi demo nội bộ ngắn và cập nhật bảng chỉ mục trong `README.md`.
- Sau mỗi phase chạy lại bộ hồi quy ở `03_TEST_PLAN.md` và dựng thử từ clone sạch.
- Đo tiến độ bằng số mục DoD đã đạt và số lỗi nghiêm trọng còn mở, không đo bằng số dòng code.
- Cập nhật plan này khi P0 chốt xong các quyết định, và khi có thông tin chính xác về hạn nộp.
