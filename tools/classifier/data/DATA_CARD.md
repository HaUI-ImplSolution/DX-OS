<!-- SPDX-FileCopyrightText: 2026 HaUI-ImplSolution -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Thẻ dữ liệu

Dữ liệu huấn luyện và đo của năm bộ phân loại hồ sơ tiếng Việt trong trợ lý nghiệp vụ.
Các câu mô tả tình huống giả lập và không chứa hồ sơ của người dùng.
Đội DX-OS công bố bộ dữ liệu ở [Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data) qua tài khoản `hittungtran` của thành viên Tùng.
Phiên bản 2 có đủ 2.490 câu gốc cùng tập chỉnh và bộ nghiệm thu theo CC BY 4.0 trong phạm vi đội có quyền.
Dữ liệu MASSIVE được tải riêng khi tái tạo trọng số.

## Nguồn gốc và giấy phép

| Phần | Nơi lưu | Số dòng | Người viết | Giấy phép |
| --- | --- | --- | --- | --- |
| Câu gốc huấn luyện | `data/seeds/` | 2.490 | Claude (Anthropic) | CC BY 4.0, trong phạm vi đội có quyền |
| Câu từ chối dùng chung | `data/negatives.jsonl` | 22 | Claude (Anthropic) | như trên |
| Tập chỉnh | `eval/tuning/` | 420 | Claude (Anthropic), chưa có người duyệt | như trên |
| Bộ nghiệm thu | `eval/acceptance/` | 450 | Thành viên đội viết tay, duyệt chéo | CC BY 4.0, ghi công HaUI-ImplSolution |
| Định nghĩa nhãn | `labels.json` | 5 nhiệm vụ | Đội | CC BY 4.0, ghi công HaUI-ImplSolution |
| Câu lạc đề ngoài | MASSIVE vi-VN 1.0 | 300 câu lấy mẫu | Amazon | CC BY 4.0, tải lúc huấn luyện, không nằm trong repository |

Biến thể do mã của đội sinh từ câu gốc trong tập huấn luyện.
Cột `nguoi_viet` của các tệp CSV ghi người viết từng dòng; các tệp câu gốc không ghi người viết hay người duyệt.
Codex của OpenAI không tạo dữ liệu nào của bộ phân loại.

### Điều khoản của Anthropic

