# Kế hoạch kiểm thử: Case Assistant

Kết quả được ghi theo từng ca; chỉ đánh dấu đạt cho phần đã chạy.
Kiểm schema và ID nguồn không thay thế nghiệm thu nội dung hoặc rà nhãn độc lập.

## 1. Môi trường và dữ liệu

- Dùng máy local, một bản sao Backend, bản sao sạch của nhánh và dữ liệu thử riêng.
- Không kiểm trên VPS hoặc đưa dữ liệu thật của người dùng vào fixture, prompt log và artifact.
- Dùng các mô hình một cửa, hỗ trợ và đặt tài nguyên trong thư viện mẫu; thêm dữ liệu tổng hợp bằng script kiểm hiện có.
- Có người tạo khác nhau, hai đơn vị, người được giao, vai đọc, nhiều vai và bản ghi con qua nhiều cấp.
- Bộ câu hỏi có điều kiện rõ, diễn đạt gần nghĩa, thiếu thông tin, phủ định, ngày mơ hồ, hồ sơ không có lịch sử và không có kết quả.
- Tối thiểu 36 câu, 12 câu mỗi mô hình; mỗi thao tác có bốn câu trong mỗi mô hình, cùng các ca an toàn/hồi quy riêng.
- Nhãn gồm ID bản ghi liên quan và các nhận định được nguồn hỗ trợ; người dùng rà trước khi dùng làm kết quả nghiệm thu.
- Model và dependency dùng để đo được ghi bằng tên/phiên bản; không ghi credential hoặc nội dung nhạy cảm.

## 2. Test case

