# DX-Farm: ghi chú cải thiện và các vấn đề cần xử lý

hiện có một số cái cần note lại để cải thiện:

> Quy ước: Từ mục 6 trở xuống là các vấn đề phát hiện thêm, xem ghi chú ngay trước mục 6.

## 1. Về vấn đề farmer, engineer, admin truy cập ứng dụng:

- Tôi muốn farmer chỉ có thể truy cập trên mobile, trên web sẽ không hỗ trợ farmer.
    - Hiện backend chỉ nhìn role, không phân biệt token đến từ web hay mobile. Cần cấu hình Keycloak để client web/superset không cho role FARMER đăng nhập.
- Admin sẽ có toàn quyền, engineer sẽ bị hạn chế một số quyền.
    - Nên chốt thành một bảng quyền (ma trận role x hành động) rồi mới code, tránh vá từng chỗ.
    - Đề xuất thêm ý "phạm vi phụ trách": engineer chỉ thấy thửa của xã/khu vực được gán, admin thấy hết.
    - Admin vẫn đang tạo user bằng Keycloak Admin Console. Phải có luồng onboarding nông hộ rõ ràng: ai tạo tài khoản, ai tạo hồ sơ nông hộ, hai cái này gắn với nhau kiểu gì (xem mục 6).

## 2. Về vấn đề tạo thửa ruộng và vụ mùa:

- Farmer có thể tạo thửa ruộng ở trên mobile:
    - Mobile hiện chưa có màn hình tạo thửa, tạo vụ (app hiện chỉ đọc + gửi báo cáo), nên toàn bộ mục 2 là việc mới hoàn toàn trên mobile.
    - Tạo thửa/vụ lúc ở ruộng thường yếu sóng, nên cũng phải đi qua hàng đợi offline như báo cáo sâu bệnh (hiện hàng đợi chỉ phục vụ gửi ảnh), kèm Idempotency-Key cho các request tạo mới.
    - Mã thửa: Cần có một quy chuẩn đặt mã thửa và tôi muốn nó sẽ tự động hard cứng vào chứ không cho nông hộ nhập linh tinh. Tôi muốn bạn đề xuất cái này, dựa trên những dữ liệu về nông hộ mà hệ thống mình có
        - Hiện mã thửa là ô nhập tay tự do (backend chỉ cần `@NotBlank` và unique).
        - Đề xuất (backend tự sinh, farmer không nhập): `<MÃ HTX>-<MÃ HỘ>-T<STT thửa của hộ>`, ví dụ HTX01-H0042-T03. Mã HTX lấy từ cấu hình, mã hộ cấp tuần tự khi tạo hồ sơ nông hộ, STT thửa tăng dần theo từng hộ. Sinh trong cùng transaction + unique constraint để 2 request đồng thời không trùng.
        - Cái này cần có thực thể Nông hộ (mục 6) mới làm được, vì hiện tại hệ thống chỉ có mỗi cái tên (chuỗi) của hộ.
    - Tên nông hộ: Hard cứng theo tên trong tài khoản như hiện tại
        - Hard cứng theo tên thì có lỗ: so khớp bằng chuỗi tên (không phân biệt hoa thường), 2 farmer trùng tên thấy chung thửa, đổi tên trong Keycloak là mất thửa. Nên chuyển sang gắn theo id tài khoản (`sub`) qua bảng Nông hộ, vẫn hiển thị tên như cũ. Chi tiết ở mục 6.
    - Vị trí, kinh độ, vĩ độ: Tôi đang muốn bắt buộc cho 3 trường này để có thể xác định được vị trị thửa của nông hộ, đồng thời có thể view được ở trên superset cho trực quan. Bạn hãy gợi ý cho tôi phương án để có thể lấy được các thông tin này nhé
        - Phương án: lấy GPS ngay trên mobile khi farmer đang đứng ở ruộng (thêm expo-location, mobile hiện chưa có). Lưu thêm độ chính xác (mét), từ chối nếu sai số quá lớn (ví dụ > 30m), chặn/đánh dấu mock location. Có nút "đang ở ruộng, lấy vị trí" và hiển thị độ chính xác cho farmer thấy.
        - Hiện lat/lng là tùy chọn. Đổi thành bắt buộc thì dữ liệu cũ (thửa đã tạo không có tọa độ) phải xử lý: migration + đánh dấu "thiếu vị trí".
- Farmer có thể tạo vụ mùa trên mobile, vụ mùa sẽ bao gồm:
    - Mã vụ mùa: Cũng cần có một quy chuản đặt mã vụ mùa cho thống nhất, cái này bạn gợi ý cho tôi nhé
        - Hiện vụ mùa chưa có mã, chỉ có id số.
        - Đề xuất: `<MÃ THỬA>-<NĂM><LOẠI VỤ>`, ví dụ HTX01-H0042-T03-2026DX. Loại vụ ở ĐBSCL: Đông Xuân (ĐX), Hè Thu (HT), Thu Đông (TĐ). Nên tự gợi ý loại vụ theo tháng bắt đầu, và ràng buộc unique (thửa, năm, loại vụ) để chặn luôn việc tạo trùng vụ. Mốc tháng của từng vụ cần kỹ sư xác nhận lại trước khi hard cứng.
        - Đã check code: một thửa hiện có thể tạo nhiều vụ ACTIVE cùng lúc, không ràng buộc gì, và cũng không có ràng buộc `expectedHarvestDate >= startDate`. Ràng buộc unique ở trên giải quyết được phần lớn chuyện này.
    - Ngày bắt đầu và dự kiến thu hoạch: Dự kiến thu hoạch cần có một hệ thống gợi ý cho lúa, chứ không nên để linh tinh
        - Làm bảng danh mục giống lúa (mã giống, tên, số ngày sinh trưởng min-max, nguồn). Farmer chọn giống từ danh sách (hiện đang gõ tay, mặc định "OM 5451") rồi hệ thống tự gợi ý ngày thu hoạch = ngày bắt đầu + số ngày sinh trưởng.
        - Farmer chỉnh lệch quá khoảng cho phép thì bắt nhập lý do.
        - Số ngày sinh trưởng phải có nguồn (Viện Lúa ĐBSCL, Sở NN...), ghi trạng thái duyệt như cách đang làm với data/structured, không tự bịa số.
- Sau khi farmer tạo thửa và vụ mùa:
    - Cần được admin xét duyệt mới có thể tạo lên hệ thống, tránh trường hợp tạo linh tinh
        - Cần chốt trạng thái: chờ duyệt / đã duyệt / bị từ chối (kèm lý do) / đã hủy. Bị từ chối thì farmer sửa lại và gửi lại được, không phải tạo mới từ đầu.
        - Thửa đã được duyệt rồi mà farmer muốn sửa (diện tích, vị trí) thì sao? Nên có luồng "yêu cầu chỉnh sửa" cũng phải qua admin duyệt, nếu không farmer sửa thoải mái sau khi duyệt, mất ý nghĩa của bước duyệt.
        - Vụ mùa chỉ được tạo trên thửa đã APPROVED. Vụ mùa chưa duyệt thì farmer không được được báo sâu bệnh.
        - Chống tạo linh tinh: giới hạn số yêu cầu đang chờ duyệt của mỗi farmer, cảnh báo admin nếu vị trí GPS trùng/gần thửa của hộ khác.
        - Cần một bảng lịch sử chuyển trạng thái dùng chung cho thửa / vụ / báo cáo / chẩn đoán (xem mục 6 và mục 8).
    - Lúc có yêu cầu tạo thửa, vụ mùa mới tôi cũng muốn bắn một income webhook cho mattermost để admin có thể nắm được thông tin
        - Hiện Mattermost mới báo 2 sự kiện (báo cáo mới, chẩn đoán chờ duyệt) và chỉ vào một channel public duy nhất. Yêu cầu của admin nên vào channel private `Ban quản trị`, nên phải hỗ trợ nhiều webhook / định tuyến theo loại sự kiện (xem thêm mục 13).
