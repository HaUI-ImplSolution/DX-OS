# Kiểm chứng các chỉ số mục tiêu

Tài liệu này ghi cách đo và kết quả của bốn chỉ số mục tiêu trong [`docs/dynamic-core/README.md`](./dynamic-core/README.md), cùng chi phí hạ tầng của lõi tự cài.
Số đo ngày 2026-11-12 trên một máy Linux 12 nhân, 30 GB RAM, đã có sẵn các image.
Chưa có người thật nào thử; phần người mới là một bản mô phỏng và được ghi rõ như vậy.

## Mức của một hệ thống

Mức nói một hệ thống xây trên DX-OS cần viết gì ngoài tệp mô hình.

| Mức | Hệ thống gồm | Mã phải viết |
|---|---|---|
| 1 | Thực thể, trường, vai, quyền và một họ quy trình có sẵn, chỉ đặt tham số như hạn, vai xử lý, giao diện | Không |
| 2 | Như mức 1, cộng quy trình tự khai báo hoặc mở rộng: trạng thái, hành động, điều kiện `when`, leo thang, tự đóng, hành động hoàn tất | Không |
| 3 | Có logic ngoài lõi: dịch vụ gọi API, bên nhận webhook tự viết, hành động có `check`, giao diện hoặc trang riêng ([`EXTENSIONS.md`](./EXTENSIONS.md)) | Có |

## Quy tắc đếm mã tự viết

