// SPDX-License-Identifier: Apache-2.0
import { getLocale } from "./locale";

// The backend answers in English with a stable errorCode. What the user reads is decided here, so the
// text can follow the language of the interface. A message that no rule matches is shown as the backend sent it.
type Rule = [RegExp, string];

const escape = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
// A message that never changes, and one with parts that are kept (written as regular expression groups).
const fixed = (english: string, vietnamese: string): Rule => [new RegExp(`^${escape(english)}$`), vietnamese];
const pattern = (english: string, vietnamese: string): Rule => [new RegExp(`^${english}$`), vietnamese];

const NAME = "\\[a-z\\]\\[a-z0-9_\\]\\*";

// Most specific first: the general "... is required" comes last.
const VI: Rule[] = [
  fixed("The model changed; read it again", "Mô hình đã thay đổi; hãy tải lại trước khi thực hiện"),
  pattern("must be a whole number at least (.+)", "Phải là số nguyên không nhỏ hơn $1"),
  pattern("The record does not meet the condition of this action: (.+)", "Hồ sơ chưa đạt điều kiện của hành động này: $1"),
  // A model file (the checker of the model format).
  fixed("The model file has problems", "Tệp mô hình có lỗi"),
  pattern("invalid YAML: (.*)", "YAML không hợp lệ: $1"),
  fixed("the file must be a mapping with format, name, roles and entities", "Tệp phải là một mapping gồm format, name, roles và entities"),
  fixed("entities must be a mapping with at least one entity", "entities phải là một mapping có ít nhất một thực thể"),
  fixed("units must be a list with at least one unit", "units phải là một danh sách có ít nhất một đơn vị"),
  fixed("a unit is a mapping with name and label", "Mỗi đơn vị là một mapping gồm name và label"),
  fixed("unit name must match [A-Z][A-Z0-9_]* and be at most 40 characters", "Tên đơn vị phải khớp [A-Z][A-Z0-9_]* và dài tối đa 40 ký tự"),
  pattern("unit (.+) is declared twice", "Đơn vị $1 bị khai báo hai lần"),
  pattern("owner_via of (.+) leads back to (.+)", "owner_via của $1 quay lại chính $2"),
  pattern("entity (.+) has more than one unit field", "Thực thể $1 có hơn một trường kiểu unit"),
  pattern("access unit of (.+) needs a unit field on (.+)", "Quyền unit của $1 cần một trường kiểu unit ở $2"),
  pattern("access assigned of (.+) needs a process on (.+)", "Quyền assigned của $1 cần có quy trình ở $2"),
  pattern("unsupported format, this version reads format (.+)", "Định dạng không được hỗ trợ, phiên bản này đọc định dạng $1"),
  pattern(`(.+) must match ${NAME} and be at most 40 characters`, "$1 phải khớp [a-z][a-z0-9_]* và dài tối đa 40 ký tự"),
  fixed("roles must be a list with at least one role", "roles phải là một danh sách có ít nhất một vai"),
  fixed("a role is a mapping with name, description and staff", "Mỗi vai là một mapping gồm name, description và staff"),
  fixed("role name must match [A-Z][A-Z0-9_]* and be at most 40 characters", "Tên vai phải khớp [A-Z][A-Z0-9_]* và dài tối đa 40 ký tự"),
  pattern("role (.+) is declared twice", "Vai $1 bị khai báo hai lần"),
  pattern("role (.+) is reserved for the platform", "Vai $1 được nền tảng dành riêng, hãy đặt tên khác"),
  fixed("staff must be true or false", "staff phải là true hoặc false"),
  fixed("entity name must match [a-z][a-z0-9_]* and be at most 40 characters", "Tên thực thể phải khớp [a-z][a-z0-9_]* và dài tối đa 40 ký tự"),
  fixed("an entity is a mapping with fields and access", "Mỗi thực thể là một mapping gồm fields và access"),
  pattern("owner_via must name a reference field of (.+)", "owner_via phải là một trường kiểu reference của $1"),
  pattern("field (.+) is renamed from (.+), which still exists", "Trường $1 được đổi tên từ $2, nhưng tên cũ vẫn còn"),
  fixed("fields must be a mapping with at least one field", "fields phải là một mapping có ít nhất một trường"),
  fixed("field name must match [a-z][a-z0-9_]* and be at most 40 characters", "Tên trường phải khớp [a-z][a-z0-9_]* và dài tối đa 40 ký tự"),
  pattern("field name (.+) is reserved", "Tên trường $1 được hệ thống dành riêng"),
  fixed("a field is a mapping with at least a type", "Mỗi trường là một mapping có ít nhất type"),
  pattern("field (.+) has no type", "Trường $1 chưa có type"),
  pattern("unknown type (.+), use text, number, datetime, choice, reference, user, unit or file", "Kiểu $1 không có, hãy dùng text, number, datetime, choice, reference, user, unit hoặc file"),
  fixed("options only applies to a choice field", "options chỉ dùng cho trường kiểu choice"),
  fixed("a unit field needs units declared in the model", "Trường kiểu unit cần mô hình khai báo units"),
  fixed("a reference field needs `to` naming an entity of this model", "Trường kiểu reference cần `to` chỉ một thực thể của mô hình này"),
  fixed("to only applies to a reference field", "to chỉ dùng cho trường kiểu reference"),
  fixed("a file field is filled by uploading, so it cannot be required or unique", "Trường kiểu file được điền bằng cách tải tệp lên nên không thể required hay unique"),
  pattern("renamed_from must be the earlier name of field (.+)", "renamed_from phải là tên cũ của trường $1"),
  fixed("a unique field cannot have a default", "Trường unique không được có default"),
  fixed("the default of a number field must be a number", "default của trường kiểu number phải là một số"),
  fixed("the default must be one of the options", "default phải là một trong các options"),
  fixed("only text, number, choice and unit fields can have a default", "Chỉ trường kiểu text, number, choice và unit mới có default"),
  fixed("a choice field needs options, a non-empty list", "Trường kiểu choice cần options, một danh sách không rỗng"),
  fixed("an option is text of 1 to 60 characters", "Mỗi lựa chọn là văn bản dài từ 1 đến 60 ký tự"),
  pattern("option (.+) is listed twice", "Lựa chọn $1 bị liệt kê hai lần"),
  fixed("access maps at least one role to all, own, read, unit or assigned", "access phải gán ít nhất một vai với all, own, read, unit hoặc assigned"),
  pattern("role (.+) is not declared in roles", "Vai $1 chưa được khai báo trong roles"),
  pattern("access of (.+) must be all, own, read, unit or assigned", "Quyền của $1 phải là all, own, read, unit hoặc assigned"),
  fixed("process is a mapping with family and handler", "process là một mapping gồm family và handler"),
  pattern("unknown process family, available: (.+)", "Họ quy trình không có, hiện có: $1"),
  fixed("handler must be a role declared in roles", "handler phải là một vai đã khai báo trong roles"),
  pattern("the handler role (.+) must have access all, read, unit or assigned", "Vai xử lý $1 phải có quyền all, read, unit hoặc assigned"),
  pattern("the (.+) must have access all, read, unit or assigned", "$1 phải có quyền all, read, unit hoặc assigned"),
  fixed("process is a mapping with handler and a family or states and actions", "process là một mapping gồm handler, cùng với family hoặc với states và actions"),
  pattern("(.+) must be a role declared in roles", "$1 phải là một vai đã khai báo trong roles"),
  pattern("(.+) needs at least one role", "$1 cần ít nhất một vai"),
  pattern("(.+) is one role or a list like \\[MANAGER, SELLER\\], got '(.*)'", "$1 là một vai hoặc một danh sách như [MANAGER, SELLER], không phải '$2'"),
  pattern("(.+) is one role or a list of roles", "$1 là một vai hoặc một danh sách vai"),
  pattern("role (.+) in (.+) is not declared in roles, declared: (.+)", "Vai $1 trong $2 chưa khai báo ở roles, hiện có: $3"),
  pattern("role (.+) is listed twice", "Vai $1 bị liệt kê hai lần"),
  fixed("a process without a family needs states", "Quy trình không có family phải tự khai báo states"),
  fixed("a process without a family needs actions", "Quy trình không có family phải tự khai báo actions"),
  fixed("states is a mapping from a state name to its label and whether it is final", "states là một mapping từ tên trạng thái tới label và terminal"),
  fixed("actions is a mapping from an action name to what it does", "actions là một mapping từ tên hành động tới nội dung hành động"),
  pattern("state name must match " + NAME + " and be at most 40 characters", "Tên trạng thái phải khớp [a-z][a-z0-9_]* và dài tối đa 40 ký tự"),
  pattern("action name must match " + NAME + " and be at most 40 characters", "Tên hành động phải khớp [a-z][a-z0-9_]* và dài tối đa 40 ký tự"),
  pattern("state (.+) is added by the platform for the action withdraw", "Trạng thái $1 do nền tảng tự thêm cho hành động withdraw, không cần khai báo"),
  pattern("there is no state (.+) to remove", "Khung không có trạng thái $1 để bỏ"),
  pattern("there is no action (.+) to remove", "Khung không có hành động $1 để bỏ"),
  fixed("a state is a mapping with label and terminal, or null to remove it", "Trạng thái là một mapping gồm label và terminal, hoặc null để bỏ trạng thái của khung"),
  fixed("an action is a mapping with from and to, or null to remove it", "Hành động là một mapping gồm from và to, hoặc null để bỏ hành động của khung"),
  pattern("withdraw is always taken by the owner and ends in (.+)", "withdraw luôn do người nộp thực hiện và kết thúc ở $1, chỉ khai from"),
  fixed("by is handler or owner", "by là handler hoặc owner"),
  fixed("role only applies to an action of the handler", "role chỉ dùng cho hành động của vai xử lý"),
  pattern("action (.+) needs (from and to|from)", "Hành động $1 cần khai $2"),
  pattern("(.+) is a state name or a list of state names", "$1 là tên một trạng thái hoặc danh sách tên trạng thái"),
  pattern("(.+) needs at least one state", "$1 cần ít nhất một trạng thái"),
  pattern("require must name fields of this entity that are not files, fields: (.+)", "require phải là trường của thực thể này và không phải trường tệp, hiện có: $1"),
  fixed("a process needs at least one state", "Quy trình cần ít nhất một trạng thái"),
  pattern("initial must be one of the states: (.+)", "initial phải là một trong các trạng thái: $1"),
  fixed("the initial state cannot be final", "Trạng thái ban đầu không được là trạng thái cuối"),
  fixed("a process needs at least one final state, with terminal: true", "Quy trình cần ít nhất một trạng thái cuối, khai terminal: true"),
  pattern("action (.+) leaves (.+), which is not a state, states: (.+)", "Hành động $1 đi ra từ $2, không phải trạng thái; các trạng thái: $3"),
  pattern("action (.+) leaves the final state (.+)", "Hành động $1 đi ra từ trạng thái cuối $2; trạng thái cuối không có hành động đi ra"),
  pattern("action (.+) ends in (.+), which is not a state, states: (.+)", "Hành động $1 kết thúc ở $2, không phải trạng thái; các trạng thái: $3"),
  pattern("action (.+) is taken by the owner, so it takes no role", "Hành động $1 do người nộp thực hiện nên không có role"),
  pattern("state (.+) has no action leaving it, so a record would stay there", "Trạng thái $1 không có hành động nào đi ra, hồ sơ sẽ kẹt ở đó"),
  pattern("state (.+) can never be reached from (.+)", "Không có đường đi từ trạng thái ban đầu $2 tới trạng thái $1"),
  pattern("(.+) is not a final state of the process", "$1 không phải trạng thái cuối của quy trình"),
  fixed("auto_close needs after, the time to wait", "auto_close cần after, thời gian chờ"),
  fixed("auto_close needs action, an action of the owner", "auto_close cần action, một hành động của người nộp"),
  fixed("auto_close needs an action here, as auto_close: {after: 7d, action: <action>}", "Quy trình này cần chỉ rõ hành động: auto_close: {after: 7d, action: <hành động>}"),
  fixed("the action of auto_close must be an action of the owner other than withdraw", "action của auto_close phải là hành động của người nộp, không phải withdraw"),
  fixed("the reservation family needs a booking block", "Họ reservation cần khối booking"),
  fixed("booking only applies to the reservation family", "booking chỉ dùng cho họ reservation"),
  fixed("booking is a mapping with resource, starts, ends and capacity", "booking là một mapping gồm resource, starts, ends và capacity"),
  fixed("starts and ends must be different fields", "starts và ends phải là hai trường khác nhau"),
  fixed("capacity is a positive whole number", "capacity là một số nguyên dương"),
  pattern("(.+) must name a (.+) field of this entity", "$1 phải là một trường kiểu $2 của thực thể này"),
  fixed("after maps a final state to a list of actions", "after gán một trạng thái cuối với một danh sách hành động"),
  pattern("(.+) is not a final state of the family (.+)", "$1 không phải trạng thái cuối của họ $2"),
  fixed("a list of actions is required", "Cần một danh sách hành động"),
  fixed("an action is a mapping with do, user and group or group_from", "Mỗi hành động là một mapping gồm do, user và group hoặc group_from"),
  pattern("unknown action, available: (.+)", "Hành động không có, hiện có: $1"),
  fixed("user must be created_by or a user field", "user phải là created_by hoặc một trường kiểu user"),
  fixed("give either group or group_from", "Chỉ cho group hoặc group_from, không cho cả hai"),
  fixed("group_from must be a choice or text field", "group_from phải là trường kiểu choice hoặc text"),
  fixed("deadline is a positive number followed by m, h or d, such as 72h", "deadline là một số dương kèm m, h hoặc d, ví dụ 72h"),
  fixed("auto_close is a positive number followed by m, h or d, such as 7d", "auto_close là một số dương kèm m, h hoặc d, ví dụ 7d"),
  fixed("auto_close only applies to the work_item family", "auto_close chỉ dùng cho họ work_item"),
  pattern("(.+) must be true or false", "$1 phải là true hoặc false"),
  pattern("unknown key (.+), allowed: (.+)", "Khóa $1 không có, được dùng: $2"),

  // Applying and changing a model.
  pattern("The plan is out of date: model (.+) runs version (.+); make a plan and pass its fromVersion as base", "Kế hoạch đã cũ: mô hình $1 đang chạy phiên bản $2; hãy lập kế hoạch lại và truyền fromVersion của nó làm base"),
  pattern("This change is at level (.+); read the plan and pass accept=(.+)", "Thay đổi này ở mức $1; hãy đọc kế hoạch và truyền accept=$2"),
  pattern("(\\d+) rules are broken by existing rows, first (.+) with (\\d+) rows; see the plan", "$1 quy tắc bị dữ liệu hiện có vi phạm, đầu tiên là $2 với $3 dòng; hãy xem kế hoạch"),
  pattern("role (.+) is declared again and (\\d+) users still hold it; applying gives them its access back", "Vai $1 được khai báo lại và còn $2 người đang giữ; áp dụng sẽ trả lại quyền của vai cho họ"),
  pattern("Model (.+) is being applied right now","Mô hình $1 đang được áp dụng"),
  pattern("Model (.+) is busy; try again", "Mô hình $1 đang có yêu cầu chưa xong; hãy thử lại sau ít giây"),
  pattern("Model (.+) has no earlier version", "Mô hình $1 chưa có phiên bản trước"),
  pattern("Model (.+) has no apply that stopped", "Mô hình $1 không có lần áp dụng nào đang dừng dở"),
  pattern("The apply of model (.+) that stopped kept no file; apply the file again", "Lần áp dụng dở của mô hình $1 không còn lưu tệp; hãy áp dụng lại tệp"),
  pattern("An earlier apply of model (.+) stopped after renaming fields; apply that file again to finish it", "Lần áp dụng trước của mô hình $1 dừng sau khi đổi tên trường; hãy áp dụng lại đúng tệp đó để hoàn tất"),
  pattern("The schema tool would remove (.+), which the model does not ask for", "Công cụ đổi lược đồ sẽ xóa $1, mà mô hình không yêu cầu"),
  pattern("Cannot use a temporary file: (.*)", "Không dùng được tệp tạm: $1"),
  fixed("The schema tool did not finish in time", "Công cụ đổi lược đồ không xong trong thời gian cho phép"),
  pattern("The schema tool failed: (.*)", "Công cụ đổi lược đồ báo lỗi: $1"),
  fixed("An entity cannot be renamed; its table, rows and attached files are removed", "Thực thể không đổi tên được; bảng, dữ liệu và tệp đính kèm của nó sẽ bị xóa"),
  pattern("Renamed from (.+); rows are kept, callers of the old name break", "Đổi tên từ $1; dữ liệu được giữ, nơi nào còn gọi tên cũ sẽ lỗi"),
  fixed("The column and its values are removed", "Cột và các giá trị của nó sẽ bị xóa"),
  pattern("Is (.+) a rename of (.+)\\? Then declare renamed_from: (.+) to keep the values", "Có phải $1 là tên mới của $2? Nếu đúng, hãy khai báo renamed_from: $3 để giữ các giá trị"),
  pattern("Field (.+) changes its type from (.+) to (.+), which is not supported; add a new field instead", "Trường $1 đổi kiểu từ $2 sang $3, điều này chưa được hỗ trợ; hãy thêm một trường mới"),
  pattern("Field (.+) refers to another entity, which is not supported; add a new field instead", "Trường $1 tham chiếu sang thực thể khác, điều này chưa được hỗ trợ; hãy thêm một trường mới"),
  fixed("Only for an entity without rows", "Chỉ làm được với thực thể chưa có dòng nào"),
  pattern("Entity (.+) changes its process family, which is not supported yet", "Thực thể $1 đổi họ quy trình, điều này chưa được hỗ trợ"),
  pattern("Entity ([a-z][a-z0-9_]*): (.+)", "Quy trình của thực thể $1 không dựng được: $2"),
  pattern("Cannot change CHANGE_FLOW for ([^;]+) while (\\d+) records are still open", "Không đổi được trạng thái hoặc hành động của $1 khi còn $2 hồ sơ đang xử lý"),
  pattern("Cannot change CHANGE_SELF_DECISION for ([^;]+) while (\\d+) records are still open", "Không đổi được quyền tự quyết định của $1 khi còn $2 hồ sơ đang xử lý"),
  pattern("Cannot change CHANGE_COMPLETION for ([^;]+) while (\\d+) records are still open", "Không đổi được hành động hoàn tất của $1 khi còn $2 hồ sơ đang xử lý"),
  pattern("Cannot change CHANGE_BOOKING for ([^;]+) while (\\d+) records are still open", "Không đổi được khối đặt chỗ của $1 khi còn $2 hồ sơ đang xử lý"),
  pattern("Cannot change CHANGE_DEADLINE for ([^;]+) while (\\d+) records are still open", "Không đổi được hạn xử lý của $1 khi còn $2 hồ sơ đang xử lý"),
  pattern("Cannot change ([A-Z_]+) for ([^;]+) while (\\d+) records are still open", "Không đổi được quy tắc quy trình của $2 khi còn $3 hồ sơ đang xử lý"),
  pattern("Cannot drop ([^;]+) while it has (\\d+) process instances running", "Không xóa được thực thể $1 khi còn $2 hồ sơ đang chạy quy trình; hãy hoàn tất hoặc rút các hồ sơ đó trước"),
  pattern("Model (.+) is being applied or needs reconciliation", "Mô hình $1 đang được áp dụng hoặc đã dừng dở, cần áp dụng lại đúng tệp đó để hoàn tất"),

  // Not found.
  pattern("Model (.+) is not applied", "Mô hình $1 chưa được áp dụng"),
  pattern("Model (.+) not found", "Không tìm thấy mô hình $1"),
  pattern("Model (.+) has no version (.+)", "Mô hình $1 không có phiên bản $2"),
  pattern("Cannot read (.+)", "Không đọc được $1"),
  pattern("Entity (.+) not found", "Không tìm thấy thực thể $1"),
  pattern("User (.+) not found", "Không tìm thấy người dùng $1"),
  pattern("Unit (.+) not found", "Không tìm thấy đơn vị $1"),
  pattern("Model (.+) has no role (.+)", "Mô hình $1 không có vai $2"),
  pattern("Role (.+) of (.+) not found", "Không tìm thấy vai $1 của $2"),
  pattern("Action (.+) not found", "Không tìm thấy hành động $1"),
  pattern("File (.+) not found", "Không tìm thấy tệp $1"),
  pattern("Message (.+) not found", "Không tìm thấy thông điệp $1"),
  pattern("(.+) (\\d+) not found", "Không tìm thấy $1 $2"),

  // Records, rows and who may do what.
  fixed("The record is closed", "Hồ sơ đã đóng, không thay đổi được"),
  fixed("A record with a process is kept; its owner withdraws it instead", "Hồ sơ có quy trình được giữ lại; người nộp rút lại thay vì xóa"),
  fixed("Other records still refer to this record", "Vẫn còn hồ sơ khác tham chiếu tới hồ sơ này"),
  pattern("(.+) has no process", "$1 không có quy trình"),
  fixed("Your roles cannot assign this record", "Vai của bạn không được giao hồ sơ này"),
  fixed("Your roles cannot create this record", "Vai của bạn không được tạo hồ sơ này"),
  fixed("Your roles cannot reach this entity", "Vai của bạn không truy cập được loại hồ sơ này"),
  fixed("Your roles can only read this entity", "Vai của bạn chỉ được xem loại hồ sơ này"),
  fixed("The unit is out of your reach", "Đơn vị nằm ngoài phạm vi của bạn"),
  pattern("The (.+) is out of your reach", "$1 nằm ngoài phạm vi của bạn"),
  fixed("The status is changed by the process only", "Trạng thái chỉ đổi qua quy trình"),
  fixed("This account may not use this client", "Tài khoản này không được dùng ứng dụng này"),
  pattern("Only the owner of the record can (.+)", "Chỉ người nộp hồ sơ mới được thực hiện thao tác $1"),
  pattern("Only (.+) can do this", "Chỉ vai $1 mới được thực hiện thao tác này"),
  pattern("The record cannot take this action in state (.+)", "Hồ sơ không thực hiện được thao tác này ở trạng thái $1"),
  fixed("The record cannot take this action now", "Hồ sơ không thực hiện được thao tác này lúc này"),
  fixed("The creator of a record cannot handle it", "Người tạo hồ sơ không được tự xử lý hồ sơ của mình"),
  fixed("The record is assigned to someone else", "Hồ sơ đang được giao cho người khác"),
  fixed("The resource is already booked for this time", "Tài nguyên đã có người đặt trong khung giờ này"),
  fixed("Only the user who added a file can remove it", "Chỉ người đã thêm tệp mới gỡ được tệp"),
  pattern("(.+) is not a file field of (.+)", "$1 không phải trường tệp của $2"),
  fixed("The file is empty", "Tệp rỗng"),
  fixed("The file has no name", "Tệp chưa có tên"),
  fixed("Files of this type are not accepted", "Không nhận loại tệp này"),
  fixed("The file was rejected because the virus scan found malware in it", "Tệp bị từ chối vì quét virus phát hiện mã độc"),
  fixed("The virus scanner cannot be reached", "Chưa quét virus được nên tệp chưa được lưu; thử lại sau ít phút"),
  pattern("The virus scanner gave no verdict: (.*)", "Chưa quét virus được nên tệp chưa được lưu; thử lại sau ít phút ($1)"),
  fixed("The message was delivered already", "Thông điệp đã được gửi"),

  // The message of one field, shown under its input.
  fixed("cannot handle this record", "Không xử lý được hồ sơ này"),
  fixed("no field to update", "Không có trường nào để cập nhật"),
  fixed("like applies to text fields", "like chỉ dùng cho trường văn bản"),
  fixed("in needs at least one value", "in cần ít nhất một giá trị"),
  pattern("unknown operator (.+)", "Toán tử $1 không có"),
  fixed("unknown field", "Trường không có trong mô hình"),
  fixed("cannot be set", "Không được gán trực tiếp"),
  fixed("must be text", "Phải là văn bản"),
  pattern("must be one of (.+)", "Phải là một trong $1"),
  fixed("must be a number", "Phải là một số"),
  fixed("is set by uploading a file", "Được điền bằng cách tải tệp lên"),
  fixed("must be an ISO-8601 date and time with an offset", "Phải là ngày giờ ISO-8601 kèm múi giờ"),
  fixed("must be the id of a record", "Phải là mã của một hồ sơ"),
  fixed("already exists", "Đã tồn tại"),
  fixed("refers to a record that does not exist", "Tham chiếu tới hồ sơ không tồn tại"),
  fixed("has a value that is not allowed", "Có giá trị không được phép"),
  fixed("breaks a rule of the model", "Vi phạm một quy tắc của mô hình"),
  pattern("must be after (.+)", "Phải sau $1"),
  pattern("a record holds at most (\\d+) files", "Mỗi hồ sơ chỉ giữ tối đa $1 tệp"),
  fixed("cannot be read", "Không đọc được tệp"),
  fixed("must be failed, stopped, pending or delivered", "Phải là failed, stopped, pending hoặc delivered"),
  fixed("must not be negative", "Không được là số âm"),
  pattern("is required before (.+)", "Bắt buộc nhập trước khi thực hiện $1"),
  fixed("is required", "Bắt buộc nhập"),

  // Outgoing messages and the accounts service.
  pattern("No handler for (.+)", "Chưa có bộ xử lý cho chủ đề $1"),
  pattern("Unknown operation (.+)", "Thao tác $1 không có"),
  fixed("Keycloak is unreachable", "Không kết nối được Keycloak"),
  pattern("Keycloak has no group (.+)", "Keycloak chưa có nhóm $1"),
  fixed("Keycloak returned no access token", "Keycloak không trả mã truy cập"),
  fixed("Keycloak did not return the new user location", "Keycloak không trả địa chỉ của người dùng mới"),
  pattern("Username or email already exists: (.+)", "Tên đăng nhập hoặc email đã tồn tại: $1"),
  pattern("Keycloak refused a role lookup: HTTP (\\d+)", "Keycloak từ chối tra cứu vai: HTTP $1"),
  pattern("Keycloak refused to change a role: HTTP (\\d+)", "Keycloak từ chối đổi vai: HTTP $1"),
  pattern("Keycloak refused to change a user: HTTP (\\d+)", "Keycloak từ chối đổi người dùng: HTTP $1"),
  pattern("Keycloak refused to reset a password: HTTP (\\d+)", "Keycloak từ chối đặt lại mật khẩu: HTTP $1"),
  fixed("You cannot lock your own account", "Bạn không thể khóa chính tài khoản của mình"),
  pattern("Keycloak refused to create a client: HTTP (\\d+)", "Keycloak từ chối tạo client của mô hình: HTTP $1"),
  pattern("Keycloak has no client (.+)", "Keycloak chưa có client $1"),
  pattern("Keycloak refused to create a role: HTTP (\\d+)", "Keycloak từ chối tạo vai: HTTP $1"),
  pattern("Keycloak refused to create a group: HTTP (\\d+)", "Keycloak từ chối tạo nhóm: HTTP $1"),
  pattern("Keycloak refused to add a user to a group: HTTP (\\d+)", "Keycloak từ chối thêm người dùng vào nhóm: HTTP $1"),
  pattern("Keycloak refused to remove a user from a group: HTTP (\\d+)", "Keycloak từ chối gỡ người dùng khỏi nhóm: HTTP $1"),
  pattern("Keycloak refused a lookup: HTTP (\\d+)", "Keycloak từ chối tra cứu: HTTP $1"),
  pattern("Keycloak refused to delete a user: HTTP (\\d+)", "Keycloak từ chối xóa người dùng: HTTP $1"),
  pattern("Keycloak refused a user lookup: HTTP (\\d+)", "Keycloak từ chối tra cứu người dùng: HTTP $1"),
  pattern("Keycloak refused the admin client credentials: HTTP (\\d+)", "Keycloak từ chối thông tin của client quản trị: HTTP $1"),
  pattern("Keycloak refused to create a user: HTTP (\\d+)", "Keycloak từ chối tạo người dùng: HTTP $1"),
  pattern("Keycloak refused to assign the (.+) role: HTTP (\\d+)", "Keycloak từ chối gán vai $1: HTTP $2"),
  fixed("Account service is unavailable, please try again later", "Dịch vụ tài khoản chưa dùng được, vui lòng thử lại sau"),
  fixed("Account service refused the request, check the roles of the service account", "Dịch vụ tài khoản từ chối yêu cầu; hãy kiểm tra quyền của tài khoản dịch vụ ở Keycloak, thử lại sẽ không hết lỗi"),
  pattern("(?:[Tt]he )?webhook target (.+) is not configured", "Đích webhook $1 chưa được cấu hình trong tệp đích của máy chủ"),
  pattern("The webhook answered (\\d+)", "Webhook trả mã lỗi $1"),
  pattern("The webhook could not be reached \\((.*)\\)", "Không kết nối được tới webhook ($1)"),
  fixed("The webhook could not be posted", "Không gửi được nội dung tới webhook"),
  fixed("Interrupted while posting to the webhook", "Việc gửi tới webhook bị ngắt giữa chừng"),
  fixed("Could not sign the webhook body", "Không ký được nội dung gửi tới webhook"),
  pattern("Failed to (?:store|read|delete|presign) object (.+)", "Kho tệp không xử lý được tệp $1"),
  fixed("Object storage is unreachable", "Không kết nối được kho tệp"),
  pattern("No file processor registered for: (.+)", "Chưa có bộ xử lý tệp cho mục đích $1"),
  pattern("Keycloak refused to delete a client: HTTP (\\d+)", "Keycloak từ chối xóa client của mô hình: HTTP $1"),
  pattern("No authenticated Jwt principal found: (.*)", "Không xác định được người dùng đang đăng nhập"),
  fixed("Interrupted while running the schema tool", "Công cụ đổi lược đồ bị ngắt giữa chừng"),
  pattern("Not a safe identifier: (.+)", "Tên $1 không dùng được làm tên trong cơ sở dữ liệu"),
  fixed("Files cannot be checked right now, please try again later", "Chưa kiểm tra được tệp lúc này, vui lòng thử lại sau"),
  fixed("The record was modified by someone else, reload and try again", "Bản ghi vừa được người khác thay đổi, hãy tải lại rồi thử lại"),
  pattern("Invalid value for (.+)", "Giá trị không hợp lệ cho $1"),
  pattern("Missing parameter (.+)", "Thiếu tham số $1"),
  fixed("Invalid value", "Giá trị không hợp lệ"),
  fixed("Internal server error", "Hệ thống gặp lỗi, vui lòng thử lại sau"),

  // Last: a message like "entities is required" names what is missing.
  pattern("(.+) is required", "$1 là bắt buộc"),
];