- Sau khi farmer được admin phê duyệt thửa và vụ mùa:
    - Thửa sẽ được import vào superset để thể hiện trực quan
    - Các data của thửa cũng sẽ được import vào superset
        - Thêm cột trạng thái, tạo VIEW chỉ lấy bản ghi APPROVED, đổi quyền để `superset_ro` chỉ SELECT trên view và bỏ SELECT trên bảng gốc. Xem thêm mục 14.
- Engineer không thể tạo thửa ruộng, vụ mùa và các vấn đề ảnh hưởng đến nông hộ, chỉ có được chọn thửa và vụ mùa có sẵn để làm ở phần xét duyệt
    - Phải chặn ở backend bằng phân quyền theo hành động (xem mục 1, đã check code là hiện chưa chặn).

## 3. Về vấn đề quản lý của engineer và admin

- Admin cần có một chỗ để có thể xem được các yêu cầu tạo thửa ruộng và vụ mùa, kèm theo thông tin của farmer luôn
- Admin và engineer cần có một chỗ để xem được các danh sách báo cáo sâu bệnh chờ xét duyệt, đã xét duyệt,...
- Nói chung tôi đang cần một trang quản lý cho admin và engineer, hiện tại đang chưa có
    - Phần này lớn hơn nhìn bề ngoài, cần cả backend (API danh sách có phân trang + lọc) lẫn frontend (có router). Chi tiết ở mục 12.

## 4. Về vấn đề luồng xử lý ở block vụ mùa:

- Sau khi có thửa ruộng và vụ mùa:
    - Farmer: có thể bấm khai báo đã thu hoạch nhưng cần có pop up cảnh báo nếu chưa đến ngày hoặc một cảnh báo gì đó, chứ không phải bấm cái là được ngay. Đến ngày thu hoạch dự kiến cũng không tự động khai báo đã thu hoạch, mà nên lên kế hoạch bắn thông báo về app cho farmer biết là chưa thu hoạch khi đến ngày dự kiến và cho farmer chốt lại ngày dự kiến tiếp
        - Pop-up ở app chưa đủ. Nguyên tắc dự án là chặn 2 tầng (backend + frontend). Backend phải từ chối khai báo thu hoạch sớm nếu thiếu cờ xác nhận (ví dụ `confirmEarly=true` kèm lý do), rồi ghi lại để audit. Nếu chỉ popup thì gọi API thẳng là qua.
        - "Đến ngày dự kiến mà chưa thu hoạch thì bắn thông báo + cho farmer chốt ngày mới" cần 3 thứ hiện chưa có: (1) job chạy định kỳ ở backend (@Scheduled hoặc tương đương), (2) kênh thông báo tới app farmer (xem mục 13), (3) lịch sử gia hạn ngày dự kiến (lần 1, lần 2... lý do), để biết vụ nào bị dời liên tục.
        - Khai báo thu hoạch hiện chỉ lưu `harvestedAt = now`. Nên cho nhập ngày thu hoạch thực tế + sản lượng (kg hoặc tạ) + diện tích thu hoạch. Sau này làm carbon/năng suất cần mấy số này, giờ không thu thì sau không có để mà tính.
    - Admin / engineer: phần này không nên để engineer can thiệp, còn admin thì sẽ có cảnh báo kiểu dạng đây là thao tác của farmer, admin không nên thực hiện chẳng hạn
        - Admin bấm thay farmer: ngoài cảnh báo thì phải ghi rõ trong log là "thao tác thay mặt" (ai làm, thay cho ai, lý do), chứ không chỉ hiện popup.
        - Engineer bị chặn thật ở backend, không chỉ ẩn nút (xem mục 1).
- UI chỗ Hỏi: ruộng này còn cách ly không? (M7) đổi thành Ruộng này còn cách ly không?
    - Nút "Hỏi: ruộng này còn cách ly không?" ở web vẫn còn đó (frontend/src/components/CropSeasonPanel.tsx). Sửa chữ cho đúng ý trên, bỏ nhãn milestone.

## 5. Về vấn đề luồng nộp báo cáo sâu bệnh:

- Farmer:
    - Khi chụp ảnh, cần chạy một lượt AI dạng để đánh giá xem ảnh này đã đạt tiêu chuẩn: rõ nét, thấy rõ vết bệnh, có khả năng cho AI nhận dạng bệnh hay chưa,...
        - Mobile không được gọi thẳng AI (nguyên tắc client chỉ gọi Backend), nên cần endpoint backend kiểu "precheck" (không lưu DB, chỉ trả đạt / không đạt + lý do). Ngoài ra nên có thêm vài check nhẹ ngay trên máy (mờ, tối, quá nhỏ) cho nhanh, đỡ tốn request.
        - Phía server vẫn phải kiểm lại khi nộp thật, vì client có thể bỏ qua bước precheck. Lưu kết quả đánh giá chất lượng vào báo cáo.
        - Ngưỡng độ nét hiện tại (`MIN_SHARPNESS = 10`) rất thấp, ảnh mờ vừa vẫn lọt. Cần đo lại bằng ảnh thật chụp ngoài ruộng rồi chỉnh ngưỡng.
    - Nếu không đạt yêu cầu về ảnh, phải yêu cầu farmer chụp lại ngay chứ không nên để gửi báo cáo rồi bên engineer chạy AI không nhận diện được
    - Nếu ảnh không liên quan đến lúa, hoặc nó linh tinh quá cũng yêu cầu chụp lại luôn
        - Vấn đề lớn: model YOLO hiện tại không có lớp "không phải lúa / ảnh linh tinh". Khi không có detection nào đạt ngưỡng thì AI trả `Rice___healthy` (fallback). Tức là ảnh chụp con mèo, cái bàn, ảnh mờ đều có thể ra kết quả "lúa khỏe".
        - Muốn bắt được "ảnh không liên quan đến lúa" thì phải bổ sung một lớp lọc riêng (classifier lúa / không phải lúa, hoặc kiểm tra độ tin cậy, hoặc huấn luyện thêm lớp background). Đây là việc của AI, không làm bằng popup được.
        - Báo cáo tên là "sâu bệnh" nhưng model chỉ nhận 5 bệnh trên lá lúa. Sâu hại (rầy nâu, sâu cuốn lá, ốc bươu vàng...) không có lớp nào. Cần đổi tên thành "báo cáo bệnh".
