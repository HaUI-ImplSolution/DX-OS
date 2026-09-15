# Quy ước làm việc của Claude (backend)

Cách Claude nên vận hành trong thư mục này — về quy trình và hành vi, không phải sự thật về dự án.
`backend/` là service **Spring Boot (Java)** của DX-Farm.
Để biết dự án *là gì*, xem [../README.md](../README.md).

> Backend đang ở giai đoạn khởi tạo, chưa có code (`backend/` hiện chỉ có `.claude/`).
> Những sự thật cụ thể bên dưới (group ID, có dùng Lombok hay không, vị trí swagger...) sẽ được cập nhật ngay khi project Spring Boot được khởi tạo.

## 1. Mặc định vào Plan Mode
- Vào plan mode cho mọi task không tầm thường (3+ bước, hoặc một quyết định kiến trúc thật sự).
- Dùng plan mode cả cho bước verify, không chỉ khi build.
- Viết spec/approach ra trước khi đụng vào code, để giảm công sửa lại về sau.

## 2. Vòng lặp tự cải thiện
- Sau mỗi lần bị user sửa, ghi lại pattern (xem quy ước [[feedback memory]]) để không lặp lại lỗi ở phiên sau.
- Ưu tiên một rule bền vững hơn là một fix một lần, khi điều chỉnh đó tổng quát hoá được.

## 3. Xác minh trước khi báo xong
- User preference: **không** chạy test và **không** chạy/khởi động app.
  Xác minh chỉ dừng ở compile: `./mvnw compile` (hoặc `./mvnw test-compile` nếu có sửa test source) để bắt lỗi, rồi đọc diff.
- Không chạy `./mvnw spring-boot:run`, `./mvnw test`, gọi thử endpoint như một phần của "verify" trừ khi user yêu cầu rõ.

## 4. Đòi hỏi sự tinh gọn (có chừng mực)
- Với thay đổi không tầm thường, dừng lại và hỏi "có cách nào gọn hơn không?" trước khi chốt.
- Bỏ qua bước này với fix đơn giản, hiển nhiên — không overengineer một thay đổi một dòng.

## 5. Commit message luôn bằng tiếng Anh
- Bất kể cuộc trò chuyện đang diễn ra bằng tiếng Việt hay tiếng Anh, **commit message luôn viết bằng tiếng Anh** (subject + body) — không commit tiếng Việt.
- Nếu phát hiện commit đã lỡ viết tiếng Việt và cần sửa lại: chỉ amend/rewrite khi user xác nhận rõ, đặc biệt nếu commit đó đã push (cần force-push, theo Git Safety Protocol chung).

## 6. Yêu cầu commit/push lùi ngày ("push với ngày ...")
- Khi user yêu cầu commit/push với ngày cụ thể (vd "push với ngày 29/06/2026"), chỉ set **author date** bằng `git commit --date="YYYY-MM-DD HH:MM:SS" -m "..."`, hoặc `git commit --amend --date="YYYY-MM-DD HH:MM:SS" --no-edit` cho commit đã có.
  **Không** set thêm `GIT_COMMITTER_DATE` ở phía committer.
- Lý do: ô đóng góp trên GitHub tính theo **author date**, nhưng thứ tự lịch sử/commit list tính theo **committer date**.
  Lùi ngày cả hai (vd `GIT_AUTHOR_DATE=... GIT_COMMITTER_DATE=...`) làm commit bị sắp xếp như thể nó được tạo vào ngày cũ đó, nên bị chôn xuống dưới commit cha thực tế — không phải điều user muốn.
  Giữ committer date là "hiện tại" (mặc định của git khi chỉ override author date/`--date`) vẫn đảm bảo commit được sắp đúng vị trí mới nhất, trong khi vẫn đánh dấu đúng ngày yêu cầu trên contribution graph.
- Nếu commit đã push, việc này cần `git commit --amend` + `git push --force-with-lease` — luôn xác nhận với user trước khi force-push, theo Git Safety Protocol chung.

## 7. Không tự ý commit/push
- Sau khi sửa xong một fix, dừng lại để user review diff.
  **Không** tự chạy `git commit` hoặc `git push` trừ khi user yêu cầu rõ ràng trong lượt đó.
- Sự đồng ý commit/push trước đó trong cuộc trò chuyện không tự động áp dụng cho thay đổi sau — mỗi fix mới cần một sự đồng ý riêng, kể cả giữa cùng một session.
- Lý do: user từng bị các fix tự động commit và push mà không có cơ hội review trước.
- Điều này không nới lỏng Git Safety Protocol chung (force-push, `--no-verify`... vẫn luôn cần xác nhận rõ ràng mỗi lần) — nó siết chặt hơn: commit/push thông thường giờ cũng cần hỏi rõ, không chỉ suy đoán "chắc user muốn vậy".

## 8. Toàn quyền với việc reversible
- Trong thư mục `backend/`, được tiến hành mà không cần dừng lại xin phép từng bước với việc reversible, local: đọc file, chạy lệnh chỉ-đọc (`git show`, `git log`, `git diff`, `grep`, `./mvnw compile`), và sửa/viết file (code, docs, spec).
- Lý do: user đã cấp toàn quyền rõ ràng cho loại việc này và không muốn phải duyệt từng bước — hỏi xác nhận lặp lại cho việc chỉ-đọc hoặc dễ hoàn tác thuần tuý là gây cản trở.
- Điều này **không** áp dụng cho thao tác git mang tính phá huỷ hoặc thay đổi trạng thái chung (`git commit`, `git push`, `git reset --hard`, force-push, `--no-verify`, xoá file/branch) — các thao tác này vẫn cần xác nhận rõ ràng mỗi lần theo mục 7 và Git Safety Protocol chung.

