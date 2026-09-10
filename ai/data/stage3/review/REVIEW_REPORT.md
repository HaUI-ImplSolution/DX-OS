# Stage 3 — Báo cáo rà soát kỹ thuật cho MVP

## Kết luận

Stage 3 không promote toàn bộ candidate.

Kết quả cuối:

- 4 Pesticide records được đưa vào final.
- 8 TreatmentRule records được đưa vào final.
- 21 DiseaseManagementPolicy records đảm bảo safe fallback cho các class còn lại.
- 8 TreatmentRule candidate tiếp tục giữ ở HOLD.

## Rule được promote

### Tomato___Late_blight
- Revus Opti 440SC
- Ortiva 600SC

Lý do:
- Danh mục Việt Nam có `sương mai/cà chua`.
- Nguồn kỹ thuật Việt Nam xác nhận Phytophthora infestans là tác nhân bệnh sương mai cà chua.
- Liều/PHI có nguồn nhà sản xuất.

### Potato___Early_blight
- Revus Opti 440SC

Lý do:
- Danh mục ghi `đốm vòng/khoai tây`.
- Alternaria solani được đối chiếu với bệnh đốm vòng trên khoai tây.

### Potato___Late_blight
- Revus Opti 440SC
- Amistar 250SC

Lý do:
- Danh mục ghi `mốc sương/khoai tây`.
- Phytophthora infestans được đối chiếu với bệnh mốc sương trên khoai tây.
- Dose/PHI được giữ đúng theo product source.

### Tomato___Early_blight
- Ortiva 600SC
- Amistar 250SC

Lý do:
- Danh mục ghi `đốm vòng/cà chua`.
- Alternaria solani được đối chiếu với bệnh đốm vòng cà chua.

### Corn_(maize)___Northern_Leaf_Blight
- Amistar Top 325SC

Lý do:
- Danh mục ghi `đốm lá lớn/ngô`.
- Nguồn học thuật Việt Nam xác nhận Exserohilum turcicum gây bệnh đốm lá lớn trên ngô.

## Candidate giữ HOLD

- Target Spot: `đốm lá` quá rộng.
- Common rust: `rỉ sắt/ngô` vẫn giữ review thay vì ép exact mapping.
- Amistar 250SC / tomato late blight: không dùng vì lịch sử source conflict và MVP đã có alternative sạch hơn.
- Tomato bacterial spot: thiếu PHI primary verified.
- Corn gray leaf spot: broad target + thiếu PHI.
- Tomato Leaf Mold: broad target.
- Tomato Septoria: broad target.
- Squash powdery mildew: crop mismatch + secondary quantitative source.

## Nguyên tắc review

Ưu tiên false-negative hơn false-positive:

> Nếu không chắc exact crop–disease–label mapping thì không promote TreatmentRule.

Điều này phù hợp với kiến trúc deterministic-first và anti-hallucination của DX-Farm.

## Giới hạn

Đây là source-grounded technical review phục vụ MVP/demo phần mềm.
Không thay thế kiểm tra nhãn sản phẩm hiện hành hoặc xác nhận của kỹ sư nông nghiệp
khi triển khai cho sử dụng ngoài thực địa.