- Engineer:
    - Về luồng nhập tay kết quả CV để kiểm thử nghiệp vụ, tôi muốn đổi thành kiểu là nếu AI không nhận diện được, sẽ có kỹ sư chọn loại bệnh (nếu có) hoặc nhập bệnh / triệu chứng vào, sau đó AI sẽ dựa trên text kỹ sư nhập để có thể đoán được bệnh và ghi nhận triệu chứng, từ đó gợi ý các phòng ngừa
        - Chỗ dễ vi phạm nguyên tắc deterministic-first / LLM không tự sinh kết luận. Đề xuất: text của kỹ sư chỉ dùng để truy xuất (RAG) và xếp hạng ứng viên trong danh sách bệnh có sẵn (hiện chỉ 6 mã), kỹ sư chốt bệnh từ danh sách đó; LLM chỉ diễn đạt lại phần phòng ngừa từ nguồn đã ingest.
        - Kết quả luôn phải là một `disease_code` hợp lệ trong danh mục, không để LLM tự nghĩ ra bệnh mới. Triệu chứng kỹ sư nhập nên lưu có cấu trúc để sau dùng làm dữ liệu huấn luyện.
        - Đã check code: đường nhập tay hiện luôn đòi phải có treatment rule active cho bệnh đó, mà seed sạch chưa có rule nào, nên trên database mới đường này lỗi 404. Sửa cùng lúc khi đổi luồng (xem mục 11).
    - Engineer cũng có thể yêu cầu farmer chụp lại ảnh trong một số trường hợp cần xem chi tiết hơn mà AI không detect được. Nên yêu cầu chụp lại kèm một đoạn text mô tả lý do yêu cầu chụp lại. Bên farmer app sẽ có thông báo cho cái này
        - Hiện app farmer hiểu "Cần chụp lại" chính là trạng thái REJECTED của chẩn đoán, trộn lẫn 2 chuyện khác nhau: (1) ảnh không dùng được, (2) kỹ sư không đồng ý khuyến nghị. Nên tách trạng thái riêng cho báo cáo (ví dụ NEEDS_RETAKE + lý do).
        - Báo cáo mới liên kết tới báo cáo cũ (retakeOf) để xem được cả chuỗi. Farmer nhận thông báo qua kênh ở mục 13.

## 6. Danh tính nông hộ và mô hình dữ liệu:

- Hiện không có thực thể Nông hộ. Quan hệ thửa - chủ hộ chỉ là chuỗi tên (`Parcel.farmerName`) so khớp với claim `name` trong token, không phân biệt hoa thường (đã check code: CurrentUserService.canAccess). 
   - Hậu quả: 2 farmer trùng tên thấy chung thửa của nhau, đổi tên trong Keycloak là mất thửa, lệch dấu/chính tả cũng mất. Chính docs (PLAN.md mục rủi ro) cũng đã ghi cái này là rủi ro.
- Đề xuất: bảng Household (mã hộ, họ tên, SĐT, địa chỉ, `keycloakUserId` = claim `sub`), Parcel trỏ tới Household bằng khóa ngoại. Quyền truy cập dựa trên `sub`, không dựa trên tên nữa. Mã thửa/mã vụ ở mục 2 cũng dựa vào đây.
- Audit hiện lưu `reportedBy`, `decidedBy` bằng tên hiển thị (không lưu `sub`/username). Đổi tên là mất dấu vết, 2 người trùng tên không phân biệt được. Nên lưu thêm id người dùng.
- Bảng lịch sử chuyển trạng thái không bị ghi đè + `assignedTo` (kỹ sư phụ trách), dùng chung cho thửa / vụ mùa / báo cáo / chẩn đoán: xem mục 8.
- Chưa có xóa mềm / hủy bản ghi. Nhập nhầm thì không có đường để hủy, chỉ còn sửa DB tay.
- Dữ liệu cá nhân nông hộ (tên, SĐT, vị trí, ảnh) nên có ghi chú về thu thập tối thiểu, đồng ý của người dùng và thời gian lưu. Không cần làm to, nhưng nên có trong tài liệu vì có phần thuyết trình về dữ liệu.

## 7. Kiến trúc Open-Core, Tầng 1 [Nghiêm trọng] (đề mục 1, 3, 4.1):

- Đề bắt buộc làm lõi Open-Core headless trước, ứng dụng nghiệp vụ phải khai thác dịch vụ của lõi, chứ không coi website / demo nghiệp vụ là sản phẩm chính. Không chứng minh được Core headless hoặc chỉ làm một app CRUD độc lập thì bị trừ mạnh ở tiêu chí kiến trúc (10 điểm), thậm chí không đạt điều kiện tuyển chọn (đề mục 11, 12). Nên đây là vấn đề lớn nhất về kiến trúc.
- Ranh giới Core và DX-Farm chưa rõ:
    - Hiện trạng: backend đang là một khối gộp nghiệp vụ nông nghiệp (Parcel, CropSeason, DiseaseReport, Diagnosis) với dịch vụ dùng chung, chưa chỉ ra được cái nào giữ nguyên khi đổi sang ứng dụng khác. Đề ghi rõ "Core không chỉ là mở giao diện CRUD".
    - Hướng xử lý: vẽ kiến trúc ba tầng, chỉ rõ đâu là Core, đâu là H-P-D-I, đâu là ứng dụng; nói được thành phần nào không phải đổi khi thay ứng dụng nghiệp vụ. Đây cũng là câu hỏi số 1 và số 4 của hội đồng (mục 20).
- Identity / SSO / RBAC: có (Keycloak). Phân quyền còn lỏng, xem mục 9.
- API Gateway hoặc lớp API trung gian [Quan trọng]:
    - Hiện trạng: yếu. Nginx với Spring Backend có thể coi là lớp trung gian, nhưng chưa ai mô tả luồng truy cập công khai / nội bộ. Client đang gọi thẳng backend (8081), AI (8000), Keycloak; nginx của frontend chỉ proxy /api và /uploads.
    - Đề ghi "hoặc tương đương" nên không bắt buộc APISIX, nhưng cần một cửa chung có kiểm soát: kiểm JWT, rate limit, routing / version, không để lộ AI và internal API. Gợi ý Apache APISIX chế độ standalone (cấu hình bằng file yaml, không cần etcd, Apache-2.0, đúng gợi ý của đề), hoặc Traefik / Nginx.
    - Làm cái này giải quyết luôn mấy chuyện ở mục 9 và mục 17 (cổng expose ra host, AI không auth, rate limit). Dù chọn gì thì cũng phải vẽ luồng request, chỉ rõ Nginx, Spring Security, API công khai, internal API và service API key.
- Dữ liệu cấu trúc / phi cấu trúc: cấu trúc có Postgres. Phi cấu trúc là ảnh, đang lưu thẳng thư mục local, public không auth. Đề ghi "CSDL, object/file storage, metadata". Gợi ý MinIO (AGPL-3.0, OSI) hoặc tối thiểu bọc thành một "storage service" có metadata (ai upload, loại, nguồn) và link có hạn. Cùng lúc xử lý được chuyện upload và /uploads công khai ở mục 9.
- Workflow Engine: xem mục 8 (đây là chỗ yếu nhất so với đề).
- Sơ đồ kiến trúc + luồng gọi dịch vụ chứng minh ứng dụng dùng Core: chưa có. Cần 1 sơ đồ 3 tầng và 1 sơ đồ luồng request (người dùng, gateway, Core, H-P-D-I, kết quả). Cũng là nội dung của báo cáo, slide và demo.

## 8. Workflow / quy trình xử lý, phần Process [Nghiêm trọng] (đề mục 4.1 và 4.2):

- Đề yêu cầu Workflow Engine "quản lý trạng thái, luồng xử lý / phê duyệt và lịch sử nghiệp vụ", và phần P phải có "ít nhất 1 quy trình có trạng thái, người tạo, người xử lý, thời gian và lịch sử thay đổi; có cơ chế theo dõi yêu cầu chờ xử lý".
- Hiện trạng: có trạng thái, người tạo, người quyết định, thời gian, nhưng chỉ là enum + if trong service. Thiếu lịch sử chuyển trạng thái (không bị ghi đè), thiếu người được giao xử lý (`assignedTo`), thiếu danh sách việc đang chờ, và không tái dùng được cho quy trình khác. Thiếu đúng 3 trên 5 ý đề yêu cầu.
- Hướng xử lý: thêm bảng lịch sử xử lý (WorkflowEvent), `assignedTo`, API danh sách chờ và timeline trên giao diện. Làm một bảng dùng chung cho thửa / vụ mùa / báo cáo / chẩn đoán (và phiếu CNTT nếu có ứng dụng thứ hai ở mục 22). Hai hướng:
    - (1) Nhúng Flowable (Apache-2.0, đề có gợi ý, có sẵn lịch sử, task, assignee, danh sách chờ).
    - (2) Tự làm bảng workflow_case / workflow_event kèm định nghĩa quy trình dạng cấu hình dùng chung.