Câu do Claude soạn qua tài khoản Claude Pro, chịu Điều khoản dịch vụ dành cho người dùng cá nhân của Anthropic (hiệu lực 8/10/2025, đọc ngày 26/11/2026).
Điều khoản chuyển cho người dùng quyền đối với kết quả, nếu có, và cấm dùng dịch vụ để huấn luyện mô hình cạnh tranh với Anthropic.
Bài hướng dẫn của Anthropic ([Can I use my Outputs to train an AI model?](https://support.claude.com/en/articles/12326764-can-i-use-my-outputs-to-train-an-ai-model), cập nhật 16/3/2026, đọc ngày 26/11/2026) cho phép dùng kết quả để huấn luyện mô hình chuyên biệt không cạnh tranh, như bộ phân loại và hệ thống phân loại nội dung.
Bài này cấm chatbot đa dụng, mô hình sinh văn bản mở, dùng kết quả làm training target và hỗ trợ bên thứ ba làm các việc đó.

Mô hình huấn luyện từ dữ liệu này là bộ phân loại hồi quy logistic cho năm nhiệm vụ nhãn cố định.
Nó không sinh văn bản, không trả lời tự do và không dùng làm chatbot hay mô hình đa dụng.
Nó chỉ gợi ý nhãn cho một trường của hồ sơ trong trợ lý nghiệp vụ của DX-OS, và người dùng xác nhận trước khi lưu.
Nhãn là nhãn nghiệp vụ theo [định nghĩa nhãn](../labels.json), không phải dự đoán của Claude.

Phần do Claude soạn được công bố công khai theo CC-BY-4.0, giấy phép này không cho đội thêm hạn chế nào.
Điều khoản của Anthropic cấm dùng kết quả của Claude để huấn luyện chatbot đa dụng hoặc mô hình sinh văn bản, và cấm hỗ trợ bên thứ ba làm việc đó.
Vì bộ dữ liệu công khai, một bên thứ ba có thể tải phần này về để huấn luyện mô hình như vậy, và đội không kiểm soát được việc đó.
Đội đề nghị chỉ dùng phần do Claude soạn để huấn luyện bộ phân loại hoặc mô hình chuyên biệt không cạnh tranh; đề nghị này không phải điều kiện của giấy phép.
Người dùng lại dữ liệu tự chịu trách nhiệm tuân thủ điều khoản của Anthropic đối với phần do Claude soạn.

### Bản quyền

Văn bản không có tác giả là con người có thể không được bảo hộ bản quyền ở nhiều nước.
HaUI-ImplSolution phát hành các câu do Claude soạn theo CC BY 4.0 trong phạm vi mình có quyền và không khẳng định bản quyền đối với từng câu.
Phần quyền của đội gồm nhãn, định nghĩa nhãn, cách chọn và sắp xếp dữ liệu, mã sinh biến thể, và bộ nghiệm thu do thành viên đội viết.

### Dữ liệu ngoài

Câu lạc đề lấy từ [MASSIVE](https://arxiv.org/abs/2204.08582) 1.0 của Amazon (CC BY 4.0), tập train `vi-VN` có 11.514 câu.
MASSIVE được bản địa hóa từ [SLURP](https://github.com/pswietojanski/slurp) và chứa một phần chữ của SLURP (CC BY 4.0; Bastianelli, Vanzo, Swietojanski và Rieser, 2020, [bài báo](https://aclanthology.org/2020.emnlp-main.588/)).
Phần âm thanh của SLURP theo CC BY-NC 4.0 không nằm trong MASSIVE và không được tải hay dùng.
Công cụ lấy mẫu theo seed từ trường `utt` thuộc các ý định đồng hồ báo thức, nấu ăn, truyện cười, tiền tệ và phép tính, sau khi lọc theo [manifest](sources.json) và khử trùng sau chuẩn hóa.
Mã SHA-256 và URL của bản phát hành gốc nằm trong manifest.
Mỗi câu lấy mẫu được gán `TU_CHOI` với loại ca `lac_de` cho cả năm nhiệm vụ.
Văn bản được giữ nguyên, lưu trong cache và không đóng gói.

## Thành phần

| Nhiệm vụ | Nhãn nghiệp vụ | Câu mỗi nhãn | Câu từ chối trong tệp nhiệm vụ | Tổng |
| --- | --- | --- | --- | --- |
| `onestop` | Giấy xác nhận, Phúc khảo, Hoãn thi | 135 | 120 | 525 |
| `itaccess` | GIT_USERS, VPN_USERS, WIKI_EDITORS | 135 | 120 | 525 |
| `retail` | Đổi hàng, Trả hàng | 135 | 120 | 390 |
| `support` | Thấp, Trung bình, Cao | 135 | 120 | 525 |
| `clinic` | Khám tổng quát, Tư vấn, Tái khám | 135 | 120 | 525 |

Bộ câu gốc có 2.490 dòng, gồm 1.890 câu nghiệp vụ và 600 câu từ chối chia đều giữa bốn loại ca của từng nhiệm vụ.
Tệp `negatives.jsonl` có 17 câu dùng chung và năm câu lạc đề bổ sung, mỗi nhiệm vụ có một câu riêng.
Văn phong lịch sự, bình thường và cẩu thả chiếm lần lượt khoảng 30%, 40% và 30% trong mỗi tệp câu gốc.
Mỗi nhãn nghiệp vụ có 15 câu ngắn nói thẳng yêu cầu bằng tên nhãn hoặc từ gần nghĩa, chia đều cho ba văn phong.
Tập chỉnh dùng để chọn ngưỡng và siêu tham số; bộ nghiệm thu được giữ riêng để đo.

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
