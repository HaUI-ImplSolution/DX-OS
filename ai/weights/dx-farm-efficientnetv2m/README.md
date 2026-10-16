# DX-Farm EfficientNetV2-M

Checkpoint do người huấn luyện cung cấp, fine-tune từ `EfficientNet_V2_M_Weights` của Torchvision trên RiceLeafDiseaseBD v3.
Sáu lớp theo [class contract](../../../data/structured/class_contract.json), ảnh 480×480; số liệu người huấn luyện ở [AI_EVALUATION.md](../../../docs/AI_EVALUATION.md).
Ba phần checkpoint giữ nguyên byte; thứ tự và kích thước ở [parts.json](./parts.json).
[assemble_cv_weights.py](../../scripts/assemble_cv_weights.py) ghép file trong Docker build hoặc khi phát triển local; không tải trọng số và không in checksum.
Checksum được tính cục bộ, chỉ lưu trong env riêng.
Phần fine-tune của nhóm theo [Apache-2.0](../../../LICENSE); điều khoản trọng số pretrained và dữ liệu theo [LICENSES.md](../../../LICENSES.md).