- Cần chốt hướng sớm vì các mục 2, 3, 6, 12 đều đè lên cái này. n8n đề có nhắc nhưng đã loại vì license, giữ nguyên.

## 9. Phân quyền và bảo mật (đã check code):

- Phân quyền theo hành động ở backend: đã ghi ở mục 1.
- `/uploads/**` công khai không cần đăng nhập. Tên file là UUID khó đoán nhưng link đã bị đưa vào thông báo Mattermost và gửi cho AI. Ảnh ruộng của nông hộ nên cần token hoặc link có hạn (signed URL), nhất là khi đã có GPS.
- Upload chỉ kiểm tra `Content-Type` do client gửi, và lấy đuôi file từ tên file client. Chưa kiểm tra nội dung thật (magic bytes), chưa giới hạn kích thước/chiều ảnh ở server ngoài 10MB của multipart. Cần đọc ảnh lại, chuẩn hóa đuôi, và xóa EXIF (EXIF có thể chứa GPS; chưa thấy chỗ nào backend xóa).
- AI Service: `/api/v1/disease/predict` và `/knowledge/retrieve` không có xác thực, và cổng 8000 đang publish ra host. Chỉ `/diagnose` có service API key. Nên bắt buộc key cho mọi endpoint, hoặc không publish cổng AI ra ngoài, chỉ cho mạng nội bộ compose (làm chung với cửa API ở mục 7).
- Secret và mật khẩu mặc định (đề trừ điểm nghiêm trọng, yêu cầu khắc phục trước demo, đề mục 5 và 12):
    - Chỗ dễ bị tính là vi phạm: `sso/realm-dxfarm.json` (mật khẩu tài khoản demo farmer123 / engineer123 / admin123 và secret client Superset nằm thẳng trong file được commit), `.env.example` điền sẵn mật khẩu (Keycloak123, Superset123, Mattermost123, AiVectorRo123...), giá trị mặc định trong application.yaml (ví dụ `keycloak_demo_only`).
    - Đây là mật khẩu demo nhưng công cụ quét secret vẫn báo. Còn nguy hiểm hơn là nếu quên điền biến thì app vẫn chạy với mật khẩu yếu thay vì dừng lại.
    - Hướng xử lý: để trống hoặc sinh ngẫu nhiên lúc dựng; bỏ default ở yaml cho nhất quán với compose (compose đã bắt buộc khai báo); tài khoản demo ghi rõ trong README là demo; chạy gitleaks hoặc tương đương và rà cả lịch sử commit trước khi public.
- Keycloak đang chạy `start-dev`, realm để `sslRequired: none`, không bật chống brute force, không có password policy, không có hết hạn/giới hạn phiên rõ ràng. OK cho demo nhưng phải có bước "chuyển sang chế độ chạy thật" ghi rõ trước khi chấm.
- Postgres publish cổng 5432 ra host. Với môi trường thật nên đóng cổng.
- Log mức DEBUG cho package của app đang là mặc định trong application.yaml, cần chỉnh theo profile.
- CORS chỉ cho phép localhost; khi có domain/HTTPS thật phải cấu hình lại. Chưa có rate limit cho API nào, kể cả upload ảnh và gọi AI (gọi AI tốn tài nguyên, một tài khoản spam là nghẽn).
- Backend chỉ xác thực chữ ký token (`jwk-set-uri`), không kiểm `iss` và `aud`. Đã ghi là đánh đổi có chủ đích trong sso/README.md, nhưng nên có `aud` riêng cho API khi lên chạy thật.

## 10. Lỗ hổng nghiệp vụ / poka-yoke (đã check code, mấy cái này ảnh hưởng độ tin cậy của "khóa PHI" mà dự án lấy làm điểm nhấn):

- Ghi nhận phun thuốc (`apply`) nhận `appliedAt` tùy ý, không có `@PastOrPresent`, không giới hạn lùi bao nhiêu ngày. Khai ngày phun lùi xa về trước thì `harvestLockedUntil` rơi vào quá khứ và khóa thu hoạch coi như vô hiệu. Ngược lại khai ngày tương lai thì khóa kéo dài vô lý. Cần ràng buộc ngày hợp lệ (không tương lai, lùi tối đa N ngày) và ghi lại ai khai.
- `LocalDate.now()` trong CropSeasonService không chỉ định múi giờ. Dockerfile không set TZ nên container nhiều khả năng chạy UTC, trong khi người dùng ở UTC+7 (cấu hình Jackson và thông báo Mattermost lại dùng Asia/Ho_Chi_Minh). Ngày hết cách ly có thể lệch tới nửa ngày. Nên dùng một Clock/ZoneId thống nhất cho toàn backend.
- Khai báo thu hoạch chỉ bị chặn khi đã có bản ghi phun thuốc còn hạn. Nếu chẩn đoán đã APPROVED mà chưa ghi nhận phun (farmer chưa phun hoặc kỹ sư chưa nhập), hoặc còn báo cáo đang PENDING, farmer vẫn thu hoạch được ngay. Cần chốt: có nên chặn/cảnh báo khi còn khuyến nghị đã duyệt mà chưa xử lý không.
- Vụ mùa đã HARVESTED vẫn nhận báo cáo sâu bệnh mới (không check trạng thái vụ khi tạo báo cáo).
- Một thửa có thể tạo nhiều vụ ACTIVE cùng lúc, không có ràng buộc gì. Cũng không có ràng buộc `expectedHarvestDate >= startDate`.
- Người thực sự phun thuốc là nông dân, nhưng xác nhận "đã phun" hiện là việc của kỹ sư (UI mobile còn ghi "Ghi nhận việc phun thuốc do kỹ sư thực hiện"). Thực tế kỹ sư không đứng ở ruộng. Nên cho farmer tự khai "đã phun" (kèm ảnh/giờ), kỹ sư xác nhận lại. Đây là chỗ khóa PHI phụ thuộc vào dữ liệu có thật hay không.
- Mỗi chẩn đoán chỉ có 1 rule và 1 lần áp dụng (`apply` chặn lần thứ hai). Thực tế có phun lặp lại nhiều đợt cách nhau vài ngày. Cần chốt có hỗ trợ không, vì khóa PHI lấy theo bản ghi phun mới nhất.
- Giao dịch ghi/đọc đồng thời: approve, reject, apply chưa có khóa lạc quan (`@Version`) nên 2 người bấm cùng lúc có thể ghi đè nhau.

## 11. Luồng chẩn đoán và AI [Quan trọng, phần đánh giá AI] (đề mục 4.2 phần I):

Cập nhật phạm vi ngày 06/10/2026: người dùng chốt confidence 0.70 và bỏ phần dataset/công cụ/báo cáo chỉ số đánh giá khỏi Phase 6. Các nhận xét đánh giá bên dưới là đề xuất ban đầu, không phải việc còn chờ để đóng Phase 6.