| ID | WP | Ca kiểm | Kết quả mong đợi | Kết quả |
|---|---|---|---|---|
| CA-T-01 | 01 | Resolve dependency, kiểm giấy phép/model theo tiêu chuẩn dự án | Công cụ dùng lại được; model local và model phát hành được phân biệt | Đạt dependency; model/runtime phát hành còn chờ |
| CA-T-02 | 02 | Lệnh khởi động lõi khi AI tắt, không có cấu hình/model AI | Đăng nhập, CRUD, hộp việc và workflow hoạt động | Đạt: lõi tám service, không AI/Ollama; đăng nhập và đọc dữ liệu |
| CA-T-03 | 02 | Query không token; thực thể không được đọc; client tự gửi nguồn hoặc URL | 401/403 hoặc lỗi hợp đồng; không gọi AI với dữ liệu không hợp lệ | Đạt: unit và 10 ca API trợ lý |
| CA-T-04 | 02 | Trường/toán tử/SQL không hợp lệ, OR/NOT không hỗ trợ và model mơ hồ | Từ chối hoặc hỏi lại; không bỏ điều kiện rồi truy vấn rộng hơn | Đạt chặn lỗi biết trước; ngữ nghĩa bộ lọc chưa nghiệm thu |
| CA-T-05 | 02 | Bộ lọc chính xác, 0 kết quả, phân trang và tổng số | Khớp API chuẩn; không lấy top-k làm tổng số | Đạt: PostgreSQL, API và bộ lọc không sinh thống kê |
| CA-T-06 | 03 | Hai người, hai đơn vị, assigned, nhiều vai và owner_via nhiều cấp | Nguồn ngoài quyền không vào request retrieval/LLM hoặc response | Đạt: PostgreSQL và API theo quyền Core |
| CA-T-07 | 03 | Có 201 ứng viên, payload quá giới hạn, trường/lịch sử quá dài | Yêu cầu thu hẹp; không cắt ngầm rồi sinh, chọn khoảng lịch sử nhỏ có nhãn rõ | Đạt giới hạn số lượng/byte/trường/lịch sử; trường dài ghi BACKLOG |
| CA-T-08 | 03 | Thêm/xóa/sửa ứng viên ngoài top-k, thêm ghi chú, đổi model trong lúc model chờ | Tổng số/tập ứng viên có snapshot nhất quán; phát hiện stale cả nguồn ngoài top-k hoặc sự kiện mới | Đạt: snapshot PostgreSQL và test đổi nguồn khi AI chờ |
| CA-T-09 | 03 | Đổi quyền/đơn vị và lấy token mới | Áp dụng quyền mới, không dùng lại cache nội dung cũ | Đạt qua UserContext thay vai/đơn vị; Core giữ hiệu lực theo token |
| CA-T-10 | 04 | Tìm gần nghĩa, tìm mã chính xác và loại hồ sơ đích khỏi similar | Xếp hạng có căn cứ; cùng schema dùng được cho ba mô hình | Đạt trên pilot ba mô hình; nhãn nháp |
| CA-T-11 | 04 | Tóm tắt lịch sử, phân biệt người làm và thứ tự các lần bổ sung | Các ý được nguồn hỗ trợ; thiếu nội dung thì không tự thêm | Chưa nghiệm thu nội dung; có ca suy diễn trạng thái |
| CA-T-12 | 04 | JSON lỗi, ID nguồn bịa, đảo phủ định, tự nhận quy định/đã duyệt | Chặn lỗi cấu trúc/nguồn xác định được; rà thủ công lỗi ngữ nghĩa | Đạt schema/ID; lỗi ngữ nghĩa ghi BACKLOG |
| CA-T-13 | 04 | Chỉ dẫn độc hại trong mô tả, ghi chú và query | Không ghi dữ liệu, không gọi URL/tool tùy ý, không mở quyền | Đạt ranh giới: không tool ghi/DB, request không được cấp nguồn |
| CA-T-14 | 04 | Hai người hỏi đồng thời, lỗi và hủy giữa pipeline | Không lẫn corpus; store tạm được dọn, tác vụ hết hạn không chạy vô hạn | Đạt kho riêng, cleanup lỗi và giới hạn đồng thời; hủy chờ trên web |
| CA-T-15 | 04 | Ollama dừng, thiếu model, model chậm, AI dừng | Không tải ngầm trong request; lỗi rõ, bằng chứng hợp lệ và luồng lõi còn dùng được | Đạt: dừng AI/Ollama và timeout model thật; model thiếu/JSON lỗi qua test SDK |
| CA-T-16 | 05 | Web: find, summarize, similar; mở nguồn về hồ sơ | Nội dung rõ, link do client/Backend dựng; không tin URL do LLM tạo | Đạt API ba thao tác, web tóm tắt và mở nguồn |
| CA-T-17 | 05 | Loading/hủy, màn hình nhỏ, nội dung dài và HTML trong nguồn | Không treo giao diện hoặc thực thi HTML; lỗi có hướng xử lý | Đạt: 360px/chữ 200%, hủy chờ và HTML giả lập |
| CA-T-18 | 06 | Cấu hình overlay bật/tắt, env thiếu, kiểm cổng và route | AI tùy chọn; secret được kiểm khi bật; không có endpoint nội bộ công khai | Đạt: overlay tùy chọn, không publish cổng AI/Ollama |
| CA-T-19 | 06 | Chuẩn bị model lần đầu, restart và chạy offline | Chỉ tải khi thiếu trong bước chuẩn bị; cache còn dùng được | Đạt cache/offline thật; nhánh tải khi thiếu kiểm bằng mock |
| CA-T-20 | 07 | Đo chất lượng theo bộ đề được rà | Có số đo tái tính được, công bố cả hỏi lại và không trả lời | Chờ rà nhãn và nghiệm thu độc lập |
| CA-T-21 | 07 | Cold/warm, hai yêu cầu đồng thời, giới hạn CPU/RAM | Có thời gian, tài nguyên và tỷ lệ lỗi thực tế; không OOM hoặc ảnh hưởng nghiêm trọng lõi | Đã đo pilot, đồng thời; chưa phải benchmark tải dài |
| CA-T-22 | 07 | Hồi quy chuỗi check lõi với AI tắt và bật | Không thay hành vi ghi, quy trình, quyền và thay đổi mô hình | Đạt: 324 kiểm lõi; 10 trợ lý; khởi động lõi không AI |
| CA-T-23 | 07 | Clone sạch, hướng dẫn cài và dọn tài nguyên thử | Dùng được theo tài liệu, giữ nguyên dữ liệu/container dự án | Đạt bản sao mã/database thử rỗng; dọn riêng và giữ dữ liệu dự án |
| CA-T-24 | 07 | Rà diff, secret, artifact, license và dữ liệu mẫu | Chỉ có thay đổi đã duyệt, không có `report/` hoặc tài nguyên local | Đạt: license/header/route, gitleaks và diff; không commit/push |
| CA-T-25 | 05 | Tìm từ danh sách với bộ lọc hiện hành | Tự truyền model/entity, giữ điều kiện lọc và không nhập ID; danh sách chuẩn còn dùng được | Đạt trên browser và API thật |
| CA-T-26 | 05 | Tóm tắt/tìm tương tự từ chi tiết và từ kết quả | Đúng bản ghi đã chọn; không hiện ô ID; đọc lại nguồn qua quyền hiện có | Đạt browser bằng giả lập/replay; API Qwen thật có nguồn |
| CA-T-27 | 05 | Trạng thái và nguồn khi Qwen lỗi/ngược kết luận | Dữ kiện Backend tách riêng, đứng trước phần sinh; nguồn thu gọn và mở đúng bằng trích dẫn | Đạt render với câu mâu thuẫn giả lập; không xác nhận chất lượng model |
| CA-T-28 | 05 | Đổi câu hỏi/bộ lọc/loại hồ sơ, đóng panel hoặc hủy khi chờ | Không gắn kết quả cũ vào ngữ cảnh mới; không lỗi promise hoặc treo nút | Đạt hủy/đổi ngữ cảnh và deadline trình duyệt với phản hồi trễ giả lập |
| CA-T-29 | 05 | Liên kết sâu sai quyền/không tồn tại và AI tắt/lỗi | Không hiển thị nội dung không được phép; CRUD/bộ lọc thường giữ nguyên | Đạt sai chủ sở hữu thật, AI tắt và phản hồi lỗi giả lập |
| CA-T-30 | 05 | Bàn phím, 360px/chữ 200%, mở nguồn và quay về hồ sơ | Không tràn panel, đọc được nhãn, thao tác không cần nhập định danh | Đạt browser: Enter, panel và trang tìm kiếm, trích dẫn và link gốc |
| CA-T-31 | 05 | Đổi liên kết sâu trong SPA, Back/Forward, URL không hợp lệ và phản hồi cũ đang chờ | Model/entity/ID/thao tác cùng ngữ cảnh mới, nguồn cũ bị bỏ, không tự gọi sinh; quyền Core vẫn áp dụng | Đạt 7 kiểm browser; API đọc thật, phản hồi cũ giả lập |

