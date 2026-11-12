# Bộ trả lời câu hỏi hội đồng

Những câu hội đồng dễ hỏi sau phần trình diễn, kèm câu trả lời của đội cho bản `2.0.0`.
Mười câu đầu theo thứ tự câu hỏi định hướng ở mục 13 của [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html); các câu sau là câu hỏi phụ hay gặp.
Mỗi câu trả lời nói được trong khoảng nửa phút.
Dòng **Bằng chứng** là chỗ mở ngay khi hội đồng muốn xem: đường dẫn trong repository, lệnh, bước CI.
Số liệu chỉ lấy từ số đã đo; chưa đo thì nói chưa đo.
Mức hoàn thiện của từng tính năng ở [Ma trận bằng chứng](./evidence-matrix.md), việc chưa làm ở [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md).

| # | Câu hỏi |
|---|---|
| 1 | Open-Core nằm ở đâu, thành phần nào chạy headless? |
| 2 | Vì sao chọn kiến trúc và stack này, giấy phép gì? |
| 3 | Đổi ứng dụng nghiệp vụ thì lõi có phải đổi không? |
| 4 | Định danh, cổng API, dữ liệu và luồng công việc được cung cấp dùng chung thế nào? |
| 5 | Điểm nào thể hiện H-P-D-I thay vì một dashboard CRUD? |
| 6 | AI nằm ở đâu, dữ liệu và giới hạn là gì? |
| 7 | Một thành phần ngừng thì hệ thống ra sao? |
| 8 | Dựng lại từ mã nguồn trên máy mới được không? |
| 9 | Từ 100 lên 100.000 người dùng thì sao? |
| 10 | Cộng đồng muốn đóng góp thì làm thế nào? |
| 11 | Còn gì chưa làm? |
| 12 | Đội tự viết gì, khi đã dùng nhiều phần mềm nguồn mở? |
| 13 | Sao không dùng nền low-code có sẵn? |
| 14 | Lõi "không giao diện" mà sao có bảng điều khiển và trang quản trị Keycloak? |
| 15 | Đổi mô hình khi hệ thống đang chạy có an toàn không? |
| 16 | Lịch sử hồ sơ có chống sửa lén không? |
| 17 | Bảo mật: gọi thẳng API để lách quy trình thì sao? |
| 18 | Giấy phép tuân thủ thế nào? |

## 1. Open-Core nằm ở đâu, thành phần nào chạy headless?

- Tầng 1 là lõi: dịch vụ `dx-engine` (áp dụng mô hình, API chung, Flowable nhúng) cùng thư viện `dx-core`, Keycloak, APISIX, PostgreSQL và SeaweedFS.
  Không thành phần nào có trang cho người dùng cuối; mọi thứ qua API.
- Tầng 2 là ứng dụng: giao diện react-admin dựng từ metadata, portal và màn hình giám sát.
- Tầng 3 chỉ là tệp mô hình, không có mã.
- Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực; `dx-model` không phụ thuộc module nào, `dx-core` không phụ thuộc `dx-engine`.
- **Bằng chứng:** `ARCHITECTURE.md` mục 1 và 2; `backend/dx-engine/src/test/.../ArchitectureTest.java`; [Ma trận bằng chứng](./evidence-matrix.md) mục 1.

## 2. Vì sao chọn kiến trúc và stack này, giấy phép gì?

- Dùng phần mềm nguồn mở trưởng thành cho từng việc: Keycloak cho định danh, APISIX cho cổng, PostgreSQL cho dữ liệu, SeaweedFS cho tệp, Flowable cho quy trình, pgschema để đổi lược đồ.
  Đội tự viết phần nối chúng theo một tệp mô hình.
- Java 21 và Spring Boot vì ranh giới giữa các module kiểm được lúc biên dịch.
- Mã của đội theo Apache-2.0, repository chỉ có một giấy phép.
  Mọi thành phần khác đều theo giấy phép OSI; không dùng BUSL, SSPL, RSAL, MSCL hay phần Enterprise.
