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
| `src/pages/index.js` | Trang chủ |

Thêm một tài liệu: tạo tệp `.md` trong `docs/`, rồi thêm id của nó vào `sidebars.js`.

## Giấy phép

Apache-2.0, xem [`LICENSE`](../LICENSE) ở thư mục gốc.
