<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Thẻ dữ liệu

Dữ liệu câu gốc là bản nháp viết với công cụ AI để đội biên soạn, duyệt và xác nhận quyền đóng góp.
Biến thể sinh bằng mã của đội.
Các câu lạc đề từ nguồn ngoài lấy từ MASSIVE (CC BY 4.0, Amazon).
Các câu do công cụ soạn mô tả tình huống giả lập và không chứa hồ sơ người dùng.
Câu gốc, định nghĩa nhãn, bộ nghiệm thu do đội viết tay và bộ chỉnh ngưỡng được công bố theo CC BY 4.0 tại [Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data); bản này không gồm câu của MASSIVE.

## Thành phần

| Nhiệm vụ | Nhãn nghiệp vụ | Câu mỗi nhãn | Câu từ chối trong tệp nhiệm vụ | Tổng |
| --- | --- | --- | --- | --- |
| `onestop` | Giấy xác nhận, Phúc khảo, Hoãn thi | 120 | 120 | 480 |
| `itaccess` | GIT_USERS, VPN_USERS, WIKI_EDITORS | 120 | 120 | 480 |
| `retail` | Đổi hàng, Trả hàng | 120 | 120 | 360 |
| `support` | Thấp, Trung bình, Cao | 120 | 120 | 480 |
| `clinic` | Khám tổng quát, Tư vấn, Tái khám | 120 | 120 | 480 |

Bộ câu gốc có 2.280 dòng, gồm 1.680 câu nghiệp vụ và 600 câu từ chối riêng chia đều giữa bốn loại ca của từng nhiệm vụ.
Tệp `negatives.jsonl` có 17 câu dùng chung và năm câu lạc đề bổ sung, mỗi nhiệm vụ có một câu riêng.
Trong từng tệp câu gốc, văn phong lịch sự chiếm 30%, bình thường 40% và cẩu thả 30%.
Trong mỗi nhiệm vụ, hơn một nửa số câu nghiệp vụ diễn đạt yêu cầu mà không dùng nguyên văn tên nhãn.
Dữ liệu đội viết và biến thể sinh từ dữ liệu đó theo CC-BY-4.0, ghi công HaUI-ImplSolution.

| Nguồn | Phiên bản hoặc revision | Giấy phép | Câu lấy mẫu |
| --- | --- | --- | --- |
| [MASSIVE của Amazon](https://github.com/alexa/massive#accessing-and-processing-the-data) | 1.0 | CC-BY-4.0 | 300 |

Tập train `vi-VN` của MASSIVE có 11.514 câu.
Công cụ lấy mẫu theo seed từ trường `utt` thuộc các ý định đồng hồ báo thức, nấu ăn, truyện cười, tiền tệ và phép tính sau khi lọc theo manifest và khử trùng sau chuẩn hóa.
Mã SHA-256 và URL của bản phát hành gốc nằm trong [manifest](sources.json).
Mỗi câu lấy mẫu từ bên ngoài được gán `TU_CHOI` với loại ca `lac_de` cho cả năm nhiệm vụ.
Dữ liệu ngoài lưu trong cache và không đóng gói.
Văn bản nguồn ngoài được giữ nguyên trong các ví dụ lấy mẫu.
Tệp giấy phép của MASSIVE nằm trong bản phát hành 1.0 của Amazon.

## Chia tập và biến thể

Seed cố định là 210.
Họ câu được chia theo nhãn và loại từ chối thành khoảng 68% huấn luyện, 16% chỉnh nội bộ và 16% dev để đo.
Tập 300 câu MASSIVE dùng chung cho mỗi nhiệm vụ gồm 204 câu huấn luyện, 48 câu chỉnh nội bộ và 48 câu dev.
Các họ câu dùng chung luôn thuộc cùng một phần ở cả năm nhiệm vụ.
Chỉ sinh biến thể từ câu đội viết trong tập huấn luyện.
Một câu sinh tối đa bốn biến thể khác nhau, mang cùng `family`.
Cột tỉ lệ lấy từ trọng số chọn phép biến đổi, còn số mẫu thực tế phụ thuộc số phép tạo ra văn bản khác câu gốc và khác các biến thể đã có.

| Biến đổi | Tỉ lệ chọn | Số biến thể thực tế cộng năm nhiệm vụ |
| --- | --- | --- |
| Bỏ dấu toàn phần | 22% | 1.016 |
| Bỏ dấu một phần | 20% | 1.455 |
| Viết tắt | 14% | 391 |
| Lỗi gõ Telex | 12% | 865 |
| Lặp hoặc rơi một ký tự | 12% | 1.243 |
| Đổi hoa thường | 10% | 772 |
| Bỏ dấu câu | 10% | 543 |

| Nhiệm vụ | Câu đội viết trong train | Biến thể train | Câu ngoài trong train | Tổng train | Chỉnh nội bộ | Dev |
| --- | --- | --- | --- | --- | --- | --- |
| `onestop` | 338 | 1.316 | 204 | 1.858 | 128 | 128 |
| `itaccess` | 338 | 1.317 | 204 | 1.859 | 128 | 128 |
| `retail` | 256 | 1.008 | 204 | 1.468 | 109 | 109 |
| `support` | 338 | 1.332 | 204 | 1.874 | 128 | 128 |
| `clinic` | 338 | 1.312 | 204 | 1.854 | 128 | 128 |
