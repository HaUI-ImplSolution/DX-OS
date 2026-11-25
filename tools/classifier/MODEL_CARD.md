<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Thẻ mô hình

Bộ phân loại đưa ra gợi ý từ phần chữ của hồ sơ tiếng Việt trong năm mẫu DX-OS, kèm cụm chữ làm căn cứ.
Kết quả là tín hiệu hỗ trợ người xử lý hồ sơ.
Quyết định nghiệp vụ thuộc cán bộ có thẩm quyền.

| Nhiệm vụ | Nhãn nghiệp vụ |
| --- | --- |
| `onestop` | Giấy xác nhận, Phúc khảo, Hoãn thi |
| `itaccess` | GIT_USERS, VPN_USERS, WIKI_EDITORS |
| `retail` | Đổi hàng, Trả hàng |
| `support` | Thấp, Trung bình, Cao |
| `clinic` | Khám tổng quát, Tư vấn, Tái khám |

Mỗi nhiệm vụ có thêm lớp `TU_CHOI` theo [định nghĩa nhãn](labels.json) revision `vi-2026-11-25-v1`.

## Thông số

| Mục | Giá trị |
| --- | --- |
| Tên | DX-OS Vietnamese Record Classifier, định dạng trọng số 1, định nghĩa nhãn `vi-2026-11-25-v1` |
| Kiểu mô hình | Hồi quy logistic đa lớp trên n-gram ký tự và từ băm vào 8.192 chiều; không dùng mạng nơ-ron hay LLM |
| Số tham số | 32.772 cho mỗi mô hình bốn lớp; 24.579 cho `retail` (ba lớp) |
| Kích thước tệp | 211–305 KB mỗi mô hình; image trợ lý chứa ba mô hình, tổng 876 KB |
| Ngưỡng | onestop 0,790; support 0,757; clinic 0,917; itaccess 0,681; retail 0,605 |
| Huấn luyện | Python 3.12 thư viện chuẩn, seed 210, 28 vòng; 4 phút 15 giây cho năm mô hình trên Intel Core i5-10300H, chỉ CPU; chạy lại cho cùng từng byte |
| Suy luận trong trợ lý (Node) | Nạp năm mô hình 30 ms; một hồ sơ p50 0,19 ms, p95 0,46 ms; lô 25 hồ sơ p50 5,1 ms, p95 9,1 ms |
| API rà loại hồ sơ trên stack chạy thật | Lượt 11 hồ sơ, gồm cả đọc Core: p50 30 ms, p95 63 ms qua 12 lượt |
| RAM | Container trợ lý 50 MiB (giới hạn 256 MiB); cả stack 11 container 2,1 GiB |
| Dữ liệu | Dữ liệu đội và bộ nghiệm thu tại [Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data) (CC BY 4.0); câu lạc đề từ MASSIVE vi-VN 1.0 |

Số đo thời gian và RAM lấy trên máy Intel Core i5-10300H, 8 CPU, 8 GB RAM, ngày 25/11/2026.

## Huấn luyện

Công cụ huấn luyện năm mô hình hồi quy logistic đa lớp bằng SGD có L2, 28 vòng với seed 210.
Văn bản được chuẩn hóa NFC và chuyển chữ thường, sau đó đặc trưng ký tự có dấu và bỏ dấu cùng đặc trưng từ 1–2 được băm vào 8.192 vị trí.
Phạm vi n-gram ký tự, cách bỏ dấu ở đặc trưng từ và hệ số L2 được chọn từ lưới khai trong `train.py`.
Tập chỉnh gồm 420 câu do Claude viết với công cụ AI và chỉ dùng để chọn ngưỡng và siêu tham số.
Bộ nghiệm thu là một bộ riêng do đội tự viết.
Bộ chọn tối đa hóa độ phủ, với ít nhất 97% đúng trên đề xuất và ít nhất 97% từ chối đúng trong từng loại `lac_de`, `mo_ho`, `hai_y` và `chen_lenh`.
Khi bỏ `--tune`, công cụ dùng tập chỉnh nội bộ đã tách theo họ câu.
Tệp trọng số ghi seed, revision nhãn, cấu hình đặc trưng và SHA-256 cho tập huấn luyện và tập chỉnh.
Nguồn và số lượng dữ liệu nằm trong [thẻ dữ liệu](data/DATA_CARD.md).

### Lọc câu lệnh