- **Bằng chứng:** [Thành phần được chọn và không chọn](./dependencies.md); `DEPENDENCIES.txt`; `sbom/`; job `Check licenses` của workflow `Compliance`.

## 3. Đổi ứng dụng nghiệp vụ thì lõi có phải đổi không?

- Không: nghiệp vụ nằm hoàn toàn trong tệp mô hình.
  Thư viện mẫu có bảy mô hình, từ mượn thiết bị, cấp quyền IT, đặt phòng đến bàn một cửa của sinh viên, phủ cả ba họ quy trình và cùng chạy trên một lõi.
- Đưa một mô hình thuộc họ khác vào thì chạy được mà không sửa mã lõi, không build lại giao diện.
- Hai mô hình dùng cùng tên vai vẫn không vào được dữ liệu của nhau, vì mỗi mô hình có Keycloak client riêng cho vai của nó.
- **Bằng chứng:** `backend/dx-engine/src/main/resources/templates/`; `python3 deploy/dev/check_demo.py` (tiêu chí 6); `python3 deploy/dev/check_conformance.py` (cách ly giữa mô hình).

## 4. Định danh, cổng API, dữ liệu và luồng công việc được cung cấp dùng chung thế nào?

| Dịch vụ | Cách cung cấp |
|---|---|
| Định danh và SSO | Keycloak; vai, client và nhóm đơn vị sinh từ mô hình; quản trị người dùng ngay ở bảng điều khiển; quản trị viên có thể bị bắt nhập mã một lần (`STAFF_OTP_FLOW=CONDITIONAL`) |
| Cổng API | APISIX là cửa duy nhất: kiểm token, giới hạn tốc độ, định tuyến; route không kiểm token nằm trong danh sách công khai kiểm bởi `check_routes.py`; backend kiểm token lần hai |
| Dữ liệu có cấu trúc | PostgreSQL, mỗi mô hình một schema `m_<tên>`; bảng, ràng buộc, chỉ mục sinh từ mô hình |
| Dữ liệu phi cấu trúc | SeaweedFS qua API S3, trường `file` của mô hình, quét ClamAV khi bật, link tải có hạn |
| Luồng công việc | Flowable nhúng; ba họ mẫu `approval`, `work_item`, `reservation`; trạng thái hồ sơ chỉ đổi qua quy trình |

- **Bằng chứng:** `ARCHITECTURE.md` mục 3 đến 6 và 13; `python3 deploy/apisix/check_routes.py`; `python3 deploy/dev/check_api.py`.

## 5. Điểm nào thể hiện H-P-D-I thay vì một dashboard CRUD?

- **H, môi trường số chung:** một lần đăng nhập, vai và đơn vị khai trong mô hình; sinh viên chỉ thấy hồ sơ của mình, cán bộ chỉ thấy hồ sơ của đơn vị mình hoặc được giao, và xử lý từ hộp việc.
- **P, rào chắn và luồng tự động:** trạng thái chỉ đổi qua quy trình (gọi thẳng API để đổi bị chặn); từ chối và trả lại bắt buộc có lý do; người tạo không tự quyết hồ sơ của mình; hạn xử lý, quá hạn lên đầu hộp việc; việc sau khi duyệt chạy qua hàng đợi và được thử lại.
- **D, một nguồn sự thật:** dữ liệu nghiệp vụ, lịch sử và hàng đợi cùng nằm ở PostgreSQL; lịch sử hồ sơ nối thành chuỗi hash; mọi thay đổi báo ra ngoài bằng webhook có chữ ký.
- **I, trí tuệ:** chưa làm (xem câu 6 và 11).
- **Bằng chứng:** `python3 deploy/dev/check_demo.py` (tám tiêu chí của kịch bản trình diễn); `check_case.py`, `check_process.py`, `check_events.py`.

## 6. AI nằm ở đâu, dữ liệu và giới hạn là gì?