- Một báo cáo chỉ có tối đa 1 diagnosis (`createFromAi` ném lỗi nếu đã có). Nếu kỹ sư không đồng ý kết quả AI hoặc cần chạy lại sau khi farmer chụp ảnh mới, không có đường chạy lại trên cùng báo cáo. Chốt: cho phép nhiều lần chẩn đoán (có lịch sử) hay bắt buộc báo cáo mới.
- Đường "nhập tay" (`POST .../diagnosis`) luôn đòi phải có treatment rule active, seed sạch chưa có rule nào nên đường này lỗi 404 trên database mới. Đã ghi ở mục 5, sửa cùng lúc khi đổi luồng nhập tay. Gốc rễ là chuyện thiếu dữ liệu thuốc / rule ở mục 18.
- `createFromAi` gọi AI (timeout đọc 120 giây) ngay bên trong `@Transactional`, giữ một kết nối DB suốt thời gian đó. Vài kỹ sư bấm cùng lúc là cạn pool kết nối. Nên gọi AI ngoài transaction rồi mới mở transaction để lưu.
- Backend tự gọi AI sau khi upload vẫn đang giữ thủ công như đã chốt trước đó, ghi lại ở đây cho đủ: khi làm thì kết hợp với bước precheck ở mục 5 để ảnh đã qua kiểm tra mới chạy.
- Chưa có vòng phản hồi để cải thiện model: kỹ sư sửa/chốt bệnh nhưng nhãn chốt không được lưu riêng để dùng làm dữ liệu huấn luyện về sau. Nên lưu (ảnh, nhãn AI, nhãn kỹ sư chốt, có đồng ý của nông dân) để có bộ dữ liệu thực tế.
- Đánh giá AI chưa đủ [Quan trọng]:
    - Hiện trạng: AI có vai trò thật trong quy trình nhưng mới có validation metrics. Chưa có test set độc lập, chỉ số theo từng lớp, confusion matrix, phân tích lỗi điển hình. Ngưỡng chuyển cho người xem tay (`0.70`) chưa có căn cứ đo.
    - Đề yêu cầu nêu rõ tên mô hình / công cụ, phiên bản, giấy phép, dữ liệu đầu vào, giới hạn của kết quả, có căn cứ giải thích. Hiện có CV (YOLOv8n), RAG, LLM (Qwen2.5-1.5B).
    - Hướng xử lý: làm một báo cáo đánh giá ngắn gồm dữ liệu đầu vào, metric hiện có, giới hạn, false positive / false negative điển hình, trường hợp bắt buộc chuyển cho người xem. Không bắt buộc phải có model card. Nhớ ghi AGPL-3.0 của Ultralytics trong bảng license (mục 19).
- Nguồn RAG phần lớn còn trạng thái IN_REVIEW; thuốc / treatment rule / policy chưa có bản ghi nào được duyệt. Việc này cần chuyên gia, không code được, nên cần lên kế hoạch sớm vì nó chặn cả demo trọn luồng (mục 18).

## 12. Trang quản lý cho admin / engineer (mở rộng mục 3):

- Backend chưa có endpoint "liệt kê tất cả" nào cho báo cáo; chỉ có theo vụ mùa. Danh sách thửa thì `findAll` không phân trang, không lọc. Cần endpoint list có phân trang + lọc (trạng thái, thửa, hộ, ngày, người phụ trách) cho: yêu cầu tạo thửa, yêu cầu tạo vụ, báo cáo sâu bệnh, chẩn đoán chờ duyệt. Đề phần D cũng đòi có tìm kiếm / lọc / thống kê.
- `listByCropSeason` đang truy vấn diagnosis riêng cho từng báo cáo (N+1), đã ghi trong CHANGES_OVERVIEW. Làm list toàn cục thì phải sửa trước, nếu không sẽ chậm.
- Frontend hiện là một trang duy nhất (App.tsx), không có router, chọn thửa -> vụ -> báo cáo tuần tự. Trang quản lý cần: router, menu theo role, bảng danh sách có lọc, trang chi tiết (xem thông tin nông hộ + ảnh + kết quả AI + lịch sử), hộp "việc của tôi" cho kỹ sư. Nên tách rõ khu vực Admin và khu vực Engineer.
- Deep link từ Mattermost sang báo cáo (đã có thiết kế ở mục 9 của `pending_docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`, chưa làm) sẽ gắn luôn vào router này.
- Cần màn hình xem lịch sử xử lý (timeline, dựa trên bảng ở mục 8) và nhật ký thao tác của admin, để truy vết ai duyệt cái gì.
- SLA xử lý: báo cáo chờ quá N giờ chưa có ai xem thì nhắc kỹ sư/admin (dùng chung job định kỳ ở mục 4 phần thu hoạch).
- Thống kê nhanh cho admin: số yêu cầu chờ duyệt, số báo cáo theo bệnh/tuần, số vụ sắp thu hoạch, thửa đang cách ly. Nhiều thứ trong này có thể đưa sang Superset thay vì tự code, cần chốt cái gì làm trong app, cái gì để BI.
- Quản lý người dùng đang phải vào Keycloak Admin Console. Nếu muốn admin thao tác ngay trong trang quản lý (tạo hộ + tạo tài khoản + gán role) thì backend phải gọi Keycloak Admin REST API bằng service account riêng.

## 13. Thông báo:

- App farmer hiện không có kênh nhận thông báo nào (mobile chưa có thư viện thông báo; BACKEND_CHANGES.md còn ghi rõ "không thêm endpoint đăng ký device token hay push"). Mấy yêu cầu mục 4, 5 (nhắc ngày thu hoạch, yêu cầu chụp lại, kết quả duyệt thửa/vụ) bắt buộc phải mở lại quyết định này.
- Cần lưu ý PoF: push thông thường đi qua FCM/APNs/Expo push service, đều là dịch vụ đóng. Phương án thay thế mã nguồn mở: ntfy (Apache-2.0) / UnifiedPush; hoặc đơn giản nhất cho MVP: "hộp thông báo" trong app (bảng Notification ở backend, app kéo/poll khi mở và khi quay lại foreground). Nên làm hộp thông báo trước (không phụ thuộc dịch vụ ngoài), push thêm sau như phần tiện lợi.
- Mattermost bây giờ chỉ báo 2 sự kiện (báo cáo mới, chẩn đoán chờ duyệt). Cần thêm: yêu cầu tạo thửa, yêu cầu tạo vụ, khai báo thu hoạch (đặc biệt thu hoạch sớm / admin làm thay), yêu cầu chụp lại, SLA quá hạn.
- Kênh nhận thông báo đang là một channel public duy nhất. Yêu cầu của admin nên vào channel private `Ban quản trị`, báo cáo sâu bệnh vào channel kỹ sư, tức là phải hỗ trợ nhiều webhook/định tuyến theo loại sự kiện.
- Các hạn chế đã biết (ghi lại cho đủ): không retry/outbox, hàng đợi trong RAM, không cập nhật tin cũ khi đã xử lý, cấp webhook là thao tác tay.

## 14. BI / Superset và dữ liệu mở (đề mục 4.2 phần D, đề mục 8):

