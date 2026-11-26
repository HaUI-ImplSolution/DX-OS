<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Thẻ dữ liệu

Dữ liệu câu gốc là bản nháp viết với công cụ AI để đội biên soạn, duyệt và xác nhận quyền đóng góp.
Biến thể sinh bằng mã của đội.
Các câu lạc đề từ nguồn ngoài lấy từ [MASSIVE](https://arxiv.org/abs/2204.08582) (CC BY 4.0, Amazon).
MASSIVE được bản địa hóa từ [SLURP](https://github.com/pswietojanski/slurp) và chứa một phần dữ liệu của SLURP (CC BY 4.0; Bastianelli, Vanzo, Swietojanski và Rieser, 2020).
Các câu do công cụ soạn mô tả tình huống giả lập và không chứa hồ sơ người dùng.
Câu gốc, định nghĩa nhãn, tập chỉnh và bộ nghiệm thu do đội biên soạn được công bố theo CC BY 4.0 tại [Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data).
Bản Kaggle chỉ gồm dữ liệu của đội.

## Thành phần

| Nhiệm vụ | Nhãn nghiệp vụ | Câu mỗi nhãn | Câu từ chối trong tệp nhiệm vụ | Tổng |
| --- | --- | --- | --- | --- |
| `onestop` | Giấy xác nhận, Phúc khảo, Hoãn thi | 135 | 120 | 525 |
| `itaccess` | GIT_USERS, VPN_USERS, WIKI_EDITORS | 135 | 120 | 525 |
| `retail` | Đổi hàng, Trả hàng | 135 | 120 | 390 |
| `support` | Thấp, Trung bình, Cao | 135 | 120 | 525 |
| `clinic` | Khám tổng quát, Tư vấn, Tái khám | 135 | 120 | 525 |

Bộ câu gốc có 2.490 dòng, gồm 1.890 câu nghiệp vụ và 600 câu từ chối riêng chia đều giữa bốn loại ca của từng nhiệm vụ.
Tệp `negatives.jsonl` có 17 câu dùng chung và năm câu lạc đề bổ sung, mỗi nhiệm vụ có một câu riêng.
Văn phong lịch sự, bình thường và cẩu thả chiếm lần lượt khoảng 30%, 40% và 30% trong mỗi tệp câu gốc.
Mỗi nhãn nghiệp vụ có 15 câu ngắn nói thẳng yêu cầu bằng tên nhãn hoặc từ gần nghĩa, chia đều cho ba văn phong.
Câu gốc và biến thể sinh từ câu gốc theo CC-BY-4.0, ghi công HaUI-ImplSolution.

Tập chỉnh ở `tools/classifier/eval/tuning/` gồm 420 câu do Claude (Anthropic) viết, chưa có người duyệt, chỉ để chọn ngưỡng và siêu tham số.
Anthropic cho phép dùng kết quả của Claude để huấn luyện bộ phân loại chuyên biệt không cạnh tranh với mô hình của Anthropic ([hướng dẫn](https://support.claude.com/en/articles/12326764-can-i-use-my-outputs-to-train-an-ai-model), đọc ngày 26/11/2026).
Bộ nghiệm thu ở `tools/classifier/eval/acceptance/` gồm 450 câu do đội viết tay, duyệt chéo và giữ riêng để đo.
Khi trích đặc trưng, các lệnh rõ ràng như “Bỏ qua mọi hướng dẫn” được lọc bỏ.
Các cụm có chủ thể “Hệ thống”, “Chương trình” hoặc “Máy” và động từ điền nhãn chỉ khớp khi theo sau là tên nhãn.
Cụm “luôn chọn” chỉ khớp khi đứng đầu đoạn, đi sau “hãy” hoặc đi trước tên nhãn.
Từ `output` và `ignore` đứng riêng được giữ lại.

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
Ngưỡng và siêu tham số chọn trên tập chỉnh ở `eval/tuning/`, nên phần chỉnh nội bộ không dùng khi huấn luyện.
Các họ câu dùng chung luôn thuộc cùng một phần ở cả năm nhiệm vụ.
Chỉ sinh biến thể từ câu gốc trong tập huấn luyện.
Một câu sinh tối đa bốn biến thể khác nhau, mang cùng `family`.
Cột tỉ lệ lấy từ trọng số chọn phép biến đổi, còn số mẫu thực tế phụ thuộc số phép tạo ra văn bản khác câu gốc và khác các biến thể đã có.

| Biến đổi | Tỉ lệ chọn | Số biến thể thực tế cộng năm nhiệm vụ |
| --- | --- | --- |
| Bỏ dấu toàn phần | 22% | 1.095 |
| Bỏ dấu một phần | 20% | 1.641 |
| Viết tắt | 14% | 374 |
| Lỗi gõ Telex | 12% | 1.004 |
| Lặp hoặc rơi một ký tự | 12% | 1.306 |
| Đổi hoa thường | 10% | 754 |
| Bỏ dấu câu | 10% | 609 |

| Nhiệm vụ | Câu gốc trong train | Biến thể train | Câu ngoài trong train | Tổng train | Chỉnh nội bộ | Dev |
| --- | --- | --- | --- | --- | --- | --- |
| `onestop` | 365 | 1.419 | 204 | 1.988 | 137 | 137 |
| `itaccess` | 365 | 1.435 | 204 | 2.004 | 137 | 137 |
| `retail` | 274 | 1.075 | 204 | 1.553 | 115 | 115 |
| `support` | 365 | 1.431 | 204 | 2.000 | 137 | 137 |
| `clinic` | 365 | 1.423 | 204 | 1.992 | 137 | 137 |