## 9. Thứ tự tài liệu: Spec → Swagger → Code
- Bất cứ khi nào một thay đổi backend ảnh hưởng đến những gì API trả về hay ý nghĩa của response field (endpoint mới, thay đổi shape request/response, thay đổi ý nghĩa field), swagger phải được cập nhật trong cùng một lần làm việc — không được ship riêng thay đổi backend rồi coi swagger là việc làm sau.
- Thứ tự: **spec doc trước (chỉ khi task thực sự cần một tài liệu và user yêu cầu — nghĩa là một tài liệu đặc tả thật, không phải file plan của plan mode) → swagger kế tiếp → code triển khai sau cùng.**
- Chốt: dùng springdoc annotation trong Java (`@Tag`/`@Operation` trên controller, `OpenApiConfig` cho `Info` chung) — không hand-maintain OpenAPI YAML riêng.
  UI ở `/swagger-ui.html`, JSON ở `/v3/api-docs`, tự sinh từ code, không có lệnh lint/verify riêng ngoài compile.

## 10. Comment và tài liệu ngắn, chỉ nêu kết quả cuối cùng
- Comment trong code (`//`, `#`, docstring) phải **ngắn** và chỉ nêu **hành vi/kết quả hiện tại** — không kể lại quá trình so sánh trước/sau ("trước làm X, đổi sang Y vì Z sẽ lỗi A"), không liệt kê phương án đã bị loại bỏ, không thuật lại lý luận dẫn tới code hiện tại.
- Trước khi stage/commit, đọc lại mọi comment mới thêm hoặc sửa trong diff.
  Rút còn 1-2 dòng nêu đúng "làm gì, vì sao (nếu lý do không hiển nhiên từ context)" — bỏ hẳn phần kể chuyện đối chiếu cũ/mới.
  Áp dụng cho cả comment class/module-level, không chỉ inline.
- Cùng quy tắc áp dụng cho **tài liệu** — `README.md`, `CHANGELOG.md`, và mọi doc khác trong repo: mục "Đã xong" chỉ liệt kê trạng thái/kết quả hiện tại, không kể lại diễn biến "trước đó làm X, phát hiện lỗi Y, sửa thành Z" — phần diễn biến đó (nếu cần) thuộc về commit message/PR description, không thuộc README/CHANGELOG.
- Lý do: user yêu cầu rõ rút gọn comment đã staged rồi mở rộng sang tài liệu, bỏ khung so sánh trước/sau ở cả hai — áp dụng như quy tắc chuẩn cho mọi lần sau, không phải chỉ một lần.

## 11. File `.md` viết theo kiểu mỗi câu một dòng
- Mọi file Markdown (`README.md`, `CHANGELOG.md`, `WORKFLOW.md`, doc trong `bi/`...) viết theo semantic line breaks: mỗi câu bắt đầu một dòng nguồn mới, không gói (wrap) nhiều câu chung một dòng dài rồi ngắt theo độ rộng cột tuỳ ý.
  Markdown coi các dòng liên tiếp không cách nhau bằng dòng trống là cùng một đoạn, nên khi render ra vẫn là một đoạn văn bình thường.
- Trong một bullet/list item, nếu có nhiều câu thì câu thứ hai trở đi xuống dòng mới (thụt lề theo item cha), không dồn chung dòng với câu đầu.
- Lý do: mỗi câu một dòng khiến diff khi sửa một câu chỉ đổi đúng dòng đó, không làm cả đoạn bị reflow và đổi trắng những dòng không liên quan — dễ review hơn nhiều trên git.

## Nguyên tắc cốt lõi
- **Đơn giản trước tiên** — thay đổi nhỏ nhất giải quyết được vấn đề; tối thiểu hoá code sinh ra.
- **Không lười biếng** — sửa tận gốc, không vá tạm.
- **Bám theo pattern đã có** — domain mới đi theo layout và convention của các domain đã có trong repo; xem phần Kiến trúc trong [../README.md](../README.md) một khi có shape cụ thể (`ResponseDTO`, exception handling, controller split...) để bám theo.

## Quy tắc riêng của dự án
- Java + Spring Boot, build bằng Maven (`./mvnw`), group ID **`HaUI.ImplSolution`**, artifact `DX-Farm`, base package `HaUI.ImplSolution.DX_Farm`.
- Có dùng Lombok: **có** — `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` trên JPA entity (mutable, cần no-arg constructor cho Hibernate).
  DTO vẫn ưu tiên Java record (đã bất biến, không cần Lombok) — chỉ dùng Lombok cho entity, không lạm dụng sang DTO.
- Package-by-layer: `controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`, `exception/` dưới base package — theo đúng skill `spring-boot`.
  Domain mới bám theo layout của `Parcel` (model/repository/service/controller + `dto/ParcelCreateRequest`, `dto/ParcelResponse`) làm ví dụ tham chiếu.
- Mọi response của controller bọc trong `dto/ApiResponse<T>` (record, static factory `success`/`error`) — kể cả lỗi, qua `exception/GlobalExceptionHandler`.
  Không trả entity JPA trực tiếp, không trả `Map`/raw object tuỳ tiện.
- Domain có business key rõ ràng (vd `Parcel.code` như "TD-01") thì route theo key đó (`/api/v1/parcels/{code}`), không theo surrogate `id` — surrogate id vẫn giữ nội bộ cho FK.
- Giữ `README.md` và `backend/CLAUDE.md` (khi có) đồng bộ khi thay đổi kiến trúc, route, hoặc trạng thái migrate.
- Không có cấu hình CircleCI trong repo này (`.circleci/` không tồn tại) — không tự tạo file CI trừ khi user yêu cầu rõ.