- Chỉ cho bản ghi đã duyệt lên Superset (VIEW + đổi quyền `superset_ro`): đã ghi ở mục 2. Chặn FARMER vào Superset: đã ghi ở mục 1.
- Dashboard mới có 2 chart (bản đồ điểm, pie vụ mùa). Cần thêm: số báo cáo theo bệnh theo thời gian, bản đồ điểm nóng dịch bệnh (kết hợp GPS bắt buộc ở mục 2), thửa đang cách ly, lịch thu hoạch dự kiến, tỷ lệ AI đúng/sai (so với kỹ sư chốt).
- Bản đồ vẫn chưa có nền (basemap). Bài toán nhãn Hoàng Sa/Trường Sa đã ghi trong bi/README.md, vẫn chưa có hướng ra. Nếu GPS thành điểm nhấn của demo thì nên chốt giải pháp sớm (ví dụ tự host tile tối giản, hoặc nền vệ tinh không nhãn).
- Metadata của Superset là SQLite trong deploy/data/superset (chỉ hợp demo 1 máy). Sửa dashboard rồi phải nhớ export lại zip commit vào repo, không thì dựng lại là mất.
- Superset 3.1.3 là bản khá cũ, nên xem lại bản vá bảo mật trước khi chạy thật. Gói Authlib cài lúc khởi động (`pip install` mỗi lần chạy) chưa pin phiên bản (mục 19).
- Dữ liệu mở: đề khuyến khích tích hợp tối thiểu 02 nguồn dữ liệu mở có nguồn gốc và giấy phép, kèm snapshot để demo offline khi API ngoài ngừng hoạt động. Hiện có dataset ảnh bệnh lúa (CC BY 4.0) và corpus RAG 9 nguồn. Nên thêm một nguồn dạng sống như thời tiết (ví dụ Open-Meteo) cho dự báo nguy cơ sâu bệnh hoặc gợi ý lịch thu hoạch, có snapshot dự phòng.
- Phần D của đề còn đòi nguồn dữ liệu thống nhất, metadata / provenance, API REST cho ít nhất một nhóm dữ liệu: hiện đã có Postgres, provenance cho RAG, Swagger của backend. Thiếu tìm kiếm / lọc / thống kê trong app (mục 12).

## 15. Mobile:

- Chưa có màn hình tạo thửa, tạo vụ, khai báo thu hoạch (app hiện chỉ đọc + gửi báo cáo), nên phần lớn mục 2 và 4 là việc mới hoàn toàn trên mobile.
- Dữ liệu đọc (danh sách thửa/vụ/báo cáo) không có cache offline; chỉ hàng đợi gửi ảnh là chạy được khi mất mạng. Ra ruộng mất sóng mở app sẽ trống. Nên cache bản đọc gần nhất.
- Chưa có thông báo (mục 13), chưa có lấy vị trí (mục 2), chưa có hướng dẫn chụp ảnh trực quan (khung ngắm, gợi ý chụp gần/đủ sáng/thấy rõ vết bệnh) ngoài một dòng chữ.
- Mỗi thẻ thửa gọi thêm 2 request để hiện badge cách ly; nhiều thửa sẽ chậm, cần endpoint gộp (đã ghi nhận trong CHANGES_OVERVIEW).
- Request thua khi hai lần gửi cùng key chạy đồng thời để lại file ảnh mồ côi trong uploads. Cần dọn định kỳ (tìm file không còn báo cáo nào trỏ tới).
- Icon và splash vẫn là ảnh mặc định của Expo.
- Địa chỉ backend và Keycloak (IP LAN) bị nhúng cứng vào file APK lúc build, đổi mạng là build lại. Muốn phát cho nông dân thật cần domain + HTTPS (profile production đang chặn HTTP). Đây là chặn lớn nhất cho việc đưa app ra ngoài demo.
- Build APK đang đi qua EAS (dịch vụ đám mây của Expo). PoF yêu cầu build được từ source, nên phải có hướng dẫn build local (có ghi một dòng trong README nhưng chưa kiểm chứng) và nhớ ghi rõ EAS chỉ là tiện ích.
- Checklist kiểm thử tay trên điện thoại thật trong PLAN.md toàn bộ chưa tick. Mục "chạy backend, chạy test, chạy app trên thiết bị" cũng ghi là chưa thực hiện. Chưa có bằng chứng app chạy đúng đầu-cuối trên máy thật.
- Người dùng là nông dân lớn tuổi: nên xét cỡ chữ lớn, nút to, nội dung đơn giản, có hướng dẫn lần đầu, hỗ trợ đọc thông báo bằng giọng nói nếu có thời gian. Chưa có log lỗi/crash report phía app.

## 16. Web frontend:

- Chỉ một trang, không router, không state management; thêm trang quản lý (mục 12) thì nên refactor luôn.
- Web vẫn cho FARMER đăng nhập và dùng (trái với mục 1).
- Chưa có test nào cho frontend, mobile (chỉ có tsc/expo export).
- Token OIDC lưu ở `sessionStorage`; chấp nhận được cho demo nhưng ghi rõ giới hạn khi nói về bảo mật.
- Thông báo lỗi lấy thẳng từ backend (đang là tiếng Anh) hiện ra cho người dùng. Cần chuẩn hóa mã lỗi + bản dịch tiếng Việt ở FE/mobile (đề mục 9 ưu tiên giao diện tiếng Việt).

## 17. Deploy / CI / hạ tầng:

- CI (.github/workflows/ci.yml) chỉ chạy khi `backend/**` đổi, chỉ kiểm format và `verify -DskipTests`, tức là không chạy test nào. Frontend, mobile (tsc/expo export), AI (pytest, các script check), `docker compose config`, build image, quét secret đều chưa có trong CI. Cần bật test trong CI (liên quan mục 18).
- Dựng và chạy từ một clone sạch [Quan trọng] (đề mục 5: build/install from source, 10 điểm; câu hỏi hội đồng "build toàn bộ từ source trên máy mới được không"):
    - Hiện chưa có bằng chứng clone mới, dựng toàn bộ Compose rồi chạy trọn kịch bản. Nên viết thành quy trình chạy được thật: clone, cp .env.example, up, có tài khoản demo, có dữ liệu mẫu, và kiểm chứng từ một clone/tag sạch.
    - Chưa có script "reset về máy mới". Tự mình vừa xóa data xong mới thấy: nhiều thư mục trong deploy/data thuộc root/uid khác (mattermost, postgres) nên không xóa được nếu không có sudo hoặc chạy qua container. Nên có sẵn một script an toàn (và hướng dẫn trong README) để dựng lại từ đầu, cũng phục vụ demo lặp lại nhiều lần.
    - `docker compose up` đầu tiên phải tải/build rất nặng: image ai ~3.2GB, ollama ~9GB (+ model Qwen). Cần ghi rõ yêu cầu máy (RAM/đĩa/GPU hay CPU), thời gian dựng lần đầu, và phương án nhẹ hơn (ví dụ chạy không Ollama, dùng nhánh fallback) cho máy yếu / máy giám khảo.
- Chưa có chiến lược backup/restore cho deploy/data (Postgres, Mattermost, Superset, ảnh upload). Cần ít nhất một script pg_dump + sao lưu thư mục ảnh + hướng dẫn khôi phục. Hiện nếu mất deploy/data là mất tất cả.
- Chưa có reverse proxy + HTTPS thống nhất (Caddy/Traefik/nginx; có thể gộp với cửa API ở mục 7). Cần cho mobile production, cho CORS, cho Keycloak hostname (đang phải dùng thủ thuật không đặt KC_HOSTNAME để web và điện thoại cùng vào được).
- Chưa có giám sát/log tập trung (không cần nặng; ít nhất healthcheck cho tất cả service, giới hạn tài nguyên container, log rotation). Backend/AI/Superset có healthcheck, một số service khác thì chưa. Prometheus/Grafana đề có gợi ý, không bắt buộc.
- Port expose ra host cho hầu hết service (Postgres, AI, Keycloak, Superset...). Với môi trường thật chỉ nên expose proxy (mục 7).
- Phiên bản image: nhiều thứ dùng tag cố định (tốt), nhưng `latest`/pip chưa pin ở vài chỗ (Python chưa có lockfile). Cần khóa để build lại ra cùng kết quả.

## 18. Test, E2E và dữ liệu demo [Quan trọng] (đề mục 7 và 9):

