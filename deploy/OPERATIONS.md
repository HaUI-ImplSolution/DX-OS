# Vận hành

Tài liệu cho người cài và chạy stack hằng ngày: cài đặt và lệnh `dxos`, quản trị người dùng, kiểm tra trước khi chạy, tài nguyên, giám sát từ ngoài, sao lưu và khôi phục, quét mã độc.
Log, giới hạn tốc độ, sự kiện đăng nhập, audit và màn hình giám sát ở [`MONITORING.md`](./MONITORING.md).
Webhook sự kiện, API kéo sự kiện, lịch sử chống sửa và hồ dữ liệu ở [`INTEGRATIONS.md`](./INTEGRATIONS.md).
Kết nối công cụ báo cáo bằng vai PostgreSQL chỉ đọc ở [`REPORTING.md`](./REPORTING.md).

## Quản trị người dùng

Tab Người dùng của bảng điều khiển (`/console`, ADMIN) cho tìm người dùng, xem vai theo từng ứng dụng, gán và gỡ vai và đơn vị của các mô hình đang chạy, tạo người dùng, khóa và mở, đặt lại mật khẩu.
Mật khẩu tạm do hệ thống sinh, hiện một lần và người dùng phải đổi ở lần đăng nhập đầu.
Vai `ADMIN` của nền tảng không cấp được ở tab này; cấp ở Keycloak Admin Console (Users, Role mapping), mở theo [Keycloak Admin Console](#keycloak-admin-console).
Người dùng đăng nhập bằng nhà cung cấp ngoài, chính sách mật khẩu và MFA do Keycloak quản lý.
Backend cần tài khoản dịch vụ `dxos-backend-admin` có quyền client và scope `kc-admin-api`; `update.sh` gán scope này cho Keycloak dựng từ trước (xem [`sso/README.md`](../sso/README.md)).

## Ứng dụng ngoài

Một dịch vụ ngoài gọi API bằng token máy; đăng ký nó ở tab Ứng dụng ngoài của bảng điều khiển (`/console`, ADMIN) hoặc bằng `dxos client add <tên> --model <mô hình> --role <vai>`.
Mỗi ứng dụng là một client Keycloak `dxos-app-<tên>` dạng client credentials, giữ đúng một vai của một mô hình; vai `ADMIN` và các vai của nền tảng không cấp được.
Mã bí mật hiện một lần khi đăng ký hoặc đổi, hệ thống không giữ lại và không ghi vào nhật ký hay lịch sử.
Ứng dụng lấy token bằng `grant_type=client_credentials` ở `<địa chỉ>/realms/dxos/protocol/openid-connect/token`, rồi gọi API dữ liệu của mô hình đó; mô hình khác, API quản trị và API người dùng trả 403.
`dxos client list` liệt kê ứng dụng kèm mô hình và vai, `dxos client role <tên> --role <vai>` (hoặc nút đổi vai ở tab Ứng dụng ngoài) chuyển ứng dụng sang vai khác của cùng mô hình mà giữ nguyên tên và mã bí mật, `dxos client rotate <tên>` cấp mã mới và mã cũ hết hiệu lực ngay, `dxos client remove <tên>` xóa client; hồ sơ ứng dụng đã tạo vẫn còn.
Token đã cấp trước khi đổi mã, đổi vai hoặc xóa ứng dụng vẫn dùng được đến khi hết hạn, tối đa khoảng 5 phút; token đổi vai vẫn mang vai cũ cho đến lúc đó.
Đổi sang mô hình khác thì gỡ ứng dụng rồi đăng ký lại.
Tài khoản dịch vụ của ứng dụng không hiện trong danh sách người dùng, và nhật ký quản trị ghi `CLIENT_CREATED`, `CLIENT_SECRET_ROTATED` và `CLIENT_DELETED` kèm tên ứng dụng, và mô hình và vai của nó khi tạo hoặc xóa.
Lệnh dùng client `dxos-cli` như `dxos plan`, nên cần `DXOS_CLI_SECRET` trong `.env`.

### Giao diện trình duyệt ngoài

Đăng ký một giao diện ở tên miền khác bằng một hoặc nhiều redirect URI chính xác:

```bash
dxos client browser add partner-ui \
  --redirect-uri https://partner.example/auth/callback \
  --redirect-uri https://partner.example/silent-renew
```

Lệnh tạo public client `dxos-browser-partner-ui` dùng Authorization Code với PKCE S256, không có secret, password grant hay service account.
Mỗi origin của redirect được thêm vào `CORS_ALLOWED_ORIGINS` trong `deploy/.env`; backend đang chạy được tạo lại để nhận cấu hình.
Token có audience `dxos-api`, vai của người dùng và nhóm đơn vị như web chính.

Redirect phải dùng HTTPS, trừ `localhost`, `127.0.0.1` và `[::1]` được dùng HTTP khi phát triển.
Không nhận wildcard, user info, fragment, host hoặc cổng sai; `CORS_ALLOWED_ORIGINS` chỉ nhận origin, không có path, query hay dấu `/` cuối.
`dxos doctor` kiểm allowlist trước khi khởi động.

`dxos client browser list` liệt kê client trình duyệt.
`dxos client browser remove partner-ui` xóa client nhưng giữ origin trong allowlist vì giao diện khác có thể dùng chung; xóa origin không còn dùng khỏi `CORS_ALLOWED_ORIGINS` rồi tạo lại backend.
Hướng dẫn cấu hình ứng dụng từ đầu đến cuối ở [`docs/EXTENSIONS.md`](../docs/EXTENSIONS.md#giao-diện-ngoài).

## Cài đặt và lệnh `dxos`

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Bộ cài ([`get.sh`](./get.sh), phát hành kèm mỗi bản với tên `dxos-install.sh`) tải gói của bản phát hành, so với SHA-256 trong `dx-os-X.Y.Z.sha256`, rồi chạy [`install.sh`](./install.sh) của gói đó.
Gói sai SHA-256 thì không cài gì.
`install.sh` chép `deploy/` và `sso/realm-dxos.json` vào `~/.dxos` (đổi bằng `--prefix` hoặc biến `DXOS_HOME`), sinh `deploy/.env` bằng [`make_env.sh`](./make_env.sh), đặt `COMPOSE_PROJECT_NAME=dxos` cùng image GHCR của bản đó, và tạo liên kết `~/.local/bin/dxos`.
Chọn bản bằng `--version vX.Y.Z`; cài từ gói có sẵn trên máy bằng `--file dx-os-X.Y.Z.tar.gz`.

| Lệnh | Việc |
|---|---|
| `dxos start [--core\|--full] [tệp...]` | Chạy `doctor.sh`, tải image còn thiếu, khởi động, chờ mọi dịch vụ sẵn sàng (`--core` chỉ chạy Tầng 1, xem [Chế độ chỉ lõi](#chế-độ-chỉ-lõi)); realm chưa có ai vai `ADMIN` thì tạo tài khoản `admin` với mật khẩu tạm in ra một lần; có tệp mô hình thì đưa các tệp vào và in kết quả của từng tệp (xem dưới); rồi in các địa chỉ |
| `dxos stop` | Dừng các container, giữ dữ liệu |
| `dxos status` | Trạng thái từng dịch vụ, các địa chỉ và trạng thái quét mã độc |
| `dxos logs [dịch vụ]` | Theo dõi log |
| `dxos admin` | Đặt mật khẩu tạm mới cho `admin`, tạo tài khoản nếu chưa có |
| `dxos update [vX.Y.Z]` | Cài bản mới hơn (mặc định bản mới nhất): thay tệp chạy, giữ `.env`, `data/` và `apisix/local.yaml`, thêm biến mới, rồi chạy `update.sh`; script này gán cho `dxos-backend-admin` scope `kc-admin-api` mà Keycloak 26.8 đòi (`keycloak-admin-scope.sh`, chạy lại được), và nên sao lưu trước vì cơ sở dữ liệu của Keycloak không hạ phiên bản được |
| `dxos doctor`, `dxos backup` | Chạy `doctor.sh`, `backup.sh` |
| `dxos av [on\|off]` | Bật hoặc tắt quét mã độc tệp tải lên (xem dưới); không có đối số thì in trạng thái quét |
| `dxos plan [--check] <tệp>`, `dxos apply <tệp> [--accept MỨC]` | In kế hoạch của một tệp mô hình, hoặc áp dụng nó (xem dưới) |
| `dxos model upgrade [--write] <tệp>` | Xem diff nâng tuần tự tệp lên format hiện hành; `--write` thay tệp nguyên tử sau khi in diff (xem dưới) |
| `dxos gitops sync [models\|infra]`, `status [models\|infra]`, `approve [infra] <commit> [--accept MỨC]`, `install`, `uninstall` | Kéo hạ tầng và tệp mô hình từ git, so với bản cài và mô hình đang chạy; `install` chạy mỗi 5 phút bằng systemd user timer (xem dưới) |
| `dxos history verify`, `dxos history anchor` | Kiểm lịch sử hồ sơ chưa bị sửa, hoặc ghi điểm neo ngay (xem [`INTEGRATIONS.md`](./INTEGRATIONS.md#lịch-sử-chống-sửa)) |
| `dxos reconcile [install\|uninstall]` | Đưa stack về đúng khai báo; `install` chạy mỗi phút bằng systemd user timer (xem dưới) |
| `dxos archive <mô hình> [--before YYYY-MM-DD] [--out THƯ_MỤC \| --store]`, `dxos archive check <thư mục> \| --store <mô hình>/<lần chạy>`, `dxos archive list` | Xuất hồ sơ đã xong ra Parquet ở một thư mục hoặc ở kho đối tượng, kiểm một bản xuất, liệt kê các lần chạy trong kho (xem [`INTEGRATIONS.md`](./INTEGRATIONS.md#hồ-dữ-liệu)) |
| `dxos archive auto`, `dxos archive schedule [install\|uninstall]` | Ghi vào kho hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS` ngày; `schedule install` chạy mỗi ngày bằng systemd user timer (xem dưới) |
| `dxos demo [--password MẬT_KHẨU]`, `dxos demo remove` | Áp dụng mô hình Một cửa sinh viên nếu chưa chạy, tạo bốn tài khoản thử `judge_student`, `judge_officer`, `judge_officer2` và `judge_supervisor` với một mật khẩu (ngẫu nhiên nếu không nêu, in một lần; chạy lại thì đặt và in mật khẩu mới) và nộp khoảng 40 hồ sơ mẫu qua API chung, mỗi bước do đúng người làm; chạy lại không nộp thêm hồ sơ; `remove` chỉ gỡ bốn tài khoản, hồ sơ có quy trình không xóa được nên còn lại; cần client `dxos-cli` như `dxos plan`. Hạn xử lý 72 giờ của mô hình nên hồ sơ quá hạn chỉ có sau ba ngày kể từ lần nộp |
| `dxos client add <tên> --model M --role R`, `dxos client list`, `dxos client role <tên> --role R`, `dxos client rotate <tên>`, `dxos client remove <tên>` | Đăng ký và quản lý ứng dụng ngoài gọi API bằng token máy (xem phần Ứng dụng ngoài ở trên) |
| `dxos client browser add <tên> --redirect-uri URL [...]`, `dxos client browser list`, `dxos client browser remove <tên>` | Đăng ký và quản lý public OIDC client dùng PKCE cho giao diện trình duyệt ở origin khác |
| `dxos uninstall [--purge]` | Xóa container và lệnh `dxos`; `--purge` xóa cả dữ liệu và thư mục cài, phải gõ xác nhận |

File route riêng `deploy/apisix/local.yaml` được giữ khi cập nhật và ghép với cấu hình lõi mới sau khi kiểm; cấu hình xung đột làm cập nhật dừng trước khi thay container.
Cách khai báo và nạp lại ở [Route riêng trong APISIX](../docs/EXTENSIONS.md#route-riêng-trong-apisix).

### Tệp mô hình lúc khởi động

`dxos start <tệp>...` chép các tệp vào `deploy/model/` (chỉ giữ các tệp vừa đưa vào), ghi tên chúng theo thứ tự, cách nhau bằng dấu phẩy, vào `DXOS_MODEL_FILE` trong `.env` và khởi động lại backend nếu nó đang chạy.
Tên tệp chỉ gồm chữ, số, dấu chấm, gạch ngang và gạch dưới, và hai tệp không được trùng tên.
Backend chờ Keycloak trả lời (tối đa 300 giây) rồi áp dụng lần lượt từng tệp với người thực hiện `bootstrap`, và chỉ khi tệp chưa từng được áp dụng, kế hoạch ở mức an toàn không có từ chối hay dòng vi phạm, và mô hình không có lần áp dụng dở.
Mỗi tệp ghi đúng một dòng `Model bootstrap: <kết quả> <chi tiết>` ở mỗi lần khởi động, và `dxos start` in từng dòng:

| Kết quả | Ý nghĩa |
|---|---|
| `applied` | Đã áp dụng, kèm tên mô hình và phiên bản |
| `unchanged` | Mô hình đang chạy chính tệp này |
| `skipped` | Không áp dụng, kèm lý do; xử lý ở bảng điều khiển |
| `failed` | Tệp không đọc được hoặc không qua kiểm tra (kèm dòng và cột), Keycloak không trả lời kịp, hoặc việc áp dụng lỗi giữa chừng |

Backend chạy ở mọi kết quả, và một tệp lỗi không chặn các tệp sau nó.
Hai tệp cùng tên mô hình thì tệp đứng sau là `skipped`.
`dxos start` chờ đủ dòng kết quả của mọi tệp, tối đa 300 giây (đổi bằng `DXOS_MODEL_TIMEOUT`), rồi thoát với mã 4; có tệp `failed` thì thoát với mã 3.
Backend chỉ đọc tệp khi khởi động, nên `dxos start` không tham số trên hệ thống đang chạy không đọc lại tệp.
Tệp hỏng vẫn nằm trong `.env` và được báo lại ở mỗi lần backend khởi động cho đến khi sửa hoặc thay.
Cấu hình đặt tên các tệp ở `DXOS_MODEL_FILE`, cách nhau bằng dấu phẩy, và thư mục chứa chúng ở `ENGINE_MODEL_DIR` (`/models` trong container).

### Nâng format tệp mô hình

`dxos model upgrade <tệp>` gọi API nâng format và in unified diff mà không ghi tệp.
`dxos model upgrade --write <tệp>` in cùng diff rồi ghi toàn bộ kết quả vào file tạm cùng thư mục và rename qua tệp gốc; quyền file được giữ lại.
Format `0` được nâng tuần tự lên format hiện hành `1`; chạy lại format `1` không đổi nội dung, còn format mới hơn bản lõi hoặc thiếu một bước migration bị từ chối.

Nếu lệnh lỗi trước rename, tệp gốc giữ nguyên.
Xóa file `.dxos-model-upgrade.*` còn sót sau khi máy bị dừng đột ngột, sửa nguyên nhân rồi chạy lại; sau khi rename, khôi phục bằng Git hoặc bản sao lưu nếu cần.

### Đổi mô hình bằng dòng lệnh

`dxos plan <tệp>` in kế hoạch của tệp mô hình mà không đổi gì; mã thoát 1 khi kế hoạch bị từ chối.
`dxos plan --check <tệp>` thoát với mã 0 khi mô hình đang chạy giống tệp, 2 khi kế hoạch có thay đổi và 1 khi bị từ chối, để dùng trong pipeline.
`dxos apply <tệp> [--accept SAFE|MIGRATION|LOSS]` lấy kế hoạch rồi áp dụng với phiên bản của kế hoạch làm `base`.
Kế hoạch có mức cao hơn `--accept` (mặc định `SAFE`) thì bị từ chối với mã thoát 1 và không đổi gì; lệnh không bao giờ tự nâng mức.
Tệp giống mô hình đang chạy và không có gì lệch thì in "Nothing to apply" và thoát với mã 0.
Tệp giống nhưng bảng hoặc Keycloak đã lệch khỏi tệp thì kế hoạch ghi `repair`; `dxos apply` sửa phần lệch, và cần `--accept MIGRATION` khi kế hoạch ở mức đó.
Việc kiểm lệch chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc backend khởi động; bảng hoặc cột thừa do sửa tay bị từ chối chứ không bị xóa, vai thừa ở client của mô hình và nhóm đơn vị thừa không bị đụng, còn chỉ mục hay ràng buộc thừa bị xóa khi áp dụng với mức `MIGRATION`.
Đường dẫn tương đối tính theo thư mục gõ lệnh.

Lệnh đăng nhập bằng client `dxos-cli` của realm, dùng `DXOS_CLI_SECRET` trong `.env`; tài khoản dịch vụ của client giữ vai `ADMIN`, và lịch sử áp dụng ghi tên nó (`service-account-dxos-cli`).
Bí mật nằm trong `.env` của máy chủ, nên chạy được trong pipeline GitOps trên chính máy đó.
Realm đã import từ trước chưa có client này: thêm client `dxos-cli` ở Keycloak Admin Console (mở theo [Keycloak Admin Console](#keycloak-admin-console)) hoặc dựng lại realm.

### Kéo từ git (GitOps)

`dxos gitops sync` kéo từ git hai thứ, mỗi thứ một kho và một chế độ riêng, và cần `git` trên máy chạy `dxos` (thêm `ssh` cho địa chỉ SSH):

- **Hạ tầng:** thư mục `deploy/`, cấu hình cổng vào và số phiên bản image của bản cài, từ một kho DX-OS.
- **Mô hình:** các tệp mô hình nghiệp vụ, từ một kho của người triển khai.

Cấu hình trong `deploy/.env`, mẫu ở `.env.example`:

| Biến | Ý nghĩa |
|---|---|
| `GITOPS_INFRA_REPO` | Kho DX-OS chứa `deploy/` và `sso/`: `https://`, `ssh://`, `user@host:đường/dẫn`, `file://` hoặc đường dẫn tuyệt đối; để trống thì không kéo hạ tầng |
| `GITOPS_INFRA_REF` | Tag phát hành `vX.Y.Z` (đổi luôn `DXOS_IMAGE_TAG` thành tag đó) hoặc nhánh (giữ nguyên `DXOS_IMAGE_TAG`), mặc định `main` |
| `GITOPS_INFRA_MODE` | `report`, `approve` hoặc `auto`, mặc định `report` |
| `GITOPS_REPO` | Kho chứa tệp mô hình, cùng dạng địa chỉ; để trống thì không kéo mô hình |
| `GITOPS_REF`, `GITOPS_PATH`, `GITOPS_MODE` | Nhánh hoặc tag (mặc định `main`), thư mục chứa các tệp `.yaml` hoặc `.yml` không đệ quy (mặc định `models`), chế độ (mặc định `report`) |
| `GITOPS_SSH_KEY`, `GITOPS_KNOWN_HOSTS` | Đường dẫn tuyệt đối tới deploy key chỉ đọc và tệp `known_hosts`, đặt ngoài git, dùng cho cả hai kho |
| `GITOPS_REQUIRE_SIGNED`, `GITOPS_ALLOWED_SIGNERS` | `true` thì chỉ nhận commit có chữ ký SSH của người trong tệp `allowed_signers`, cho cả hai kho |

`.env` chỉ giữ đường dẫn tới khóa, không giữ nội dung khóa.
Nên dùng địa chỉ SSH với deploy key chỉ đọc; địa chỉ `https://` có token trong URL làm token nằm trong cấu hình của bản sao ở `deploy/gitops/`, thư mục chỉ chủ sở hữu đọc được.
Địa chỉ SSH kiểm host theo `GITOPS_KNOWN_HOSTS` hoặc `~/.ssh/known_hosts`, không tự chấp nhận host lạ.

Mỗi lượt kéo ref đã cấu hình, xác minh chữ ký nếu bật, rồi so với bản cài.
Kết quả mỗi mục là một trong `in-sync`, `drift`, `pending`, `rejected`, `applied` và `failed`.
Ba chế độ khác nhau ở việc có áp dụng hay không:

- `report` chỉ báo lệch (`drift`) và không đổi gì.
- `approve` để thay đổi nằm chờ (`pending`) cho đến khi người có quyền chạy `dxos gitops approve [infra] <commit>`.
- `auto` tự áp dụng; chi tiết khác nhau giữa hai nguồn ở dưới.

Commit không có chữ ký hợp lệ khi `GITOPS_REQUIRE_SIGNED=true` bị từ chối cả lượt, lệnh thoát với mã 1 và không áp dụng gì.
Lượt lỗi, như kho không với tới được hay một lần áp dụng thất bại, được thử lại ở lượt sau.
Sự duyệt gắn với một commit và hết khi thay đổi được áp dụng; một lệch khác về sau cần duyệt lại.
`dxos gitops install` chạy `sync` mỗi 5 phút bằng systemd user timer, `uninstall` gỡ; lượt chạy bỏ qua khi stack đã dừng bằng `dxos stop`.
`dxos doctor` kiểm các biến `GITOPS_*` khi một trong hai kho có giá trị.

**Hạ tầng.**
Lệnh so các tệp chạy của kho (cùng danh sách mà `install.sh` chép) với bản cài, và so tag image nếu `GITOPS_INFRA_REF` là tag phát hành.
Mục `infra` báo số tệp đổi, thêm và bỏ.
Áp dụng là chạy `install.sh --upgrade` từ bản sao trong kho rồi `update.sh`, đúng như `dxos update`: giữ `.env`, `data/` và `apisix/local.yaml`, kiểm cấu hình cổng vào trước khi thay container, chỉ tạo lại container có image đổi.
Nếu `update.sh` lỗi, ví dụ kéo image thất bại, mục `infra` ở trạng thái `failed` và `update.sh` chạy lại ở lượt sau cho đến khi xong, dù tệp đã khớp kho.
Stack đang dừng thì chỉ thay tệp, và stack dùng chúng khi khởi động.
`auto` cho hạ tầng bắt buộc `GITOPS_REQUIRE_SIGNED=true`, vì đây là thay đổi không có người bấm.
Nâng cấp không quay lui được cơ sở dữ liệu của Keycloak, nên cần sao lưu trước khi bật `auto`.
`dxos reconcile` vẫn lo phần tự phục hồi từng phút: container bị xóa hay lệch được tạo lại theo khai báo.
Áp dụng chạy `install.sh` lấy từ chính kho, tức mã trong kho chạy trên máy này; người chạy `dxos gitops approve infra` cần đọc diff của `deploy/` trước, và kho hạ tầng chỉ nên ghi được bởi người tin cậy.

**Mô hình.**
Mỗi tệp được lập kế hoạch rồi áp dụng bằng `dxos apply`.
`auto` chỉ áp dụng kế hoạch mức `SAFE`; kế hoạch cao hơn nằm chờ như ở `approve`.
`dxos gitops approve <commit> [--accept MỨC]` duyệt đến mức nêu (mặc định `SAFE`), và phải nêu mức của kế hoạch nếu cao hơn.
Kế hoạch bị từ chối (`rejected`) không bao giờ được áp dụng, và một tệp lỗi không chặn các tệp khác.
Muốn quay lại thì `git revert` ở kho rồi để lượt sau kéo về; kế hoạch mất dữ liệu vẫn phải được duyệt ở mức `LOSS`, và phiên bản mô hình cũ vẫn quay về được bằng API quay lui.

`dxos gitops status [models|infra]` in lượt gần nhất và thoát với mã 0 khi mọi mục khớp, 2 khi còn mục lệch hoặc chờ duyệt, 1 khi lượt gần nhất lỗi.
`deploy/gitops/<models|infra>/history.log` ghi một dòng mỗi khi commit hoặc kết quả của một mục đổi; lỗi của lần áp dụng gần nhất nằm ở `last-apply-error.log` cùng thư mục.
Ví dụ kho mô hình kèm workflow dán kế hoạch vào pull request ở [`examples/model-repo`](../examples/model-repo/README.md).

### Lần áp dụng dừng giữa chừng

Mô hình có lần áp dụng dừng giữa chừng bị đóng với người dùng và hiện nhãn "Đang dừng dở" ở bảng điều khiển.
Chọn Tiếp tục để hoàn tất, hoặc Hủy bỏ để đưa các bảng về phiên bản đang chạy; chạy `deploy/backup.sh` trước khi Hủy bỏ nếu cần giữ dữ liệu.
Nhóm đơn vị mà lần dở đã tạo ở Keycloak không bị xóa, vì các mô hình dùng chung nhóm theo tên; quản trị viên xóa nhóm rỗng ở Keycloak nếu muốn.
Trước khi áp dụng thay đổi mức mất dữ liệu (`accept=LOSS`), chạy `deploy/backup.sh`.

### Tệp bị gỡ khỏi hồ sơ

Gỡ hoặc thay một tệp, xóa hồ sơ hay xóa thực thể thì đối tượng rời kho sau `DXOS_FILE_DELETE_DELAY` (mặc định 24 giờ).
Backend ghi một thông điệp `file.delete` vào outbox trong cùng giao dịch, đến hạn thì xóa đối tượng.
Đối tượng đã mất thì thông điệp coi như xong; kho đang ngừng thì thông điệp được thử lại.
Một lượt sao lưu ngắn hơn khoảng trễ này luôn có đủ tệp cho mọi dòng tệp đính kèm trong bản dump.
Tệp của giao dịch bị hủy (đã vào kho nhưng dòng không được ghi) bị xóa ngay.

### Tự phục hồi

Mọi service chạy lâu có `restart: unless-stopped`, nên Docker khởi động lại chúng khi tiến trình thoát hoặc máy khởi động lại.
`dxos reconcile` chạy `docker compose up -d --no-build` theo các image đã ghim trong `.env`: container bị xóa được tạo lại, container lệch cấu hình được tạo lại theo khai báo, container đang đúng thì để nguyên.
Mỗi lượt có thay đổi ghi một dòng vào `deploy/reconcile.log`; lượt đến khi lượt trước còn chạy thì bỏ qua.
`update.sh` và `backup.sh` giữ cùng khóa `deploy/.reconcile.lock`, nên khi một lần cập nhật hoặc sao lưu đang chạy thì `dxos reconcile` bỏ qua lượt; hai script đợi tối đa 5 phút để lấy khóa rồi báo lỗi.
`dxos stop` để lại tệp `deploy/.stopped`, và `dxos reconcile` không bật lại stack có tệp này; `dxos start` xóa nó.

`dxos reconcile install` cài `dxos-reconcile.timer` (systemd user timer) chạy lệnh trên mỗi phút, `dxos reconcile uninstall` gỡ.
Máy không có systemd thì chạy `dxos reconcile` từ cron mỗi phút.
Timer của user chỉ chạy khi user còn phiên đăng nhập, trừ khi bật `loginctl enable-linger <user>`.

### Lưu trữ hồ sơ đã đóng

`dxos archive schedule install` cài `dxos-archive.timer` (systemd user timer) chạy `dxos archive auto` lúc 03:30 mỗi ngày, `dxos archive schedule uninstall` gỡ.
Mỗi lượt ghi vào bucket `S3_ARCHIVE_BUCKET` của SeaweedFS các hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS` ngày (mặc định 365) mà kho chưa giữ, và ghi kết quả vào `deploy/archive.log`.
Dữ liệu trong cơ sở dữ liệu giữ nguyên; `dxos archive list` xem các lần chạy, `dxos archive check --store <mô hình>/<lần chạy>` kiểm một lần.
Lần chạy đầu cần mạng để DuckDB tải extension.

## Chế độ chỉ lõi

`dxos start --core` chỉ chạy Tầng 1: cổng vào APISIX, Keycloak, dịch vụ lõi, PostgreSQL và kho tệp SeaweedFS, thêm ClamAV khi bật quét mã độc.
Ứng dụng web, portal và dịch vụ giám sát không chạy, và gateway không có route của chúng: `/`, `/portal/`, `/monitor/` và `/console` trả 404.
Gateway cũng không gửi nhật ký truy cập tới dịch vụ giám sát.
API, đăng nhập, áp dụng tệp mô hình, `/v3/api-docs`, OpenAPI theo mô hình và lưu tệp dùng như ở chế độ đầy đủ.

Chế độ được lưu trong `deploy/.env` bằng `DXOS_MODE` và profile `app` của `COMPOSE_PROFILES`; `dxos` sửa cả hai cùng lúc.
`dxos start --core` gỡ container của Tầng 2 nếu đang chạy, và `dxos start --full` chạy lại đủ.
`dxos start`, `dxos stop`, `dxos status`, `dxos update` và `dxos reconcile` giữ chế độ đã lưu, nên không bật lại thành phần nào của Tầng 2; `docker compose up -d` chạy tay trong `deploy/` cũng theo `.env`.
Không có `DXOS_MODE` thì là chế độ đầy đủ, như các bản cài trước.
Không có bảng điều khiển ở chế độ lõi: áp dụng mô hình bằng `dxos apply` hoặc `POST /api/v1/models/apply`, và quản trị người dùng bằng API hoặc `dxos admin`.

## Keycloak Admin Console

Gateway không mở Keycloak Admin Console mặc định: `/admin` và `/admin/*` trả 404, và `/swagger-ui.html` cùng `/swagger-ui/*` cũng vậy vì lõi không kèm Swagger UI.
Dịch vụ lõi gọi Admin API của Keycloak qua mạng nội bộ, nên quản lý tài khoản, vai và client không bị ảnh hưởng.
`dxos admin` và `dxos start` đặt tài khoản `admin` của realm bằng `kcadm.sh` trong container, không cần gateway.

Mở Admin Console khi cần vận hành:

1. Đặt `KEYCLOAK_ADMIN_CONSOLE=true` trong `deploy/.env`.
2. Chạy `docker compose up -d apisix-config` rồi `docker compose restart apisix` trong `deploy/`.
3. Mở `<địa chỉ>/admin/` và đăng nhập bằng `KEYCLOAK_ADMIN_USERNAME` và `KEYCLOAK_ADMIN_PASSWORD`; `dxos status` in địa chỉ này khi cờ đang bật.

Đặt lại `false` rồi làm lại bước 2 để đóng.
Các script kiểm ở `deploy/dev` gọi Keycloak Admin API qua gateway nên cần cờ này bật (xem [`docs/TESTING.md`](../docs/TESTING.md)).

## Kiểm tra trước khi chạy

`./doctor.sh` (trong `deploy/`) chỉ đọc, không đổi gì và không in giá trị nào của `.env`.
Nó kiểm Docker và Compose, RAM, ổ đĩa, `deploy/.env` (kể cả `DXOS_MODE` khớp với profile `app`) và các cổng.
`.env` được so với các biến `${NAME:?}` mà Compose bắt buộc phải có: biến thiếu hoặc để trống bị báo `FAIL`.
Biến có trong `.env` mà `.env.example` không liệt kê bị báo `WARN`.
Các cổng đã bị chiếm chỉ được kiểm khi stack chưa chạy.
Quét mã độc được báo là bật hay tắt; `STORAGE_SCAN_HOST=clamav` mà `COMPOSE_PROFILES` không có `av` bị báo `FAIL`, vì ClamAV sẽ không chạy và mọi tệp tải lên bị từ chối.
Thoát với mã 1 khi có `FAIL`, nên chạy trước `./update.sh` và `docker compose up -d` trên máy chủ.

## Thời gian dựng từ tệp mô hình

Số đo trên máy chủ 8 CPU, 7 GB RAM, đã xóa stack cũ và năm image `dxos-*`, bản `2.0.0` cài bằng `dxos-install.sh`.

| Bước | Thời gian |
|---|---|
| `dxos start <tệp mô hình>`: kéo năm image `dxos-*` từ GHCR, khởi động tám service, áp dụng `onestop.yaml` | 174 giây |
| `dxos av on`: tải ClamAV, khởi động lại backend | 32 giây |
| `dxos demo`: bốn tài khoản thử và 40 hồ sơ mẫu | 98 giây |

Từ tệp mô hình đến hệ thống chạy mất khoảng ba phút; image của bên thứ ba (`postgres`, `apisix`, `seaweedfs`) đã có sẵn trên máy nên không nằm trong số này.

## Tài nguyên và số bản sao

Số đo trên máy 12 CPU, 31 GB RAM, với stack tám service (không bật `clamav`) đang rảnh sau khi nạp 40 hồ sơ mẫu bằng `dxos demo`.

| Service | RAM (MiB) |
|---|---|
| `keycloak` | 624 |
| `backend` | 494 |
| `monitor` | 287 |
| `apisix` | 221 |
| `postgres` | 117 |
| `seaweedfs` | 75 |
| `frontend`, `portal` | 11 mỗi service |

Cả stack dùng khoảng 1,8 GiB RAM, CPU gần 0 lúc rảnh.
Image chiếm khoảng 3,5 GiB đĩa (kích thước `docker images` của tám service); cơ sở dữ liệu 12 MB.

Dựng stack rỗng khi image đã có trên máy, với một tệp mô hình: stack lên và mô hình áp dụng xong sau khoảng 1 phút, và tài khoản ADMIN đầu tiên (`dxos admin`) thêm vài giây.
Trên mô hình đã có 40 hồ sơ, `dxos plan` và `dxos apply` một trường mới mất dưới 1 giây và các hồ sơ còn nguyên.
Giao diện, cổng vào, trang đăng nhập Keycloak và bảng điều khiển không tải tài nguyên từ ngoài cổng vào của hệ thống.

`backend` chạy một bản.
Chạy hai bản sau gateway thì mỗi bản giữ danh sách mô hình riêng trong bộ nhớ: sau một lần áp dụng, bản kia trả 404 cho mô hình mới và tiếp tục phục vụ mô hình đang bị giữ, cho đến khi khởi động lại cả hai.

## Giám sát từ ngoài

`./online-check.sh` (trong `deploy/`) gọi web, portal, trang đăng nhập và `/api/v1/system/status` qua địa chỉ công khai, rồi thoát với mã 1 khi một đường không trả 200 hoặc khi `backend` hoặc `storage` không `UP`.
Địa chỉ lấy từ `GATEWAY_PUBLIC_URL` trong `.env`; biến `PUBLIC_URL` trong shell ghi đè để kiểm địa chỉ khác.
Chạy theo lịch bằng cron, ví dụ mỗi 12 giờ: `0 */12 * * * cd /đường/dẫn/deploy && ./online-check.sh >> online-check.log 2>&1`.
Khi `.env` có `ALERT_WEBHOOK_URL_ADMIN` (hoặc `ALERT_WEBHOOK_URL`), script gửi một tin vào kênh khi site chuyển sang lỗi và một tin khi site trở lại, không gửi lặp trong lúc còn lỗi.
Script chạy trên chính máy chủ nên không phát hiện được việc máy chủ tắt hẳn; muốn phát hiện, thêm một dịch vụ ping ngoài (ví dụ UptimeRobot) trỏ vào `/api/v1/system/status`.

## Sao lưu và khôi phục

`./backup.sh` (trong `deploy/`) chụp một bản sao lưu mã hóa của những gì không dựng lại được từ repo.
Gồm toàn bộ Postgres (dữ liệu nghiệp vụ, tài khoản Keycloak) và tệp trong SeaweedFS.
Không gồm `apisix/local.yaml` của bản cài; cất bản sao của nó cùng cấu hình triển khai.
Script dùng restic chạy trong container nên chỉ cần Docker.
Bản sao lưu khử trùng lặp và mã hóa bằng `BACKUP_PASSWORD`.
Cất mật khẩu này ở nơi khác máy chủ, vì mất nó là không đọc lại được.

```bash
cd deploy
./backup.sh              # chụp, rồi giữ 7 bản theo ngày, 4 theo tuần, 6 theo tháng
./backup.sh verify       # nạp bản dump mới nhất vào một Postgres tạm và kiểm tra
./backup.sh snapshots    # liệt kê các bản
```

`verify` in số mô hình đã áp dụng và tổng số dòng trong các bảng `m_*` của bản dump.
Mô hình có phiên bản trong `engine.model_version` mà không có bảng nào trong schema `m_<mô hình>` làm `verify` báo `FAILED` kèm tên mô hình.
`verify` còn khôi phục bản chụp của kho vào một Docker volume tạm và khởi động một SeaweedFS tạm trên đó (filer HTTP chỉ mở trong container), rồi kiểm mọi khóa trong `engine.attachment` của bản dump đều có tệp.
`verify` không so nội dung từng tệp, không kiểm quy trình đang chạy và không kiểm quyền sau khi khôi phục.
Thiếu tệp thì `verify` báo `FAILED` kèm tên các tệp thiếu và thoát với mã 1.
`backup.sh` dump Postgres rồi mới chụp kho, và tệp bị gỡ chỉ rời kho sau `DXOS_FILE_DELETE_DELAY`, nên bản sao lưu luôn có tệp của mọi dòng trong bản dump, miễn là cả hai bước ngắn hơn khoảng đó.
Lượt chạy lâu hơn khoảng đó thì `backup.sh` in `WARNING`; khi đó tăng `DXOS_FILE_DELETE_DELAY`.

Kho nằm ở `deploy/backups` (đổi bằng `BACKUP_DIR`) hoặc ở một kho restic từ xa (`BACKUP_REPOSITORY`, ví dụ `s3:...`).
Kho cùng máy với dữ liệu không cứu được khi mất máy, nên chép nó ra ngoài (rsync, rclone) hoặc dùng kho từ xa.
Chạy theo lịch bằng cron, ví dụ mỗi đêm: `0 2 * * * cd /đường/dẫn/deploy && ./backup.sh >> backups/backup.log 2>&1`, và chạy `verify` mỗi tuần.
Máy chủ dùng tệp env khác `.env` thì đặt `ENV_FILE=.env.dev ./backup.sh`.
CI chạy một lượt backup và verify trên Postgres thật có một mô hình hai dòng và một tệp đính kèm thật ở job `Back up and restore` của workflow `Platform`, rồi thêm một dòng tệp không có trong kho và mong `verify` thất bại.

**Khôi phục** (máy mới hoặc sau khi mất dữ liệu):

1. Dựng lại stack từ repo với cùng `deploy/.env`, giữ Postgres chạy và dừng các dịch vụ còn lại: `docker compose up -d postgres` rồi `docker compose stop` các dịch vụ khác.
2. Lấy bản sao lưu ra thư mục tạm: `./backup.sh restore /tmp/dxos-restore`.
3. Nạp Postgres: `gunzip -c /tmp/dxos-restore/stage/postgres.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" postgres`.
   Hai dòng lỗi `role "..." already exists` và `database "..." already exists` là bình thường vì tài khoản quản trị và database chính đã có sẵn.
   Phần còn lại phải nạp không lỗi.
4. Chép `data/seaweedfs` từ `/tmp/dxos-restore/data/` vào `deploy/data/` (các tệp thuộc root nên dùng `sudo`), rồi `docker compose up -d`.

## Quét mã độc tệp tải lên

Khi bật, mọi tệp đính kèm được ClamAV quét trước khi xử lý và lưu.
Tệp nhiễm bị từ chối với HTTP 422 `ERR_FILE_INFECTED` và lần chặn được ghi vào `audit_log` (`FILE_INFECTED`, kèm tên mẫu mã độc).
ClamAV không trả lời thì tệp không được lưu và người dùng nhận HTTP 503 `ERR_SCAN_UNAVAILABLE`, vì tệp chưa quét không được coi là sạch.
Tắt mặc định vì ClamAV cần khoảng 1 GB RAM.
Bật bằng `dxos av on`, tắt bằng `dxos av off`: lệnh sửa `COMPOSE_PROFILES` và `STORAGE_SCAN_HOST` trong `.env`, rồi khởi động hoặc gỡ ClamAV và tạo lại backend nếu stack đang chạy.
Không dùng `dxos` thì đặt `COMPOSE_PROFILES=app,av` (bỏ `app` ở chế độ lõi) và bỏ dấu `#` ở `STORAGE_SCAN_HOST=clamav` trong `deploy/.env`, rồi `docker compose up -d`.
Chữ ký virus tải về lần đầu mất vài phút và được giữ trong volume `clamav_data`.
Image chính thức tự cập nhật chữ ký một lần mỗi ngày từ mạng phân phối của ClamAV; không dùng `curl` hay script riêng để tải chúng, vì ClamAV có thể giới hạn hoặc chặn cách tải đó.
Mã 429 trong log của `clamav` là bị giới hạn tốc độ (hay gặp ở IP máy chủ đám mây dùng chung), và chữ ký cũ vẫn dùng được cho đến lần cập nhật sau.
ClamAV chặn tải chữ ký với phiên bản đã hết hỗ trợ, nên nâng image `clamav/clamav` trong `deploy/docker-compose.yml` khi bản đang ghim tới hạn (chính sách ở https://docs.clamav.net/faq/faq-eol.html).
Mỗi lần clamd nạp lại chữ ký (hằng ngày và sau khi khởi động) nó không quét được khoảng 10 giây, và người dùng gặp 503 trong lúc đó; chờ rồi tải lại.
Thử bằng tệp EICAR (chuỗi kiểm thử chuẩn của ngành diệt virus): tệp chỉ gồm đúng chuỗi đó, đổi tên `.pdf` rồi đính vào một yêu cầu; `deploy/dev/check_demo.py` tự làm bước này khi `STORAGE_SCAN_HOST` có giá trị.

## Dữ liệu cá nhân ra nước ngoài

Cài đặt chạy trên máy chủ của người triển khai nên dữ liệu ở lại nơi máy chủ đặt.
Dữ liệu chỉ ra nước ngoài khi người triển khai tự chọn một đích ngoài lãnh thổ Việt Nam.
Các chỗ cấu hình có thể làm việc đó:

- `BACKUP_REPOSITORY` trỏ tới kho restic ở máy chủ hoặc dịch vụ lưu trữ nước ngoài (xem [Sao lưu và khôi phục](#sao-lưu-và-khôi-phục)).
- Webhook sự kiện, `ALERT_WEBHOOK_URL` và `ALERT_WEBHOOK_URL_ADMIN` trỏ tới dịch vụ nước ngoài, và nội dung gửi đi có dữ liệu cá nhân.
- Đặt cả stack trên máy chủ ảo hoặc dịch vụ đám mây của nhà cung cấp nước ngoài.

Văn bản áp dụng (đọc ngày 2026-11-11):

- [Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15](https://congbao.chinhphu.vn/van-ban/luat-so-91-2025-qh15-45578.htm), có hiệu lực từ 2026-01-01.
- [Nghị định 356/2025/NĐ-CP](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-356-2025-nd-cp-468371.htm) hướng dẫn luật, ban hành 2025-12-31, có hiệu lực từ 2026-01-01.

Điều 20 của luật coi là chuyển xuyên biên giới ba trường hợp: chuyển dữ liệu đang lưu ở Việt Nam sang hệ thống lưu trữ ngoài lãnh thổ, chuyển cho tổ chức hoặc cá nhân ở nước ngoài, và dùng nền tảng ngoài lãnh thổ để xử lý dữ liệu thu thập ở Việt Nam.
Bên chuyển phải lập hồ sơ đánh giá tác động chuyển dữ liệu cá nhân xuyên biên giới và gửi một bản chính cho cơ quan chuyên trách về bảo vệ dữ liệu cá nhân trong 60 ngày kể từ ngày đầu tiên chuyển.
Hồ sơ lập một lần cho suốt thời gian hoạt động và được cập nhật khi có thay đổi.
Điều 18 của nghị định quy định hồ sơ gồm báo cáo theo Mẫu số 09, bản sao hợp đồng hoặc văn bản chuyển giao dữ liệu, và các chính sách, quy trình bảo vệ dữ liệu cá nhân liên quan.

Luật không đòi hồ sơ này trong các trường hợp sau:

- Cơ quan nhà nước có thẩm quyền chuyển dữ liệu.
- Cơ quan, tổ chức lưu dữ liệu cá nhân của chính người lao động của mình trên dịch vụ điện toán đám mây.
- Chủ thể dữ liệu tự chuyển dữ liệu của mình.
- Các trường hợp Điều 17 của nghị định liệt kê thêm: báo chí và truyền thông, dữ liệu đã công khai theo pháp luật, tình huống khẩn cấp để bảo vệ tính mạng, sức khỏe và tài sản, quản lý nhân sự xuyên biên giới theo quy chế lao động, và dữ liệu cung cấp để ký hợp đồng hoặc làm thủ tục vận chuyển, chuyển tiền, thanh toán, khách sạn, thị thực, học bổng.

Cơ quan chuyên trách được kiểm tra định kỳ không quá một lần mỗi năm, kiểm tra đột xuất khi có vi phạm hoặc sự cố lộ, mất dữ liệu, và có quyền yêu cầu ngừng chuyển khi dữ liệu bị dùng vào hoạt động có thể gây tổn hại quốc phòng, an ninh quốc gia.
Giữ máy chủ, kho sao lưu và đích webhook trong lãnh thổ Việt Nam là cách tránh phát sinh nghĩa vụ này.

Hai liên kết trỏ tới bản đăng trên Công báo điện tử, và nội dung các điều được nêu ở trên đã đối chiếu với bản PDF ở đó.
Điều kiện áp dụng cho từng bản cài, như vai trò bên kiểm soát hay bên xử lý và loại dữ liệu nhạy cảm, cần luật sư hoặc cơ quan chuyên trách xác nhận.
Mục này tóm tắt văn bản, không phải tư vấn pháp lý.
