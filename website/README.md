# DX-OS Docs

Trang tài liệu của DX-OS, nằm trong thư mục `website/` của repository này, dựng bằng [Docusaurus](https://docusaurus.io).
Địa chỉ: https://haui-implsolution.github.io/DX-OS/

## Chạy trên máy

```bash
npm ci
npm start          # http://localhost:3000/DX-OS/
npm run build      # ra build/, báo lỗi khi có link hỏng
```

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `docs/` | Tài liệu Markdown; thứ tự và nhóm ở `sidebars.js` |
| `docs/images/` | Ảnh dùng trong tài liệu |
| `docs/templates/` | Mẫu tài liệu theo phase |
| `docs/tai-lieu/` | Tài liệu sản phẩm do `scripts/sync-docs.mjs` sinh, không commit |
| `scripts/sync-docs.mjs` | Chép tài liệu sản phẩm từ `docs/` và `deploy/` trước mỗi lần dựng |
| `src/pages/index.js` | Trang chủ |

Thêm một tài liệu: tạo tệp `.md` trong `docs/`, rồi thêm id của nó vào `sidebars.js`.
Tài liệu sản phẩm (`docs/MODEL.md`, `docs/OPERATIONS.md` và các tệp khác ở danh sách đầu `scripts/sync-docs.mjs`) viết ở `docs/` và `deploy/` của repository; `npm run build` và `npm start` chép chúng vào `docs/tai-lieu/` (không commit), đổi liên kết giữa các tệp thành liên kết trong trang và liên kết sang các tệp khác thành liên kết GitHub.
Thêm một tài liệu sản phẩm: thêm dòng vào danh sách đó và id `tai-lieu/<trang>` vào `sidebars.js`.
Workflow `Docs` dựng trang trên mọi pull request đổi `website/` (liên kết hoặc anchor hỏng thì đỏ) và đăng lên GitHub Pages khi đẩy vào `main`.

## Giấy phép

Apache-2.0, xem [`LICENSE`](../LICENSE) ở thư mục gốc.
