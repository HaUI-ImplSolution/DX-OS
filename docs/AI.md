# Trí tuệ nhân tạo trong DX-OS

Trợ lý gợi ý loại hồ sơ từ nội dung tiếng Việt để cán bộ rà soát nhiều hồ sơ trong Hộp việc.
Ví dụ, khi nội dung nói «xin xem lại bài thi môn CSDL», trợ lý có thể đề xuất giá trị **Phúc khảo** cho trường **Loại hồ sơ** và chỉ ra cụm từ làm căn cứ.
Cán bộ xem đề xuất rồi quyết định xác nhận hoặc tiếp tục xử lý thủ công.

## Vì sao dùng bộ phân loại

Bài toán có tập nhãn hữu hạn do tệp mô hình nghiệp vụ khai báo.
DX-OS dùng hồi quy logistic đa lớp trên cụm ký tự và từ để chọn một nhãn hoặc từ chối, chạy trực tiếp trên CPU trong dịch vụ trợ lý.
Ba tệp trọng số đi kèm image cộng lại khoảng 865 kB, chạy với Node.js và không cần API đám mây, GPU hay máy chủ mô hình riêng.
Cùng một cơ chế dùng cho nhiều nghiệp vụ qua `ai.tasks`; mỗi nghiệp vụ mới cần tập nhãn, dữ liệu và trọng số phù hợp.
[Xem quyết định kiến trúc QĐ-9](../ARCHITECTURE.md) · [Thêm bộ phân loại cho nghiệp vụ mới](../tools/classifier/README.md#thêm-bộ-phân-loại-cho-nghiệp-vụ-mới).

## Luồng xử lý và an toàn

| Bước | Cơ chế | Nơi kiểm tra |
| --- | --- | --- |
| Khai báo | Tệp mô hình chọn trường văn bản làm đầu vào và một trường lựa chọn đơn trị làm đầu ra. | [Đặc tả mô hình](./MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ) · [Bộ kiểm khai báo](../backend/dx-model/src/main/java/HaUI/ImplSolution/dxos/model/ModelParser.java) |
| Rà soát | Trợ lý đọc hồ sơ bằng phiên Core của người dùng. Bộ phân loại bỏ qua ca dưới ngưỡng hoặc chứa dấu hiệu chèn lệnh. Mỗi đề xuất đưa ra một giá trị và cụm từ làm căn cứ. | [Mã bộ phân loại](../assistant/src/classifier/index.ts) · [Luồng rà Hộp việc](../assistant/README.md#rà-loại-hồ-sơ-ở-hộp-việc) |
| Xác nhận | Người dùng xác nhận đề xuất. Trợ lý kiểm lại quyền và trạng thái hồ sơ trước khi ghi qua API Core. Trường dùng để cấp nhóm quyền bị chặn ở bước kiểm khai báo mô hình. | [Mã kiểm và ghi đề xuất](../assistant/src/tools/fields.ts) · [Ví dụ API](./API.md#api-trợ-lý-nghiệp-vụ) |

Trợ lý chạy trong mạng nội bộ, không có đường ra Internet.
Trong bản cài full, có thể dừng `assistant-control` và tiếp tục nhập, sửa và xử lý hồ sơ trên giao diện chuẩn.
Để chỉ chạy Tầng 1, dùng `dxos start --core`; chế độ này phục vụ API lõi.
[Cấu hình trợ lý](../assistant/README.md#chạy-cấu-hình-và-tắt) · [Chế độ chỉ lõi](./OPERATIONS.md#chế-độ-chỉ-lõi).

## Dữ liệu và giấy phép

- **Huấn luyện** dùng 2.490 câu gốc và 22 câu từ chối do Claude soạn, cùng 300 câu lạc đề lấy mẫu từ MASSIVE vi-VN.
- **Chọn ngưỡng** dùng 420 câu giả lập do Claude soạn. **Nghiệm thu** dùng 450 câu riêng do thành viên đội viết tay và duyệt chéo.
- **Công bố** mã và trọng số theo Apache-2.0. Dữ liệu của đội ghi CC BY 4.0 trong phạm vi đội có quyền. MASSIVE vi-VN 1.0 theo CC BY 4.0, có ghi công nguồn. Phạm vi tái sử dụng phần do Claude soạn theo điều khoản Anthropic còn cần làm rõ, nên chưa thể kết luận toàn bộ chuỗi dữ liệu đạt OSAID.

[Thẻ dữ liệu](../tools/classifier/data/DATA_CARD.md) · [Bộ dữ liệu trên Kaggle](https://www.kaggle.com/datasets/hittungtran/dx-os-vietnamese-record-classification-data) · [Danh mục giấy phép và SHA-256 của trọng số](../DEPENDENCIES.txt) · [Vấn đề đang theo dõi](./dynamic-core/BACKLOG.md#32-chưa-có-issue).

## Kết quả nghiệm thu

Mỗi nghiệp vụ có 62 câu đúng phạm vi và 28 câu cần từ chối.
Điều kiện phát hành gồm ít nhất 95% đúng trong số đề xuất, ít nhất 50% đề xuất trên câu đúng phạm vi và từ chối đủ 28 ca khó.
Cột dò từ khóa dùng tên nhãn và được đo ở cùng độ phủ để đối chiếu cách làm đơn giản hơn.

| Nghiệp vụ | Đúng trên đề xuất | Độ phủ | Từ chối đúng | Dò từ khóa đúng trên đề xuất | Trạng thái |
| --- | ---: | ---: | ---: | ---: | --- |
| Một cửa (`onestop`) | 39/39 | 39/62 | 28/28 | 27,6% | Có trong bản cài |
| Hỗ trợ (`support`) | 32/32 | 32/62 | 28/28 | 20,4% | Có trong bản cài |
| Hẹn khám (`clinic`) | 47/47 | 47/62 | 28/28 | 23,6% | Có trong bản cài |
| Cấp quyền CNTT (`itaccess`) | 44/44 | 44/62 | 28/28 | 19,4% | Đạt số đo, trường đích cấp quyền nên không bật |
| Bán lẻ (`retail`) | 36/39 | 39/62 | 25/28 | 35,2% | Chưa đạt điều kiện phát hành |

Ba bộ phân loại đi kèm bản cài đề xuất đúng toàn bộ ca đã đề xuất trên tập này, với độ phủ từ 51,6% đến 75,8%.
Câu quá ngắn, cách nói mới, lỗi gõ nặng hoặc nhiều yêu cầu trong cùng hồ sơ vẫn có thể bị bỏ qua hoặc phân loại sai.
Các phép đo dùng ca viết cho dự án; hiệu quả trên hồ sơ thực tế và thời gian cán bộ tiết kiệm được chưa được đo.

Đo lại ngày 26/11/2026 trên Intel Core i5-10300H, Linux x86-64, Python 3.12.3 cho kết quả nghiệm thu như bảng.
Trên ba bộ đi kèm, độ trễ p95 của một câu là 0,866–1,276 ms, của lô 25 câu là 16,137–66,056 ms và RAM đỉnh của tiến trình đo là 22,4–22,5 MiB.
Phép đo chạy ngoại tuyến, chỉ đo bộ phân loại Python; độ trễ API và tài nguyên toàn dịch vụ cần đo trên hệ thống triển khai.
[Thẻ mô hình và bảng đo đầy đủ](../tools/classifier/MODEL_CARD.md).

## Đo lại

Từ gốc kho mã, dùng Python 3 và các trọng số đã lưu trong kho.

```bash
for task in onestop support clinic itaccess retail; do
  python3 tools/classifier/evaluate.py --task "$task" \
    --input "tools/classifier/eval/acceptance/$task.csv" || exit
done
```

Báo cáo in độ đúng, độ phủ, số từ chối, ma trận nhầm lẫn, số đo theo kiểu câu, so sánh ở cùng độ phủ và độ trễ.
[Xem cách huấn luyện, đo tập dev và đo dữ liệu tự chuẩn bị](../tools/classifier/README.md).