- Đề yêu cầu "kiểm thử tối thiểu cho các chức năng lõi", trong khi quy ước hiện tại của dự án là không viết test trừ khi được yêu cầu, và backend chỉ có 2 file test (TreatmentRuleTest, CatalogActiveFilterTest). Nghiệp vụ lõi (state machine duyệt, khóa PHI, phạm vi dữ liệu FARMER, idempotency, workflow) chưa có test tự động. Cần quyết định bật lại việc viết test cho phần lõi. Đề đã yêu cầu thì coi như được yêu cầu.
- Chưa có kiểm chứng đầu-cuối trên môi trường sạch [Quan trọng]: các module có test riêng nhưng chưa có E2E. Cần một kịch bản tự động: dựng compose sạch -> tạo hộ/thửa/vụ -> báo cáo -> chẩn đoán -> duyệt -> phun -> thu hoạch bị chặn; chạy được từ clone/tag sạch (mục 17) và có trong CI.
- Dữ liệu dựng mới chưa chạy hết luồng demo [Quan trọng]:
    - Hiện trạng: seed có bệnh nhưng chưa có thuốc, treatment rule, policy nào được duyệt, nên trên database sạch chưa demo trọn luồng áp dụng thuốc và khóa PHI. Cũng không có thửa mẫu ở ĐBSCL có tọa độ thật, không có báo cáo mẫu kèm ảnh. Đề yêu cầu có dữ liệu mẫu đủ để Hội đồng chạy thử.
    - Hướng xử lý: có ít nhất một rule được kiểm chứng và phê duyệt để seed. Nếu chưa kịp thì demo cho trung thực nhánh `review_required`, đừng giả vờ là có.
    - Làm script seed demo riêng (tách với seed nghiệp vụ), chạy một lệnh là có sẵn vài hộ, vài thửa, vài vụ ở các trạng thái khác nhau.
- Checklist kiểm thử tay mobile chưa chạy (xem mục 15).
- Bộ ảnh test cho AI: cần ảnh thật chụp bằng điện thoại ngoài ruộng (mờ, ngược sáng, ảnh không phải lúa) để đo precheck ở mục 5.

## 19. Tài liệu, license, PoF và hồ sơ nộp bài [Quan trọng / Theo dõi] (đề mục 5, 10, 11, 12; rubric chia 50 điểm PoF rất cụ thể):

- Tài liệu chưa khớp với thực tế [Quan trọng]: README gốc còn mô tả M1 đến M6 chạy đầu-cuối trong khi seed sạch chưa chạy hết happy path; một số mục "Chưa làm" trong README/CHANGELOG đã cũ (ví dụ nói chưa có SSO); checklist cũ còn nhắc tới model card đã bỏ. Cần đồng bộ README, CHANGELOG, checklist sau mỗi phase, và phân biệt rõ ba mức: "đã có trong code", "đã kiểm thử", "đã có dữ liệu demo". Giữ quy ước: chỉ ghi trạng thái cuối, không so sánh trước/sau.
- README theo yêu cầu của đề: phải mô tả kiến trúc 3 tầng, H-P-D-I, tính năng, cài đặt, cấu hình, chạy thử, API, tài khoản demo. README hiện thiên về tiến độ M1 đến M6, ít nói 3 tầng.
- Tài liệu hướng dẫn cho từng role (nông dân dùng app, kỹ sư duyệt thế nào, admin duyệt thửa thế nào): hiện chỉ có tài liệu kỹ thuật cho dev. Giám khảo/người dùng thật cần cái này.
- Chấm điểm PoF theo rubric, đối chiếu hiện trạng:
    - Repo công khai (5 điểm; không public lúc chấm thì Hội đồng xem xét loại): README còn ghi chính sách repo Private trong lúc phát triển, phải chuyển public trước hạn. Rà lịch sử commit để chắc không có secret (mục 9).
    - LICENSE và bảng dependency/license (10 điểm; thiếu hoặc không rõ bị trừ 5 đến 10, nặng thì loại): có LICENSE và LICENSES.md nhưng chưa khớp hoàn toàn với dependency / image thực tế.
    - Release/tag (5 điểm, thiếu bị trừ 5): hiện repo chưa có tag nào. Tag sớm (ví dụ v0.1.0) rồi tag lại bản chốt.
    - Build / install từ source (10 điểm; trừ 5 đến 10): xem mục 17.
    - Quản lý thư viện, nguồn gốc, phiên bản (10 điểm): xem phần license / dependency bên dưới.
    - README / CHANGELOG / issue tracker (10 điểm): chưa có CONTRIBUTING.md (đề khuyến khích, và câu hỏi hội đồng số 10 hỏi đúng cái này); chưa thấy issue tracker/template: bật GitHub Issues và tạo vài issue từ chính file này.
- License / dependency chưa hoàn chỉnh [Theo dõi]:
    - Hiện trạng: Python chưa có lockfile, Authlib chưa pin phiên bản, chưa có SBOM, chưa kiểm kê hết dependency bắc cầu, và còn thiếu SPDX header cho mã tự viết (CHANGELOG ghi "để riêng một lượt khác", chưa làm).
    - Hướng xử lý: rà lại từ artifact phát hành, bỏ dependency thừa, khóa phiên bản, ghi rõ nguồn / license / phiên bản / vai trò của từng thành phần.
    - Hai thành phần AGPL-3.0 (Ultralytics, PyMuPDF) và checkpoint model: cần đọc lại nghĩa vụ công bố mã nguồn khi cung cấp dịch vụ qua mạng và ghi rõ trong tài liệu.
    - Không dùng thành phần đóng / không rõ license ở lõi: hiện ổn (đã loại n8n, Llama). Giữ bảng license sạch, đừng thêm dịch vụ có điều khoản kín. Mattermost, Superset, Keycloak đều dùng image chính thức không sửa: nhớ giữ nguyên nguyên tắc "không sửa mã thư viện đính kèm" (đừng patch trực tiếp trong container).
- Hồ sơ nộp bài chưa xong [Theo dõi]: ngoài repo, đề đòi báo cáo kỹ thuật 8 đến 10 trang, slide tối đa 12, video demo 3 đến 5 phút. Chưa có cái nào. Chưa cần ưu tiên trước kiến trúc và demo, nhưng phải đóng hết checklist trước lúc chấm và chừa thời gian (đề xếp vào ngày 14).

## 20. Kịch bản demo và câu hỏi hội đồng [Quan trọng] (đề mục 9 và 13):

- Kịch bản và bộ minh chứng chưa hoàn chỉnh:
    - Hiện trạng: chưa có kịch bản 10 phút chứng minh liền mạch chuỗi người dùng, Open-Core, Process, Data, Intelligence, kết quả. Sơ đồ ba tầng, bằng chứng kiểm thử, bộ câu trả lời kiến trúc cũng chưa gom lại thành một bộ.
    - Hướng xử lý: chuẩn bị sơ đồ, kịch bản demo, dữ liệu mẫu, log/test evidence, phương án dự phòng (fallback), và câu trả lời cho các câu hỏi bên dưới. Thời lượng 10 phút demo + tối đa 5 phút trả lời.