- Phần AI chưa có.
  Dịch vụ tìm kiếm tri thức từng có đã gỡ khỏi repository, vì chưa nối vào lõi và mô hình embedding của nó chưa kiểm được dữ liệu huấn luyện theo định nghĩa Open Source AI của OSI.
- Hướng làm: hỏi đáp có trích nguồn và tác tử đề xuất giá trị cho một trường, luôn có người duyệt trước khi ghi, chạy tại chỗ để dữ liệu không ra ngoài máy.
- Điều kiện: chỉ dùng mô hình có trọng số và dữ liệu huấn luyện đều mở; chưa chọn mô hình nào.
- Lõi đã có sẵn chỗ để nối: hàng đợi sự kiện, webhook có chữ ký và `GET /api/v1/events` cho tác tử đọc việc mới, còn ghi lại vẫn đi qua API chung nên quyền, quy tắc và quy trình vẫn áp dụng.
- **Bằng chứng:** [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md), dòng "Phần AI"; `docs/dynamic-core/README.md`, mục "Cam kết nguồn mở".

## 7. Một thành phần ngừng thì hệ thống ra sao?

- **Keycloak ngừng giữa lúc áp dụng mô hình:** lần áp dụng dừng, mô hình bị giữ kể cả sau khi khởi động lại; `resume` hoàn tất lần đó, `abandon` đưa bảng về phiên bản đang chạy.
  Khi Keycloak từ chối tài khoản dịch vụ, API báo riêng lỗi "từ chối" khác với "không trả lời".
- **Bước quy trình lỗi:** hồ sơ, quy trình và dòng lịch sử cùng quay về như cũ.
- **Hành động hoàn tất lỗi:** nằm lại ở hàng đợi, thử lại với thời gian chờ tăng dần, người triển khai xem lỗi và thử lại ở bảng điều khiển.
- **Bên nhận webhook lỗi:** thông điệp được gửi lại, các dòng của một hồ sơ vẫn tới đúng thứ tự, bên nhận lỡ tin thì kéo lại theo `eventId`.
- **Màn hình giám sát** là dịch vụ riêng, không phụ thuộc module nào khác của backend.
- **Bằng chứng:** `check_recovery.py`, `check_guards.py`, `check_events.py`, `KeycloakAdminClientTest`, `WebhookSenderTest`.

## 8. Dựng lại từ mã nguồn trên máy mới được không?

- Được, hai đường.
  Từ mã nguồn: `deploy/make_env.sh`, rồi `cd deploy && docker compose up -d --build`; mật khẩu sinh ngẫu nhiên, thiếu bí mật thì Compose không chạy.
  Không cần mã nguồn: `dxos-install.sh` của bản phát hành tải gói, kiểm SHA-256, cài vào `~/.dxos`; `dxos start <tệp mô hình>` lên đủ dịch vụ, áp dụng tệp và in tài khoản `admin`.
- Mọi cấu hình nằm ở `deploy/.env`, không sửa tệp mã.
- Đã kiểm: `dxos-install.sh` của release `v2.0.0` cài và `dxos start` kéo image `v2.0.0` từ GHCR trên máy chủ đã xóa stack cũ, rồi áp dụng một tệp mô hình.
- **Bằng chứng:** `README.md` mục Chạy thử; các job `Install from the release archive` và `Validate the Compose file` của workflow `Platform` (gói sai SHA-256 bị từ chối, mọi tệp Compose mount đều có); `docs/TESTING.md` mục 3.

## 9. Từ 100 lên 100.000 người dùng thì sao?