Trước khi trích đặc trưng cho mô hình, `features.py` tách văn bản thành các đoạn câu theo dấu chấm, chấm hỏi, chấm than, chấm phẩy và xuống dòng.
Bộ lọc dò lệnh chung trong `COMMAND` trên từng đoạn đã chuyển chữ thường và bỏ dấu.
Mẫu lệnh đầu đoạn “Chọn” được dựng từ danh sách nhãn đã chuẩn hóa trong tệp trọng số.
Đoạn khớp lệnh được bỏ khỏi đầu vào trích đặc trưng và căn cứ.
Các đoạn còn lại được dùng để suy luận và mô hình có thể từ chối khi thiếu căn cứ.

## Đo và giới hạn

Số đo trên tập chỉnh dùng để chọn cấu hình và ngưỡng.

| Nhiệm vụ | Câu | Đúng trên đề xuất | Độ phủ | Từ chối đúng |
| --- | --- | --- | --- | --- |
| onestop | 90 | 100.0% (40/40) | 63.5% (40/63) | 100.0% (27/27) |
| itaccess | 90 | 100.0% (38/38) | 60.3% (38/63) | 100.0% (27/27) |
| retail | 80 | 100.0% (35/35) | 66.0% (35/53) | 100.0% (27/27) |
| support | 80 | 100.0% (23/23) | 42.6% (23/54) | 100.0% (26/26) |
| clinic | 80 | 100.0% (18/18) | 35.3% (18/51) | 100.0% (29/29) |

Tập dev giữ riêng theo họ câu gồm câu nháp giả lập và câu lạc đề từ MASSIVE.

| Nhiệm vụ | Câu | Đúng trên đề xuất | Độ phủ | Từ chối đúng |
| --- | --- | --- | --- | --- |
| onestop | 128 | 97.0% (32/33) | 56.1% (32/57) | 98.6% (70/71) |
| itaccess | 128 | 90.0% (36/40) | 63.2% (36/57) | 94.4% (67/71) |
| retail | 109 | 96.4% (27/28) | 71.1% (27/38) | 98.6% (70/71) |
| support | 128 | 91.7% (33/36) | 57.9% (33/57) | 95.8% (68/71) |
| clinic | 128 | 100.0% (26/26) | 45.6% (26/57) | 100.0% (71/71) |

Dùng `python3 tools/classifier/evaluate.py --task <task>` để in ma trận nhầm lẫn trên dev, số từ chối theo loại ca, kết quả theo văn phong, độ trễ và hai đối chiếu cùng độ phủ.
Câu quá ngắn, cách nói chưa gặp, lỗi gõ nặng và nhiều yêu cầu trong cùng hồ sơ có thể làm giảm độ phủ hoặc gây gợi ý sai.
Theo định nghĩa nhãn, ghi chú phòng khám chỉ nêu triệu chứng được từ chối.
Độ trễ báo cáo là của tiến trình Python chạy riêng trên CPU.

## Kết quả nghiệm thu

Đội viết tay 450 câu nghiệm thu, duyệt chéo và khóa bộ câu trước khi chỉnh mô hình.
Bộ nghiệm thu được đo một lần cho mỗi phiên bản trọng số.
Bảng dưới ghi kết quả của các tệp trọng số đang có trong `model/`.
Các nhiệm vụ đạt điều kiện phát hành được bật trong mẫu tương ứng.

| Nhiệm vụ | Đúng trên đề xuất | Độ phủ | Từ chối đúng | Dò từ khóa cùng độ phủ | Bật |
|---|---|---|---|---|---|
| onestop | 100% (46/46) | 74,2% (46/62) | 100% (28/28) | 26,5% | Có |
| support | 100% (42/42) | 67,7% (42/62) | 100% (28/28) | 19,4% | Có |
| clinic | 100% (35/35) | 56,5% (35/62) | 100% (28/28) | 25,5% | Có |
| itaccess | 93,3% (42/45) | 67,7% (42/62) | 89,3% (25/28) | 18,8% | Không |
| retail | 87,8% (43/49) | 71,0% (44/62) | 82,1% (23/28) | 37,5% | Không |

## Giấy phép

Mã và trọng số theo Apache-2.0.
Dữ liệu đội biên soạn theo CC-BY-4.0, ghi công HaUI-ImplSolution.
Câu lạc đề nguồn ngoài lấy từ MASSIVE vi-VN 1.0 của Amazon.com Inc. hoặc các đơn vị liên kết theo CC-BY-4.0.
Thông tin ghi công và phiên bản nguồn nằm trong [manifest dữ liệu](data/sources.json).