- Demo phải chỉ trực tiếp "đây là Tầng 1, đây là H-P-D-I, đây là Tầng 3" và đi theo chuỗi người dùng, Open-Core, quy trình, dữ liệu, Intelligence, kết quả.
- Giao diện tiếng Việt: có; chuẩn hóa mấy thông báo lỗi tiếng Anh từ backend (mục 16).
- Dashboard / landing page thể hiện trạng thái vận hành: portal hiện chỉ là danh sách link tĩnh. Nên cho portal hiện trạng thái từng dịch vụ (up/down lấy từ healthcheck).
- Câu hỏi hội đồng và việc cần có để trả lời được thật:
    - "Open-Core nằm ở đâu, thay ứng dụng có phải đổi Core không?": cần ranh giới Core và DX-Farm (mục 7), tốt nhất có thêm ứng dụng thứ hai (mục 22).
    - "Nguồn dữ liệu/API ngừng thì sao?": AI đã có nhánh evidence_fallback; nguồn ngoài như thời tiết cần snapshot (mục 14).
    - "Build toàn bộ từ source trên máy mới được không?": cần clone sạch kiểm chứng + script reset (mục 17). Lưu ý bản dựng nặng (ollama ~9GB), phải ghi yêu cầu cấu hình và có tùy chọn nhẹ.
    - "Tăng từ 100 lên 100.000 người dùng thì sao?": phải nói được hướng scale (backend stateless, Postgres, Keycloak cụm, hàng đợi cho AI, object storage cho ảnh, phân trang, index, cache) và thừa nhận các điểm nghẽn hiện có: gọi AI trong transaction (mục 11), N+1 và không phân trang (mục 12), SQLite của Superset (mục 14), file ảnh local (mục 7).
    - "Quy trình đóng góp một module mới?": cần CONTRIBUTING.md + hướng dẫn thêm module (quy trình mới, mô hình AI mới) + issue template (mục 19).
    - "AI dùng ở đâu, đánh giá, giới hạn?": báo cáo đánh giá AI (mục 11).

## 21. Bốn không gian H-P-D-I, phần còn thiếu theo đề (đề mục 4.2):

- H (Human): có đăng nhập / người dùng / quyền. "Workspace / knowledge hub" chưa rõ: Mattermost là chat, còn kho tri thức (tài liệu VietGAP, hướng dẫn) chưa có giao diện cho người dùng xem. Nên có một trang tài liệu / knowledge hub đơn giản, tận dụng corpus RAG đã có.
- P (Process): xem mục 8, đang thiếu người xử lý, lịch sử thay đổi và theo dõi yêu cầu chờ.
- D (Data): xem mục 14 (nguồn dữ liệu mở, tìm kiếm / lọc / thống kê, dashboard).
- I (Intelligence): xem mục 11. Ngoài ra RAG hiện không có giao diện hỏi đáp cho người dùng, và mục "RAG trả lời có trích nguồn" trong README vẫn chưa tick. Cần một màn "hỏi đáp tài liệu có trích nguồn" thì phần Intelligence mới trình diễn được ngoài chẩn đoán ảnh.

## 22. Rủi ro lớn nhất: có thể lệch đề bài, cần chốt với giảng viên hướng dẫn / BTC trường trước (đề mục 2 và 4.3):

- Đề HaUI ghi ứng dụng tầng 3 là "bài toán thực tế của môi trường đại học" (ví dụ: đăng ký phòng, đề nghị thiết bị, hỗ trợ CNTT, phê duyệt tài liệu, quản lý công việc, hồ sơ/tài sản, dữ liệu đào tạo), và vòng HaUI cụ thể hóa thành "DX-Lab HaUI, trạm điều hành số phục vụ khoa, phòng, trung tâm hoặc đơn vị đào tạo".
- Dự án đang làm Agri-Carbon cho hợp tác xã nông nghiệp (bài toán của đề OLP quốc gia). File PROJECT_ISSUES.md cũ kết luận "đề tài nông nghiệp không phải vấn đề" và "DX-Farm không lệch nghiêm trọng khỏi đề, nền hiện tại trình bày được thành Open-Core cùng một ứng dụng minh họa". Kết luận đó viết theo đề quốc gia; câu chữ của đề HaUI hẹp hơn. Nên hỏi chắc là nông nghiệp có được chấp nhận ở vòng này không, trước khi đổ thêm công vào các mục 1 đến 21.
- Dù kết quả thế nào thì điều kiện của kết luận cũ vẫn đúng: phải chứng minh rõ phần lõi tái sử dụng được (mục 7), làm đủ phần Process (mục 8), và có một kịch bản demo sạch chạy từ đầu đến cuối (mục 20).
- Phương án an toàn nếu không chắc: giữ DX-Farm làm ứng dụng thứ nhất, thêm một ứng dụng nhỏ thứ hai chạy trên cùng Core cho bối cảnh trường học (ví dụ phiếu hỗ trợ CNTT hoặc mượn thiết bị: tạo yêu cầu, duyệt, xử lý, thống kê). Chỉ cần dùng lại Identity, Workflow, Data, thông báo của Core, không cần AI. Cái này vừa đáp ứng "bối cảnh đại học", vừa là bằng chứng tốt nhất cho câu hỏi "thay ứng dụng nghiệp vụ thì Open-Core có phải đổi không".

## 23. Thời gian và cách xếp ưu tiên (đề mục 14, 15):

- Đề cho 14 ngày kể từ ngày công bố đề nội bộ; ngày đó chưa rõ nên cần xác định ngày chốt trước khi chia phase. Các mục 1 đến 22 cộng lại là rất nhiều việc, khó làm hết trong 14 ngày, nên phân loại:
    - Nhóm A (đề bắt buộc, ăn điểm trực tiếp hoặc có nguy cơ bị loại), gợi ý thứ tự: chốt phạm vi bài toán (mục 22); ranh giới Open-Core + sơ đồ + lớp API (mục 7); workflow engine + lịch sử + người xử lý + danh sách chờ (mục 8); loại secret hard-code (mục 9); dữ liệu demo hợp lệ để chạy trọn luồng (mục 18); đánh giá AI có bằng chứng (mục 11); build từ clone sạch + test cho lõi (mục 17, 18); repo public + release + CHANGELOG + CONTRIBUTING + issue tracker + đồng bộ tài liệu / license (mục 19); kịch bản demo + báo cáo + slide + video (mục 19, 20).
    - Nhóm B (tăng điểm sản phẩm): luồng duyệt thửa/vụ, trang quản lý admin/engineer, phân quyền engineer và farmer-chỉ-mobile, precheck ảnh, thông báo cho farmer, Superset chỉ hiện bản ghi đã duyệt, màn hỏi đáp tài liệu RAG (mục 21), nguồn dữ liệu thời tiết.
    - Nhóm C (để sau cuộc thi): polygon ruộng/PostGIS, push thật, carbon/IoT, sản lượng chi tiết, low-code, scale thật, các mục bảo mật chuyên sâu còn lại.
    - Đây mới là đề xuất, phân nhóm cuối cùng do bạn quyết.

## 24. Để sau (chưa đưa vào phase gần, ghi lại cho khỏi quên):

- Carbon/AWD Estimator, IoT mô phỏng mực nước, tự xác nhận phun từ IoT (Giai đoạn 3 trong README). Việc thu sản lượng/diện tích/ngày thu hoạch thật ở mục 4 chính là bước chuẩn bị dữ liệu cho phần này.
- Frontend low-code (ToolJet/Appsmith) trong roadmap cũ: với kế hoạch trang quản lý tự viết ở mục 12 thì nên chốt bỏ hẳn hay vẫn giữ, tránh làm hai hướng.
- GraphRAG / multi-agent.
- Polygon ruộng + PostGIS (mục 2, phương án C).
- Mattermost tương tác hai chiều (duyệt/từ chối ngay trong chat), hiện đã chốt không làm.

---

Tạm thời là như thế. Tôi muốn bạn và tôi thảo luận thêm về các vấn đề trên. Tôi chỉ viết dựa trên ý tưởng mơ hồ, bạn hãy chỉ ra các điểm bất hợp lý cho tôi và hướng đến việc nâng cấp cho các vấn đề trên. Hãy gợi ý, đưa ra phương pháp hợp lý hơn, bổ sung và cải tiến các issue trên để có được luồng hoàn thiện và đầy đủ hơn. Bạn có thể đọc src hiện tại và các docs liên quan trong dự án, lấy được cái nhìn tổng quan và đề xuất thêm các issue cần cải thiện nhé.