- Chưa đo tải ở quy mô đó.
  Tải đồng thời đã đo đến 200 người dùng ảo ở các cấu hình 12, 8, 4 và 2 nhân: từ 4 nhân trở lên đạt ngưỡng p95 dưới 500 ms đến 100 người dùng đồng thời, 2 nhân đạt đến 75; chi tiết ở [`docs/SIZING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/SIZING.md).
  Số đã đo: danh sách 10.000 dòng có lọc và sắp xếp trả về ở phân vị 95 là 11 ms; cả tám dịch vụ dùng khoảng 1,8 GiB RAM lúc rảnh.
- Backend chạy một bản.
  Thử hai bản sau gateway cho thấy cả hai nhận lưu lượng, nhưng sau một lần áp dụng mô hình, danh sách mô hình và trạng thái giữ lệch nhau giữa hai bản; vì vậy đây là giới hạn đã ghi, không phải khả năng đã có.
- Mỗi mô hình có khóa riêng khi áp dụng nên các mô hình áp dụng song song; dữ liệu của mỗi mô hình nằm ở schema riêng.
- **Bằng chứng:** `docs/TESTING.md` (cách kiểm); `deploy/OPERATIONS.md` (tài nguyên từng dịch vụ); [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md) mục giới hạn chấp nhận.

## 10. Cộng đồng muốn đóng góp thì làm thế nào?

- Thêm một nghiệp vụ là thêm một tệp mô hình: đặt vào `backend/dx-engine/src/main/resources/templates/` và khai trong `index.json` để hiện ở thư viện mẫu; kiểm bằng `POST /api/v1/models/plan` hoặc `dxos plan`.
  Đặc tả cho trình soạn thảo là `model-v1.schema.json`; tệp sai mẫu ở `models/invalid/`.
- Thêm họ mẫu quy trình mới là thêm một `ProcessFamily` và tệp BPMN cùng tên trong `dx-engine`.
- `CONTRIBUTING.md` nêu quy trình nhánh, pull request, kiểm trước khi gửi; có mẫu issue và pull request ở `.github/`; CI chạy riêng cho backend, web, nền tảng (kiểm đầu-cuối), phụ thuộc và tuân thủ.
- **Bằng chứng:** `CONTRIBUTING.md`; `.github/ISSUE_TEMPLATE/`; `.github/workflows/`.

## 11. Còn gì chưa làm?

- Chưa có phần AI: tìm kiếm tri thức có nguồn, hỏi đáp sinh câu trả lời và tác tử có người duyệt.
- Điều kiện `when` của hành động chỉ là một phép so sánh trên một trường, chưa có và/hoặc.
  Duyệt nhiều cấp tuần tự và rẽ nhánh theo ngưỡng khai báo được bằng quy trình tự khai báo, mới kiểm tay trên stack tạm chứ chưa có kiểm tự động.
- Ba nguyên lý đầu của đề mới được đáp ứng một phần: hồ dữ liệu có phần lưu trữ hồ sơ đã đóng theo lịch nhưng chưa có lớp truy vấn riêng; dữ liệu vận hành mới vào qua bộ mô phỏng IT/OT và chưa có bản sao số; phả hệ mới dừng ở danh mục dữ liệu của mô hình, chưa đến từng trường.
- Một bản backend; chưa chịu lỗi nhiều nút; chưa thử tải lớn.
- Đích webhook dạng `chat` chưa thử với Mattermost và Slack thật.
- Đổi vai hoặc đơn vị của người đang đăng nhập có hiệu lực sau tối đa khoảng 5 phút; không có quyền theo từng trường.
- Dữ liệu trong các kiểm là dữ liệu do script tạo trên stack rỗng; chưa có tổ chức nào dùng thật.
- Bản triển khai công khai vẫn là `v1.1.1`; lõi mới phải dựng lại vì không có đường nâng cấp dữ liệu.
- **Bằng chứng:** [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md); [Ma trận bằng chứng](./evidence-matrix.md) mục 4.

## 12. Đội tự viết gì, khi đã dùng nhiều phần mềm nguồn mở?

- Định dạng tệp mô hình và bộ kiểm báo lỗi theo dòng và cột.
- Phần điều khiển: so sánh hai phiên bản mô hình thành thay đổi `SAFE`, `MIGRATION`, `LOSS`; đổi tên trường giữ dữ liệu; chặn mọi thao tác xóa mà mô hình không yêu cầu; áp dụng từng bước chạy lại được; quay về, tiếp tục, hủy bỏ.
- API chung đọc mô hình lúc chạy, dựng truy vấn an toàn từ mô hình đã kiểm, quyền theo vai và theo dòng.
- Ba họ mẫu quy trình có tham số và các quy tắc chung mà quy trình nào cũng phải theo; trạng thái chỉ đổi qua quy trình, cùng giao dịch với việc ghi hồ sơ.
- Hàng đợi sự kiện, webhook có chữ ký và thứ tự, chuỗi hash của lịch sử cùng công cụ kiểm từ bên ngoài.
- Bảng điều khiển cho người triển khai và lệnh `dxos`.
- **Bằng chứng:** `ARCHITECTURE.md` mục 4 đến 10; `backend/dx-model`, `backend/dx-engine`; [Ma trận bằng chứng](./evidence-matrix.md) mục 2.

## 13. Sao không dùng nền low-code có sẵn?

- Nền cần tự sinh bảng, vai và quy trình từ một tệp khai báo, đổi được khi đã có dữ liệu, và là lõi không có giao diện cho người dùng cuối.
- Hai sản phẩm đội xem xét đầu tiên bị loại vì giấy phép: NocoBase có điều khoản cấm cung cấp hoặc phân phối sản phẩm no-code, low-code hay PaaS xây trên nó; Directus theo giấy phép có điều kiện, không thuộc OSI; Strapi trộn phần MIT với phần Enterprise đóng trong cùng kho.
- Supabase được đọc để học kiến trúc; lõi tự viết phần API chung vì cần đọc mô hình lúc chạy và chặn đổi trạng thái ngoài quy trình.
- Phần dùng được thì dùng lại nguyên bản: Keycloak, Flowable, APISIX, pgschema.
- **Bằng chứng:** [Thành phần được chọn và không chọn](./dependencies.md) mục 3; `docs/dynamic-core/README.md` mục Tư tưởng thiết kế.

## 14. Lõi "không giao diện" mà sao có bảng điều khiển và trang quản trị Keycloak?

- "Không giao diện" nghĩa là lõi không có màn hình nghiệp vụ.
  Bảng điều khiển của người triển khai, giao diện hồ sơ, portal và màn hình giám sát thuộc Tầng 2: chúng gọi lõi bằng API sau APISIX như mọi ứng dụng khác.
- Trang quản trị Keycloak đi kèm phần mềm nguồn mở dùng nguyên bản, là bàn làm việc của người vận hành; mặc định không mở ra gateway và không ứng dụng nào gọi tới.
- `dx-engine` chỉ phục vụ API.
- **Bằng chứng:** `ARCHITECTURE.md` mục 1 và 13; `frontend/src/console`; `deploy/apisix/`.

## 15. Đổi mô hình khi hệ thống đang chạy có an toàn không?

- Xem trước không đổi gì: kế hoạch nêu thay đổi theo mức `SAFE`, `MIGRATION`, `LOSS`, các dòng đang vi phạm quy tắc mới, số hồ sơ đang xử lý bị ảnh hưởng và câu lệnh sẽ chạy.
  Áp dụng chỉ chạy khi mức rủi ro không vượt mức người dùng đã xác nhận và không có dòng vi phạm.
- Đổi tên trường theo `renamed_from` giữ nguyên dữ liệu; mất bảng hay cột mà mô hình không yêu cầu thì áp dụng dừng lại.
- Trong lúc áp dụng, mô hình bị giữ; lỗi giữa chừng thì áp dụng lại để làm tiếp hoặc hủy bỏ; có quay về phiên bản trước.
- Đổi luật quy trình chỉ được khi thực thể không còn hồ sơ đang xử lý; vai `ADMIN` của nền tảng không khai báo được.
- Giới hạn: không có người thứ hai duyệt thay đổi mô hình, đây là chính sách tùy bản triển khai.
- **Bằng chứng:** `python3 deploy/dev/check_change.py` (65 kiểm), `check_guards.py` (61), `check_recovery.py` (19); `ARCHITECTURE.md` mục 4.

## 16. Lịch sử hồ sơ có chống sửa lén không?

- Mỗi dòng lịch sử mang hash nối với dòng trước, có điểm neo định kỳ lưu ở kho đối tượng; một lệnh chỉ ra dòng nào đã bị sửa, xóa hoặc chèn.
- Chuỗi chỉ phát hiện, không ngăn sửa: người sửa được cả cơ sở dữ liệu lẫn kho đối tượng của cùng máy chủ có thể tính lại cả chuỗi và các điểm neo cho khớp nhau.
- Vì vậy bên nhận webhook giữ cặp `eventId` và `hash` của các tin đã nhận, rồi so với hệ thống bằng `examples/history-witness/witness.py`; sửa lại cả chuỗi vẫn bị lộ vì hash bên nhận đã giữ không đổi.
- Dòng `updated` ghi cả giá trị cũ và mới của từng trường.
- **Bằng chứng:** `python3 deploy/dev/check_events.py` (kiểm sửa một dòng thì `firstBreak` chỉ đúng dòng đó); `examples/history-witness/README.md`; `docs/TESTING.md` mục 4.

## 17. Bảo mật: gọi thẳng API để lách quy trình thì sao?

- Mọi yêu cầu có cột `status` bị từ chối (403): trạng thái chỉ đổi qua `POST .../actions/{action}`, nơi quy trình kiểm người được làm và trạng thái đầu.
- Tên bảng và cột chỉ lấy từ mô hình đã kiểm, giá trị luôn đi qua tham số; tên trường hay toán tử không có trong mô hình bị từ chối.
- Quyền xét ở mọi đường: danh sách, bộ lọc, số đếm, lịch sử; thực thể con xét theo bản ghi cha; hồ sơ đã giao thì chỉ người được giao xử lý.
- Token kiểm ở gateway và ở backend; không có token thì 401; người giữ vai của mô hình này không đọc được dữ liệu của mô hình khác.
- Thay đổi tài khoản và áp dụng mô hình đều ghi `audit_log` kèm người thực hiện; mật khẩu không bao giờ được ghi; mật khẩu tạm do hệ thống sinh và phải đổi ở lần đăng nhập đầu.
- Quét mã độc tệp tải lên có sẵn nhưng tắt mặc định; bí mật sinh ngẫu nhiên và gitleaks quét ở mọi pull request.
- **Bằng chứng:** `python3 deploy/dev/check_conformance.py`, `check_demo.py` (tiêu chí 4), `check_users.py`; job `Scan for secrets` của workflow `Compliance`.

## 18. Giấy phép tuân thủ thế nào?

- Mã của đội theo Apache-2.0, có `LICENSE` toàn văn, `NOTICE` và dòng SPDX ở đầu mỗi tệp mã.
- Repository chỉ có một giấy phép, Apache-2.0; không có thành phần AGPL nào trong cây mã.
- Mỗi bản phát hành có gói `.tar.gz` kèm `.sha256` và SBOM CycloneDX; không dùng định dạng đóng.
- Job `Check licenses` của workflow `Compliance` chạy ở mọi pull request: kiểm header, giấy phép của từng gói trong SBOM, danh mục image.
- Cam kết của đội chặt hơn thể lệ: cả công cụ, mô hình AI (khi có) và dữ liệu đều phải mở; thành phần không đạt đã gỡ khỏi bản phát hành.
- Hai gói dữ liệu (CC-BY-4.0, CC0-1.0) chỉ dùng lúc dựng trang docs được nêu tên kèm lý do; ClamAV tùy chọn có image kèm `libclamunrar` theo giấy phép UnRAR, ghi rõ ở `DEPENDENCIES.txt`.
- **Bằng chứng:** `LICENSE`, `NOTICE`, `DEPENDENCIES.txt`; `python3 .github/scripts/check_licenses.py`; [Thành phần được chọn và không chọn](./dependencies.md); [Rà soát PoF](./pof-audit.md).