## 3. Phương pháp đo

### Tìm kiếm

`Recall@4` đo phần bản ghi liên quan tìm được trong bốn kết quả đầu.
`nDCG@4` đo mức ưu tiên bản ghi hữu ích ở đầu danh sách.
Chấm bằng `ir_measures` với qrels/run xuất từ chính tập ứng viên và cấu hình đã chạy.
Tách số câu hỏi toàn bộ, đủ điều kiện tìm, yêu cầu thu hẹp, hỏi lại và lỗi; không loại ca khó khỏi báo cáo mà không ghi rõ.
Không so điểm tập ứng viên với số đo toàn kho như cùng một phép đo.

### Tổng hợp

Người kiểm đánh dấu từng nhận định là được nguồn hỗ trợ, sai hoặc không đủ căn cứ.
Tính tỷ lệ nhận định có căn cứ và số trường hợp bỏ sót thông tin quan trọng; kiểm thêm trình tự, phủ định và đối tượng.
Kiểm ID trích dẫn chỉ xác nhận nguồn tồn tại, không tự chứng minh nhận định đúng.
Không dùng chính model sinh câu trả lời làm người chấm duy nhất.

### Hiệu năng và ích lợi

Đo riêng lấy dữ liệu, embedding/xếp hạng và sinh; đo thêm tổng thời gian người dùng hoàn thành tác vụ.
Ghi p50/p95, CPU/RAM, số request, lỗi, thời gian warm-up, giới hạn container và cấu hình phần cứng.
So tìm từ khóa/hybrid và đọc thủ công trên cùng dữ liệu; có thể chấp nhận trade-off nếu công bố rõ.
Ngân sách ngữ cảnh và độ trễ được chốt sau pilot; không biến một con số dự kiến thành cam kết hiệu năng.

