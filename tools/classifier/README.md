<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Bộ phân loại hồ sơ tiếng Việt

Công cụ huấn luyện, suy luận và đo năm nhiệm vụ phân loại bằng Python 3.11 trở lên với thư viện chuẩn.
Tệp [định nghĩa nhãn](labels.json) ghi thực thể, trường và tập giá trị lấy từ mẫu DX-OS.

| Nhiệm vụ | Mẫu | Thực thể | Đầu vào | Đầu ra |
| --- | --- | --- | --- | --- |
| `onestop` | `onestop.yaml` | `request` | `reason` | `kind` |
| `itaccess` | `itaccess.yaml` | `access_request` | `reason` | `access_group` |
| `retail` | `retail_return.yaml` | `return_request` | `reason` | `kind` |
| `support` | `support_desk.yaml` | `ticket` | `title`, `detail` | `priority` |
| `clinic` | `clinic_appointment.yaml` | `appointment` | `note` | `service` |

## Tái tạo

Chạy từ thư mục gốc của repo với CPython 3.12.3 trên Linux x86-64 để tái tạo trọng số từng byte.
Lưu SHA-256 trước khi huấn luyện rồi kiểm lại sau khi chạy.

```sh
weights_sha=$(mktemp)
sha256sum tools/classifier/model/*.json > "$weights_sha"
python3 tools/classifier/fetch.py && python3 tools/classifier/train.py && sha256sum -c "$weights_sha"
```

`fetch.py` kiểm SHA-256 của MASSIVE theo [manifest nguồn](data/sources.json) trước khi sử dụng tệp trong `~/.cache/dxos-classifier/`.
`train.py` ghi năm tệp trọng số và hỗ trợ `--task` khi cần huấn luyện một nhiệm vụ.
Mặc định, công cụ đọc `tools/classifier/eval/tuning/<task>.csv`.
Có thể truyền thư mục chứa các CSV qua `--tune`.
Tập chỉnh gồm 420 câu do Claude viết, chưa có người duyệt, và chỉ dùng để chọn ngưỡng và siêu tham số.
Trọng số ghi đường dẫn CSV tính từ thư mục gốc repo ở `tuning_source` và SHA-256 của toàn bộ CSV ở `tuning_sha256`.
Bộ chọn tối đa hóa độ phủ trên tập chỉnh, với ít nhất 90% đúng trên đề xuất và ít nhất 97% từ chối đúng trong từng loại `lac_de`, `mo_ho`, `hai_y` và `chen_lenh`.
Bộ nghiệm thu do đội viết tay nằm ở `tools/classifier/eval/acceptance/`. [Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data) là nơi công bố cùng dữ liệu do đội biên soạn, gồm tập chỉnh và bộ nghiệm thu.

## Đo dev và nghiệm thu

Thay `<task>` bằng tên nhiệm vụ cần đo.

```sh
python3 tools/classifier/evaluate.py --task <task>
python3 tools/classifier/evaluate.py --task <task> --input tools/classifier/eval/acceptance/<task>.csv
```

## Đo tập được cung cấp

```sh
python3 tools/classifier/evaluate.py --task support --input /tmp/ho-so.csv
python3 tools/classifier/evaluate.py --task clinic --input /tmp/ghi-chu.jsonl --output /tmp/ket-qua.md
```

CSV UTF-8 dùng các cột `id`, `tieu_de` cho hỗ trợ kỹ thuật, `van_ban`, `nhan`, `loai_ca` và `van_phong`.
JSONL nhận cùng tên cột CSV hoặc các khóa `id`, `title`, `text`, `label`, `case` và `style`.
Các cột bổ sung được bỏ qua.
Dùng `--output` để lưu báo cáo vào đường dẫn chỉ định.
Lệnh có `--input` chỉ cần trọng số và tệp đầu vào được chỉ định.

Độ chính xác trên câu có đề xuất tính cả đề xuất nhầm cho câu cần từ chối.
Độ phủ là tỉ lệ có đề xuất trên các câu đúng phạm vi.
Hai phương pháp đối chiếu dừng khi số câu đúng phạm vi có đề xuất bằng mô hình.
Dò từ khóa xếp câu theo số lần khớp nguyên cụm tên nhãn sau khi chuẩn hóa và bỏ dấu, rồi dùng nhãn phổ biến khi không khớp.
Phương pháp luôn chọn nhãn phổ biến dùng nhãn đúng phạm vi có nhiều câu gốc nhất trong tập huấn luyện.
Thứ tự nhãn trong `labels.json` phân định trường hợp đồng hạng về số câu huấn luyện.
Bộ đo dùng hàm băm ID với seed cố định để phân định khi điểm xếp câu bằng nhau.
Độ trễ gồm tiền xử lý, suy luận và sinh căn cứ trên một tiến trình Python đã làm nóng.

## Suy luận

```sh
PYTHONPATH=tools/classifier python3 - <<'PY'
import json
from pathlib import Path
from features import predict

model = json.loads(Path("tools/classifier/model/clinic.json").read_text(encoding="utf-8"))
print(predict(model, {"text": "Tôi muốn đặt lịch khám sức khỏe định kỳ vào thứ bảy."}))
PY
```

Hàm `predict` nhận phần văn bản ở `text` và thêm `title` cho nhiệm vụ `support`.
Trước khi mô hình phân loại, `features.py` tách văn bản thành các đoạn câu và lọc trên bản chuyển chữ thường và bỏ dấu.
Bộ lọc nhận lệnh rõ ràng như “Bỏ qua mọi hướng dẫn” hoặc “Ignore previous instructions”.
Các mẫu đầu đoạn “Chọn”, “Output” và mẫu “Hệ thống”, “Chương trình” hoặc “Máy” yêu cầu tên nhãn đã chuẩn hóa từ trọng số ngay sau từ chỉ thao tác, có thể kèm “mức” hoặc “nhãn”.
Cụm “luôn chọn” chỉ khớp khi đứng đầu đoạn, đi sau “hãy” hoặc đi trước tên nhãn.
`output` và `ignore` đứng một mình được giữ lại.
Đoạn khớp lệnh được bỏ khỏi đầu vào trích đặc trưng và căn cứ.
Mô hình suy luận từ các đoạn còn lại và có thể từ chối khi thiếu căn cứ.
Kết quả gồm `label`, `confidence` và `evidence`.
Mỗi đề xuất ưu tiên tối đa ba cụm không chồng lấn, mỗi cụm gồm hai hoặc ba từ liền nhau, có đóng góp dương lớn nhất so với nhãn đứng thứ hai và có ít nhất một từ ngoài danh sách từ đệm.
`TU_CHOI` có danh sách căn cứ rỗng.
Trường dài hơn 2.000 ký tự sau chuẩn hóa NFC làm lượt suy luận trả `TU_CHOI`.
Theo định nghĩa nhãn phòng khám, cần nêu rõ dịch vụ muốn đặt và ghi chú chỉ nêu triệu chứng thuộc lớp từ chối.
Các giới hạn của dữ liệu giả lập và kết quả dev nằm trong [thẻ mô hình](MODEL_CARD.md).