- Mã tự viết là mọi dòng mã nguồn (Java, Kotlin, Python, JavaScript, TypeScript, SQL, shell, Go, Ruby, PHP, C#, HTML, CSS) nằm trong thư mục của hệ thống.
- Dòng trống và dòng chỉ có chú thích không tính.
- Tệp mô hình và tệp cấu hình (YAML, JSON, `.env`) là khai báo, không phải mã, và được đếm riêng.
- Mức của một tệp mô hình suy ra từ những khóa nó dùng: `check` là mức 3; `states`, `actions`, `when`, `escalate`, `auto_close`, `after`, `require` hoặc `role` là mức 2; còn lại là mức 1.
- Khóa `webhook` không tự nâng mức; bên nhận tự viết, dịch vụ hoặc giao diện riêng tính là mã tự viết khi nằm trong thư mục của hệ thống.

Cách chạy: `python3 tools/count_own_code.py <thư mục>` (thêm `--json` để lấy dạng máy đọc).

## Mã tự viết

Mười mẫu mô hình đi kèm ([`templates/`](../backend/dx-engine/src/main/resources/templates)) có 252 dòng khai báo và 0 dòng mã tự viết.
Tám mẫu ở mức 1 và hai mẫu ở mức 2.

| Mẫu | Mức | Dòng khai báo |
|---|---|---|
| `notes` | 1 | 12 |
| `support_desk` | 1 | 18 |
| `plant_incident` | 1 | 20 |
| `asset_register` | 1 | 26 |
| `retail_return` | 1 | 26 |
| `room_booking` | 1 | 28 |
| `equipment` | 1 | 33 |
| `onestop` | 1 | 34 |
| `itaccess` | 2 | 23 |
| `clinic_appointment` | 2 | 32 |

Hệ thống tự dựng trong bài thử mô phỏng bên dưới ở mức 2, dài 23 dòng khai báo, 0 dòng mã.
Phép đếm không cho kết quả 0 với mọi thứ: `examples/history-witness` (111 dòng Python) và `examples/itot-simulator` (205 dòng Python) là mã tự viết, cả hai ở mức 3.

Để so sánh, phần Lab viết tay ngày trước gồm 5.792 dòng Java, 4.524 dòng giao diện và 8 migration cho một hệ thống.
Với một luồng nghiệp vụ, phép so sánh 347 dòng Java viết tay với 28 dòng khai báo nằm ở `examples/effort-benchmark/README.md`; đó là một luồng, không phải cả hệ thống.

## Thời gian dựng và đổi

Đo trên máy có sẵn image, từ thư mục bản cài rỗng (chưa có dữ liệu):

| Việc | Thời gian |
|---|---|
| `dxos start <tệp mô hình>`: dịch vụ lên đủ, tạo tài khoản `admin`, áp dụng tệp | 71 giây |
| `dxos plan` một tệp mô hình đã sửa | 0,3 giây |
| `dxos apply` thêm một trường và đổi tên một trường, bảng đã có 2 hồ sơ | 0,7 giây |

Mục tiêu "dưới 10 phút" đạt khi máy đã có image; lần đầu còn thêm thời gian tải image, chưa đo.
Đổi tên một trường là kế hoạch mức `MIGRATION`, nên `dxos apply` đòi `--accept MIGRATION`; sau khi đổi, hai hồ sơ vẫn còn đủ, tổng số tiền và trạng thái không đổi.

## Người mới dựng hệ thống đầu tiên

### Bài thử có hướng dẫn

Dành cho một người chưa biết hệ thống, có máy Linux cài Docker; người quan sát không giúp trừ khi bị hỏi và ghi mọi lần giúp.

- **Việc giao:** dựng hệ thống "Đề nghị mua sắm": nhân viên nộp đề nghị có tiêu đề và số tiền; trưởng nhóm duyệt đề nghị đến 5 triệu đồng, đề nghị lớn hơn phải chuyển lên giám đốc duyệt.
- **Tài liệu cho phép:** `README.md`, `docs/MODEL.md`, `docs/OPERATIONS.md`, và các trang trong bảng điều khiển.
- **Hoàn thành khi:** hệ thống chạy; có ba tài khoản thuộc ba vai; một đề nghị 3 triệu được trưởng nhóm duyệt; một đề nghị 20 triệu bị trưởng nhóm từ chối duyệt thẳng, chuyển lên rồi được giám đốc duyệt.
- **Ghi lại:** thời điểm bắt đầu và xong, từng lỗi gặp (thông báo, cách vượt), từng lần cần hỗ trợ, thời gian đọc tài liệu, và người thử có cho rằng mình tự làm được không.

### Bản mô phỏng ngày 2026-11-12

Một phiên Claude đóng vai người mới, chỉ dùng `README.md` và `docs/MODEL.md`, làm đúng việc giao ở trên trên một stack riêng.
Phiên này đã biết hệ thống, nên thời gian đọc và viết không đại diện cho người thật và không được tính; chỉ thời gian chạy lệnh và các điểm vướng là số thật.

Tệp mô hình cuối cùng:

```yaml
format: 0
name: purchase
roles:
  - {name: STAFF}
  - {name: LEAD, staff: true}
  - {name: DIRECTOR, staff: true}
entities:
  request:
    label: Đề nghị mua sắm
    fields:
      title: {type: text, required: true}
      amount: {type: number, required: true, min: 1}
      reason: {type: text}
    access: {STAFF: own, LEAD: all, DIRECTOR: all}
    process:
      family: approval
      handler: [LEAD, DIRECTOR]
      states:
        lead_approved: {label: Chờ giám đốc}
      actions:
        approve: {from: lead_approved, to: approved, role: DIRECTOR}
        lead_approve: {from: submitted, to: approved, role: LEAD, when: "amount <= 5000000"}
        lead_forward: {from: submitted, to: lead_approved, role: LEAD, when: "amount > 5000000"}
```

Kết quả: đề nghị 3 triệu được trưởng nhóm duyệt; đề nghị 20 triệu bị từ chối duyệt thẳng (409, `CONDITION_NOT_MET`), được chuyển lên rồi giám đốc duyệt; lịch sử ghi `created`, `lead_forward`, `approve`.

Điểm vướng gặp phải:

- Lần áp dụng đầu thất bại vì ví dụ nhiều cấp duyệt trong `MODEL.md` dùng trạng thái `lead_approved` mà không khai báo nó. Thông báo lỗi chỉ đúng dòng và cột nên sửa được; ví dụ đã được bổ sung phần `states`.
- Lần thứ hai thất bại vì ghi đè hành động `approve` mà thiếu `from` và `to`, nên trạng thái `lead_approved` không có lối ra. Thông báo nói rõ trạng thái nào và ở dòng nào.
- Đổi tên một trường đòi `--accept MIGRATION`; thông báo nêu rõ mức và tham số cần thêm.
- Tạo tài khoản cho ba vai và đăng nhập thử được làm bằng script chứ không qua bảng điều khiển, nên đường qua giao diện chưa được thử.

Mô phỏng này không chứng minh được "trong một buổi" cho người thật: nó chỉ cho thấy các bước kỹ thuật mất vài chục giây và các lỗi gặp đều có thông báo chỉ ra chỗ sửa.

## Chi phí hạ tầng của lõi tự cài

Cấu hình tham chiếu gồm một máy ảo chạy toàn bộ stack bằng Docker Compose.
Các phép đo tải trên 2, 4, 8 và 12 nhân ([`SIZING.md`](./SIZING.md)) cho thấy 4 nhân phục vụ được 100 người dùng đồng thời với p95 dưới 500 ms, và 2 nhân phục vụ được 75; RAM thật dùng của các dịch vụ chính đỉnh khoảng 2 GB.
Phép đo ghim các nhân riêng cho stack, còn vCPU dùng chung của nhà cung cấp có thể cho hiệu năng thấp hơn, nên cấu hình tham chiếu chỉ là ước lượng chưa kiểm trên máy ảo thật.
Cấu hình tham chiếu là 4 vCPU, 8 GB RAM, ổ SSD 80 GB trở lên; 2 vCPU và 4 GB cho một nhóm nhỏ.

Giá thuê tháng tra ngày 2026-11-12 từ các trang tổng hợp giá, chưa đối chiếu với trang giá của nhà cung cấp và có thể đã đổi, nên cần kiểm lại trước khi dùng:

| Nhà cung cấp và gói | Giá một tháng | Một năm |
|---|---|---|
| Hetzner CX33, 4 vCPU dùng chung, 8 GB | khoảng 6,5 đến 7 EUR ([bitdoze](https://www.bitdoze.com/md/hetzner-cloud-cost-optimized-plans.md), [costgoat](https://costgoat.com/pricing/hetzner)) | khoảng 78 đến 84 EUR |
| DigitalOcean Basic, 4 vCPU dùng chung, 8 GB | khoảng 48 USD ([getdeploying](https://getdeploying.com/digitalocean)) | khoảng 576 USD |

- Chưa tính: sao lưu ngoài máy, tên miền, thuế, và công người vận hành.
- Công người vận hành chưa đo; các việc định kỳ nằm ở [`docs/OPERATIONS.md`](OPERATIONS.md) (cập nhật bản phát hành, sao lưu, kiểm tra lịch sử).
- Với 100 người dùng một năm, các giải pháp thuê bao theo đầu người ở `docs/dynamic-core/README.md` từ 12.000 đến 26.400 USD; lõi tự cài chỉ cộng tiền máy chủ ở bảng trên và công vận hành.

## Chưa làm

- Một người thật chưa biết hệ thống làm bài thử có hướng dẫn và ghi kết quả vào bảng trên.
- Đo công người vận hành mỗi tháng.
- Thời gian lần cài đầu có tải image.
- Dựng hệ thống mẫu qua bảng điều khiển thay vì script.