### Tiêu chí chấp nhận đề xuất

Các mức dưới đây là mục tiêu của kế hoạch được duyệt triển khai, không phải kết quả đã đo.

- Không có ca lộ nguồn ngoài quyền, ghi nghiệp vụ từ AI, trích ID nguồn không tồn tại hoặc thay đổi hành vi workflow.
- Bộ lọc được hỗ trợ và tổng số khớp API chuẩn ở toàn bộ ca kiểm; điều kiện không hỗ trợ được hỏi lại thay vì bị bỏ.
- Các case race, vượt giới hạn, lỗi/hủy và hồi quy trong bảng đều đạt trước khi đóng phần kỹ thuật.
- Trên bộ đề đã rà, ít nhất 95% nhận định sinh được người kiểm xác nhận có căn cứ; không chấp nhận câu tự xác nhận đã duyệt, tự đưa quy định hoặc đảo kết luận quan trọng.
- Recall@4/nDCG@4 phải có đủ bộ đề, nhãn và cách tính lại; không đặt yêu cầu hybrid luôn vượt baseline, người dùng duyệt mức hữu ích qua kết quả và kiểm local.
- Mục tiêu warm p95: tối đa 5 giây cho truy xuất không sinh, 30 giây cho toàn luồng một người, 60 giây khi hai người đồng thời; mọi request có deadline cứng tối đa 60 giây tính cả chờ.
- Không OOM, tăng bộ nhớ không giới hạn hoặc làm chuỗi kiểm lõi thất bại khi chạy cùng AI; phải công bố CPU/RAM và phần cứng thực tế.
- Model phát hành đạt tiêu chuẩn mở; nếu chưa đạt thì chưa đóng phần sinh như một tính năng phát hành hoàn chỉnh.

Pilot dùng để điều chỉnh ngân sách trước khi đóng băng bộ đề nghiệm thu.
Nếu mục tiêu không phù hợp phần cứng, báo số đo và đề xuất trade-off để duyệt lại, không âm thầm đổi ngưỡng hoặc loại ca không đạt.

## 4. Lệnh và bằng chứng

- Backend: `./mvnw verify`, formatter và test ranh giới kiến trúc hiện có.
- AI: `python -m pytest tests -q`; test pipeline giả lập và test model thật được phân biệt.
- Web: `npx tsc --noEmit`, build và kiểm trình duyệt trên ba thao tác.
- Hồi quy: chuỗi `deploy/dev/check_*.py` hiện có, thêm ca trợ lý trong script đúng phạm vi.
- Đánh giá truy xuất dùng dependency kiểm thử và artifact local; chưa tái lập nền tảng Langfuse hoặc runner đã gỡ.
- Kết quả tổng hợp ghi ở `docs/TESTING.md`; artifact chi tiết ở ngoài repository, có hướng dẫn tính lại.

Chỉ nghiệm thu khi các điều kiện an toàn và hồi quy đạt, người dùng kiểm local, và phần model phát hành được giải quyết hoặc phạm vi được người dùng duyệt lại rõ ràng.
