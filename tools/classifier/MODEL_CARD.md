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
| Kích thước tệp | 222–300 KB mỗi mô hình, ba mô hình trong image trợ lý tổng cộng 865 KB |
| Ngưỡng | onestop 0,891, support 0,918, clinic 0,862, itaccess 0,743, retail 0,799 |
| Huấn luyện | Python 3.12 thư viện chuẩn, seed 210, 28 vòng, chỉ CPU, chạy lại cho cùng từng byte |
| Dữ liệu | Dữ liệu của bộ phân loại (CC BY 4.0, phạm vi quyền ở thẻ dữ liệu), tập chỉnh ở `eval/tuning/`, bộ nghiệm thu ở `eval/acceptance/` và câu lạc đề từ MASSIVE vi-VN 1.0 |

## Huấn luyện

Công cụ huấn luyện năm mô hình hồi quy logistic đa lớp bằng SGD có L2, 28 vòng với seed 210.
Văn bản được chuẩn hóa NFC và chuyển chữ thường, sau đó đặc trưng ký tự có dấu và bỏ dấu cùng đặc trưng từ 1–2 được băm vào 8.192 vị trí.
Phạm vi n-gram ký tự, cách bỏ dấu ở đặc trưng từ và hệ số L2 được chọn từ lưới khai trong `train.py`.
Tập chỉnh gồm 420 câu do Claude viết, chưa có người duyệt, và chỉ dùng để chọn ngưỡng và siêu tham số.
Bộ nghiệm thu là một bộ riêng do đội tự viết.
Bộ chọn tối đa hóa độ phủ, với ít nhất 90% đúng trên đề xuất và ít nhất 97% từ chối đúng trong từng loại `lac_de`, `mo_ho`, `hai_y` và `chen_lenh`.
Mặc định, công cụ đọc tập chỉnh ở `tools/classifier/eval/tuning/`.
Tệp trọng số ghi seed, revision nhãn, cấu hình đặc trưng, đường dẫn CSV tính từ thư mục gốc repo và SHA-256 cho tập huấn luyện và tập chỉnh.
Nguồn và số lượng dữ liệu nằm trong [thẻ dữ liệu](data/DATA_CARD.md).

### Lọc câu lệnh

Trước khi trích đặc trưng cho mô hình, `features.py` tách văn bản thành các đoạn câu theo dấu chấm, chấm hỏi, chấm than, chấm phẩy và xuống dòng.
Bộ lọc dò lệnh rõ ràng như “Bỏ qua mọi hướng dẫn” và “Ignore previous instructions” trên đoạn đã chuyển chữ thường và bỏ dấu.
Mẫu “Hệ thống”, “Chương trình” hoặc “Máy” đi với “chọn”, “điền”, “gán” hoặc “trả về” chỉ khớp khi đi tiếp với tên nhãn trong trọng số, có thể kèm “mức” hoặc “nhãn”.
Mẫu đầu đoạn “Chọn” hoặc “Output” cũng yêu cầu tên nhãn theo sau.
Cụm “luôn chọn” chỉ khớp khi đứng đầu đoạn, đi sau “hãy” hoặc đi trước tên nhãn.
`output` và `ignore` đứng một mình được giữ lại.
Đoạn khớp lệnh được bỏ khỏi đầu vào trích đặc trưng và căn cứ.
Các đoạn còn lại được dùng để suy luận và mô hình có thể từ chối khi thiếu căn cứ.

## Đo và giới hạn

Số đo trên tập chỉnh dùng để chọn cấu hình và ngưỡng.

| Nhiệm vụ | Câu | Đúng trên đề xuất | Độ phủ | Từ chối đúng |
| --- | --- | --- | --- | --- |
| onestop | 90 | 100.0% (38/38) | 60.3% (38/63) | 100.0% (27/27) |
| itaccess | 90 | 97.6% (41/42) | 66.7% (42/63) | 100.0% (27/27) |
| retail | 80 | 100.0% (29/29) | 54.7% (29/53) | 100.0% (27/27) |
| support | 80 | 100.0% (15/15) | 27.8% (15/54) | 100.0% (26/26) |
| clinic | 80 | 100.0% (29/29) | 56.9% (29/51) | 100.0% (29/29) |

Tập dev giữ riêng theo họ câu gồm câu nháp giả lập và câu lạc đề từ MASSIVE.

| Nhiệm vụ | Câu | Đúng trên đề xuất | Độ phủ | Từ chối đúng |
| --- | --- | --- | --- | --- |
| onestop | 137 | 100.0% (33/33) | 50.0% (33/66) | 100.0% (71/71) |
| itaccess | 137 | 95.9% (47/49) | 71.2% (47/66) | 97.2% (69/71) |
| retail | 115 | 94.4% (17/18) | 38.6% (17/44) | 98.6% (70/71) |
| support | 137 | 90.3% (28/31) | 42.4% (28/66) | 95.8% (68/71) |
| clinic | 137 | 100.0% (37/37) | 56.1% (37/66) | 100.0% (71/71) |