// "line 3, column 5: ..." in front of a problem of a model file.
const POSITION = /^line (\d+), column (\d+): (.*)$/s;

const TABLES: Record<string, Rule[]> = { vi: VI };

function translateOne(text: string, table: Rule[]): string | null {
  for (const [regex, replacement] of table) {
    if (regex.test(text)) return text.replace(regex, replacement);
  }
  return null;
}

// The text in the language of the interface. A message of several parts joined by "; " is taken part by part.
export function translateBackend(text: string, locale = getLocale()): string {
  const table = TABLES[locale];
  if (!table || !text) return text;
  const position = POSITION.exec(text);
  if (position) return `dòng ${position[1]}, cột ${position[2]}: ${translateBackend(position[3], locale)}`;
  const whole = translateOne(text, table);
  if (whole !== null) return whole;
  // A problem of one field arrives as "field: reason".
  const field = /^([A-Za-z0-9_.]+): (.+)$/.exec(text);
  if (field) {
    const reason = translateBackend(field[2], locale);
    if (reason !== field[2]) return `${field[1]}: ${reason}`;
  }
  const parts = text.split("; ");
  if (parts.length === 1) return text;
  // Some messages hold a "; " themselves, so the longest run of parts that is one known message wins.
  const out: string[] = [];
  for (let start = 0; start < parts.length; ) {
    let end = parts.length;
    let translated: string | null = null;
    for (; end > start; end--) {
      translated = translateOne(parts.slice(start, end).join("; "), table);
      if (translated !== null) break;
    }
    if (translated === null) {
      out.push(parts[start]);
      start += 1;
    } else {
      out.push(translated);
      start = end;
    }
  }
  return out.join("; ");
}

// The data of an error: a list of problems of a model file, or the messages of the fields of a form.
export function translateData<T>(data: T): T {
  if (Array.isArray(data)) return data.map((item) => (typeof item === "string" ? translateBackend(item) : item)) as T;
  if (data && typeof data === "object") {
    return Object.fromEntries(
      Object.entries(data).map(([key, value]) => [key, typeof value === "string" ? translateBackend(value) : value]),
    ) as T;
  }
  return data;
}
