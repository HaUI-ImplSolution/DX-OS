# Kho tệp mô hình trong git

Ví dụ này cho thấy tệp mô hình sống trong một kho git riêng, qua ba bước: sửa trên nhánh, xem kế hoạch trên pull request, và để DX-OS tự kéo về khi đã gộp.

## Cấu trúc kho

- `models/*.yaml`: mỗi tệp là một mô hình; mẫu ở [`models/tasks.yaml`](./models/tasks.yaml).
- `.github/workflows/model-plan.yml`: chép từ [`workflows/model-plan.yml`](./workflows/model-plan.yml).
- `.github/workflows/model-lint.yml`: chép từ [`workflows/model-lint.yml`](./workflows/model-lint.yml).

## Kiểm tệp trên pull request

Workflow `model-lint.yml` chạy trên runner của GitHub, không cần máy chạy DX-OS và không cần bí mật nào.
Nó tải `dxos-lint.jar` của một bản phát hành DX-OS, kiểm SHA-256 của tệp rồi đọc mọi tệp trong `models/` bằng bộ đọc của backend.
Một tệp có lỗi làm job đỏ, và mỗi lỗi in thành một dòng `tệp:dòng:cột: nội dung`.
Đặt `DXOS_VERSION` trong workflow thành tag của bản phát hành bạn dùng.

## Kế hoạch trên pull request

Workflow chạy trên một runner tự host đặt ở máy chạy DX-OS, gắn nhãn `dxos` và có lệnh `dxos` trong `PATH`.
Mã trong pull request chạy ngay trên máy đó, nơi `deploy/.env` giữ bí mật ADMIN của `dxos-cli`.
Vì vậy chỉ dùng cho kho riêng, và workflow bỏ qua pull request từ fork.
Với từng tệp trong `models/` nó chạy `dxos plan --check` rồi dán kế hoạch vào bình luận của pull request.
Mã thoát của `dxos plan --check` là 0 khi mô hình đang chạy giống tệp, 2 khi kế hoạch có thay đổi và 1 khi kế hoạch bị từ chối; mã 1 làm job đỏ.

## Áp dụng khi đã gộp

Trên máy chạy DX-OS, đặt trong `deploy/.env`:

```
GITOPS_REPO=ssh://git@example.org/team/models.git
GITOPS_REF=main
GITOPS_PATH=models
GITOPS_MODE=auto
GITOPS_SSH_KEY=/home/deploy/.dxos/deploy-key
GITOPS_KNOWN_HOSTS=/home/deploy/.dxos/known_hosts
```

Rồi chạy `dxos gitops install`.
Cứ 5 phút `dxos gitops sync` kéo `main`, so từng tệp với mô hình đang chạy và áp dụng kế hoạch mức `SAFE`.
Kế hoạch mức cao hơn nằm chờ: `dxos gitops status` thoát với mã 2 và `dxos gitops approve <commit> --accept MIGRATION` duyệt nó.
Lịch sử các commit đã xét nằm ở `deploy/gitops/history.log`.
Đổi `GITOPS_MODE` thành `report` nếu chỉ muốn báo lệch, hoặc `approve` nếu mọi thay đổi đều phải duyệt.

Cách cấu hình đầy đủ, kể cả bắt buộc chữ ký commit, ở [`deploy/OPERATIONS.md`](../../deploy/OPERATIONS.md).