Dùng `python3 tools/classifier/evaluate.py --task <task>` để in ma trận nhầm lẫn trên dev, số từ chối theo loại ca, kết quả theo văn phong, độ trễ và hai đối chiếu cùng độ phủ.
Câu quá ngắn, cách nói chưa gặp, lỗi gõ nặng và nhiều yêu cầu trong cùng hồ sơ có thể làm giảm độ phủ hoặc gây gợi ý sai.
Theo định nghĩa nhãn, ghi chú phòng khám chỉ nêu triệu chứng được từ chối.
Độ trễ báo cáo là của tiến trình Python chạy riêng trên CPU.

## Kết quả nghiệm thu

Đội viết tay 450 câu nghiệm thu, duyệt chéo và khóa bộ câu trước khi chỉnh mô hình.
Bộ nghiệm thu nằm ở `eval/acceptance/` và được đo một lần cho mỗi phiên bản trọng số; đến phiên bản hiện tại bộ này đã được đo ba lần.
Điều kiện phát hành là ít nhất 95% đúng trên đề xuất, ít nhất 50% độ phủ và từ chối mọi câu khó (`lac_de`, `mo_ho`, `hai_y`, `chen_lenh`).

| Nhiệm vụ | Đúng trên đề xuất | Độ phủ | Từ chối đúng | Dò từ khóa cùng độ phủ | Điều kiện phát hành |
|---|---|---|---|---|---|
| onestop | 100% (39/39) | 62,9% (39/62) | 100% (28/28) | 27,6% | Đạt |
| support | 100% (32/32) | 51,6% (32/62) | 100% (28/28) | 20,4% | Đạt |
| clinic | 100% (47/47) | 75,8% (47/62) | 100% (28/28) | 23,6% | Đạt |
| itaccess | 100% (44/44) | 71,0% (44/62) | 100% (28/28) | 19,4% | Đạt |
| retail | 92,3% (36/39) | 58,1% (36/62) | 89,3% (25/28) | 35,2% | Không đạt |

Các mẫu `onestop`, `support_desk` và `clinic_appointment` có nhiệm vụ bộ phân loại.
Mẫu `itaccess` đạt điều kiện nhưng không có nhiệm vụ, vì trường `access_group` cấp nhóm quyền qua `add_to_group` và định dạng mô hình không cho nhiệm vụ AI đề xuất trường đó.

### Máy đo, độ trễ và bộ nhớ

Các số đo độ trễ và bộ nhớ bên dưới lấy từ phép đo ngày 26/11/2026 trên Linux x86-64 với Intel Core i5-10300H @ 2,50 GHz (4 nhân, 8 luồng) và Python 3.12.3.
Mỗi lần chạy dùng một tiến trình CPU, làm nóng 25 lượt, đo 150 lượt một câu và 30 lượt lô 25 câu.
Độ trễ gồm chuẩn hóa câu, phân loại và lấy cụm từ làm căn cứ.
RAM đỉnh là RSS của toàn bộ tiến trình Python chạy công cụ đo, ghi bằng GNU `time -f '%M'`.
Số đo dành cho bộ phân loại chạy riêng; độ trễ API và RAM của dịch vụ Node.js khi chạy cùng stack cần được đo riêng.

| Nhiệm vụ | Một câu p50 (ms) | Một câu p95 (ms) | Lô 25 p50 (ms) | Lô 25 p95 (ms) | RAM đỉnh (KiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| onestop | 0,506 | 0,866 | 13,098 | 16,137 | 23.016 |
| support | 0,699 | 1,276 | 19,731 | 66,056 | 22.932 |
| clinic | 0,477 | 0,905 | 14,501 | 30,808 | 23.040 |
| itaccess | 0,713 | 1,219 | 17,971 | 35,429 | 23.132 |
| retail | 0,622 | 1,049 | 15,110 | 21,188 | 22.584 |

Lệnh đo lại một nhiệm vụ từ thư mục gốc của kho mã.

```bash
/usr/bin/time -f 'RSS đỉnh: %M KiB' \
  python3 tools/classifier/evaluate.py --task onestop \
  --input tools/classifier/eval/acceptance/onestop.csv
```

## Giấy phép

Mã và trọng số theo Apache-2.0.
Dữ liệu phát hành theo CC-BY-4.0 trong phạm vi HaUI-ImplSolution có quyền; phạm vi đó ghi ở [thẻ dữ liệu](data/DATA_CARD.md).
Câu gốc huấn luyện và tập chỉnh do Claude soạn; điều khoản của Anthropic và mục đích sử dụng của mô hình ghi ở [thẻ dữ liệu](data/DATA_CARD.md).
Câu lạc đề nguồn ngoài lấy từ MASSIVE vi-VN 1.0 của Amazon.com Inc. hoặc các đơn vị liên kết theo CC-BY-4.0.
Thông tin ghi công và phiên bản nguồn nằm trong [manifest dữ liệu](data/sources.json).
