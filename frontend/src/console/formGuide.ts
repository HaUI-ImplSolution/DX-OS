// SPDX-License-Identifier: Apache-2.0
import { getLocale } from "../shell/locale";
import { t } from "../shell/t";

// What the model form tells the person writing a model: for every part of the file, what the system
// accepts, what it lets them change in what exists, and what they can create from nothing.
export interface Guide {
  summary: string;
  accepts: string[];
  extends: string[];
  creates: string[];
}

export type Section = "general" | "roles" | "units" | "entities" | "fields" | "access" | "process";

export const GUIDE: Record<Section, Guide> = {
  general: {
    summary: t("Tên mô hình và nơi nhận lịch sử hồ sơ."),
    accepts: [
      t("Tên mô hình gồm chữ thường, số và dấu gạch dưới, bắt đầu bằng chữ, dài tối đa 40 ký tự."),
      t("Đích gửi lịch sử là tên một đích đã cấu hình ở máy chủ, không phải địa chỉ web."),
    ],
    extends: [t("Để trống đích gửi thì lịch sử đi theo đích mặc định của hệ thống.")],
    creates: [t("Mỗi tên mới là một mô hình mới, có bảng dữ liệu, vai và API riêng.")],
  },
  roles: {
    summary: t("Vai là nhóm người có cùng quyền trong mô hình này."),
    accepts: [
      t("Tên vai viết hoa, gồm chữ, số và dấu gạch dưới, bắt đầu bằng chữ. Tên ADMIN dành riêng cho nền tảng."),
      t("Cần ít nhất một vai."),
    ],
    extends: [
      t("Thêm vai bất cứ lúc nào; vai mới dùng được ngay ở mục Quyền và làm vai xử lý của quy trình."),
      t("Đổi tên vai ở đây thì quyền và vai xử lý đang trỏ tới vai đó đổi theo."),
    ],
    creates: [
      t("Vai có thể đặt tên theo nghiệp vụ của bạn, ví dụ TRUONG_KHOA hay KE_TOAN."),
      t("Vai đánh dấu nhân viên đăng nhập thêm mã xác thực một lần."),
    ],
  },
  units: {
    summary: t("Đơn vị là nhóm tổ chức như khoa, phòng; hồ sơ thuộc về một đơn vị."),
    accepts: [t("Tên đơn vị viết hoa như tên vai; nhãn là chữ hiển thị cho người dùng.")],
    extends: [t("Thêm đơn vị khi tổ chức có thêm bộ phận; người dùng được xếp vào đơn vị ở tab Người dùng.")],
    creates: [
      t("Thêm một trường kiểu Đơn vị vào thực thể, rồi cho một vai quyền Của đơn vị mình để mỗi đơn vị chỉ thấy hồ sơ của mình."),
    ],
  },
  entities: {
    summary: t("Thực thể là một loại hồ sơ, ví dụ Yêu cầu hỗ trợ hay Lượt đặt phòng."),
    accepts: [
      t("Mã thực thể gồm chữ thường, số và dấu gạch dưới, bắt đầu bằng chữ."),
      t("Mỗi thực thể cần ít nhất một trường và một dòng quyền."),
    ],
    extends: [
      t("Thêm trường vào thực thể đã có dữ liệu; trường bắt buộc mới cần giá trị mặc định để điền vào hồ sơ cũ."),
      t("Dòng cha con: thực thể con có thể thừa quyền sở hữu của thực thể cha qua một trường tham chiếu."),
    ],
    creates: [t("Tạo thực thể mới cho bất cứ nghiệp vụ nào; mỗi thực thể có bảng, API và giao diện nhập liệu riêng.")],
  },
  fields: {
    summary: t("Trường là một thông tin của hồ sơ."),
    accepts: [
      t("Kiểu: văn bản, số, ngày giờ, lựa chọn, tham chiếu, người dùng, đơn vị, tệp đính kèm."),
      t("Kiểu lựa chọn cần danh sách lựa chọn; kiểu tham chiếu cần chọn thực thể được tham chiếu."),
    ],
    extends: [
      t("Đổi tên trường mà giữ dữ liệu: điền tên cũ vào Nâng cao."),
      t("Đặt bắt buộc, không trùng hoặc giá trị mặc định ở từng trường."),
    ],
    creates: [t("Tạo trường mới cho bất cứ thông tin nào nghiệp vụ cần; không giới hạn số trường.")],
  },
  access: {
    summary: t("Mỗi vai được xem và sửa hồ sơ của thực thể này đến đâu."),
    accepts: [
      t("Chỉ vai đã khai báo ở mục Vai. Mức quyền: tất cả, của mình, chỉ đọc, của đơn vị mình, được giao cho mình."),
      t("Vai không có dòng nào ở đây thì không thấy thực thể này."),
    ],
    extends: [t("Đổi mức quyền của một vai bất cứ lúc nào; áp dụng lại mô hình thì quyền mới có hiệu lực.")],
    creates: [t("Kết hợp tự do: mỗi vai một mức khác nhau trên từng thực thể.")],
  },
  process: {
    summary: t("Quy trình cho biết hồ sơ đi qua những bước nào và ai xử lý ở mỗi bước."),
    accepts: [
      t("Chọn một khung có sẵn rồi giữ nguyên, hoặc bỏ khung và tự khai báo đủ trạng thái (states) và hành động (actions)."),
      t("Vai xử lý là một vai hoặc một danh sách vai, đều phải đã khai báo ở mục Vai và có quyền xem hồ sơ (tất cả, chỉ đọc, đơn vị hoặc được giao)."),
      t("Tên trạng thái và hành động gồm chữ thường, số, gạch dưới. Mỗi trạng thái không cuối phải có hành động đi ra, trạng thái cuối thì không."),
    ],
    extends: [
      t("Thêm trạng thái hoặc hành động vào khung đã chọn; đổi nhãn, đổi trạng thái đích; bỏ hành động của khung bằng null."),
      t("Giao một hành động cho riêng một vai (duyệt nhiều cấp) và bắt buộc điền trường trước khi thực hiện hành động."),
      t("Đặt hạn xử lý, tự đóng hồ sơ bằng một hành động của người nộp, cho phép người tạo tự quyết định, thêm hành động hoàn tất."),
      t("Đổi trạng thái hoặc hành động khi còn hồ sơ đang xử lý thì bị từ chối; đổi nhãn, vai, trường bắt buộc, hạn thì áp dụng ngay."),
    ],
    creates: [
      t("Quy trình hoàn toàn mới, không dựa khung nào: tự khai báo states, actions và trạng thái ban đầu (initial)."),
      t("Khung Chiếm dụng theo thời gian kèm khối đặt chỗ tạo ra nghiệp vụ đặt phòng, mượn thiết bị."),
      t("Bỏ quy trình thì thực thể chỉ là danh mục để nhập và xem."),
    ],
  },
};

export const FIELD_TYPE_LABEL: Record<string, string> = {
  text: t("Văn bản"),
  number: t("Số"),
  boolean: t("Đúng hoặc sai"),
  date: t("Chỉ ngày"),
  datetime: t("Ngày giờ"),
  choice: t("Lựa chọn"),
  reference: t("Tham chiếu"),
  user: getLocale() === "en" ? "User" : t("Người dùng"),
  unit: getLocale() === "en" ? "Unit" : t("Đơn vị"),
  file: t("Tệp đính kèm"),
};

export const SCOPE_LABEL: Record<string, string> = {
  all: t("Tất cả, đọc và ghi"),
  own: t("Của mình"),
  read: t("Tất cả, chỉ đọc"),
  unit: t("Của đơn vị mình"),
  assigned: t("Được giao cho mình"),
};

export const COMPLETION_LABEL: Record<string, string> = {
  add_to_group: t("Thêm vào nhóm"),
  remove_from_group: t("Bỏ khỏi nhóm"),
};

export const PARAM_HELP: Record<string, string> = {
  allow_self_decision: t("Người tạo hồ sơ được tự quyết định hồ sơ của mình"),
  deadline: t("Thời gian một vai xử lý có để xử lý hồ sơ, quá hạn thì được đánh dấu"),
  auto_close: t("Hồ sơ đã xử lý xong mà người nộp không xác nhận sau thời gian này thì hệ thống tự đóng"),
};

// The words the YAML editor shows when the pointer rests on a key. They are attached to the schema of
// the model file by the path of the key, so the structure stays where the model format is defined.
export const SCHEMA_HELP: Record<string, string> = {
  format: t("Phiên bản định dạng của tệp mô hình, hiện là 0."),
  name: t("Tên mô hình: chữ thường, số, gạch dưới, bắt đầu bằng chữ, tối đa 40 ký tự. Đặt tên bảng dữ liệu (schema m_<tên>) và đường dẫn API."),
  webhook: t("Tên đích nhận lịch sử hồ sơ, đã cấu hình ở máy chủ (không phải địa chỉ web). Bỏ trống thì dùng đích mặc định."),
  roles: t("Danh sách vai: nhóm người có cùng quyền. Cần ít nhất một vai. Vai khai ở đây dùng được ở access, handler và role của hành động."),
  "roles.name": t("Tên vai viết hoa: chữ, số, gạch dưới, bắt đầu bằng chữ. ADMIN dành riêng cho nền tảng."),
  "roles.description": t("Mô tả vai, hiện ở màn hình quản lý người dùng."),
  "roles.staff": t("true nếu người giữ vai này đăng nhập thêm bằng mã xác thực một lần."),
  units: t("Đơn vị tổ chức như khoa, phòng. Mỗi đơn vị là một nhóm; dùng cùng trường kiểu unit và quyền unit để mỗi đơn vị chỉ thấy hồ sơ của mình."),
  "units.name": t("Tên đơn vị viết hoa như tên vai."),
  "units.label": t("Tên hiển thị của đơn vị."),
  entities: t("Các loại hồ sơ của mô hình. Khóa là mã thực thể (chữ thường, số, gạch dưới). Mỗi thực thể cần fields và access."),
  "entities.label": t("Tên hiển thị của thực thể."),
  "entities.owner_via": t("Dành cho thực thể con: tên một trường reference; chủ sở hữu hồ sơ con là chủ sở hữu của hồ sơ cha."),
  "entities.fields": t("Các trường của hồ sơ. Khóa là mã trường. Cần ít nhất một trường."),
  "entities.fields.type": t("Kiểu trường: text, number, datetime, choice (cần options), reference (cần to), user, unit, file."),
  "entities.fields.label": t("Tên hiển thị của trường."),
  "entities.fields.required": t("true nếu bắt buộc nhập. Thêm trường bắt buộc vào thực thể đã có dữ liệu thì cần default."),
  "entities.fields.unique": t("true nếu giá trị không được trùng giữa các hồ sơ."),
  "entities.fields.options": t("Danh sách lựa chọn của trường kiểu choice."),
  "entities.fields.to": t("Mã thực thể mà trường kiểu reference trỏ tới."),
  "entities.fields.renamed_from": t("Tên cũ của trường, khai khi đổi tên trường mà muốn giữ dữ liệu."),
  "entities.fields.default": t("Giá trị điền cho hồ sơ cũ khi thêm trường bắt buộc; trường nhiều lựa chọn chọn từ danh sách các lựa chọn."),
  "entities.access": t("Quyền của từng vai: all (đọc và ghi tất cả), own (của mình), read (tất cả, chỉ đọc), unit (của đơn vị mình), assigned (được giao cho mình). Vai không có dòng thì không thấy thực thể."),
  "entities.process": t("Quy trình của hồ sơ. Chọn family có sẵn rồi mở rộng, hoặc bỏ family và tự khai states và actions. Bỏ cả khối nếu thực thể chỉ là danh mục."),
  "entities.process.family": t("Khung có sẵn để bắt đầu: approval (duyệt), work_item (công việc xử lý), reservation (đặt chỗ). Bỏ trống thì phải tự khai states và actions."),
  "entities.process.handler": t("Vai xử lý: một vai (handler: AGENT) hoặc danh sách ([AGENT, LEAD]). Phải là vai đã khai ở roles và có quyền xem hồ sơ ở access."),
  "entities.process.initial": t("Trạng thái đầu tiên của hồ sơ. Mặc định là trạng thái khai đầu tiên, hoặc trạng thái đầu của khung."),
  "entities.process.states": t("Trạng thái của hồ sơ. Khai tên mới để thêm, khai tên của khung để đổi nhãn, đặt null để bỏ trạng thái của khung."),
  "entities.process.states.label": t("Tên hiển thị của trạng thái."),
  "entities.process.states.terminal": t("true nếu là trạng thái cuối: hồ sơ vào đó thì không đổi được nữa."),
  "entities.process.actions": t("Hành động chuyển hồ sơ giữa các trạng thái. Khai tên mới để thêm, khai tên của khung để ghi đè, đặt null để bỏ hành động của khung. withdraw là hành động rút lại của người nộp, chỉ cần khai from."),
  "entities.process.actions.label": t("Tên hiển thị của nút hành động; bỏ trống thì nút hiện mã hành động."),
  "entities.process.actions.from": t("Trạng thái xuất phát: một trạng thái hoặc danh sách trạng thái."),
  "entities.process.actions.to": t("Trạng thái đích."),
  "entities.process.actions.by": t("handler: vai xử lý thực hiện. owner: người nộp hồ sơ thực hiện."),
  "entities.process.actions.role": t("Giới hạn hành động cho một vai hoặc danh sách vai (chỉ với by: handler); không khai thì dùng các vai ở handler."),
  "entities.process.actions.needs_note": t("true nếu người thực hiện phải ghi lý do."),
  "entities.process.actions.when": t("Điều kiện để thực hiện hành động: một phép so sánh dạng trường toán_tử giá_trị, ví dụ amount > 5000000. Toán tử: ==, !=, <, <=, >, >=; thứ tự chỉ cho trường number. Chỉ dùng cho hành động của người xử lý; mỗi trạng thái không cuối cần ít nhất một hành động ra không có điều kiện."),
  "entities.process.actions.require": t("Trường bắt buộc điền trước khi thực hiện hành động: một trường hoặc danh sách trường (không phải trường file)."),
  "entities.process.allow_self_decision": t("true nếu người tạo hồ sơ được tự xử lý hồ sơ của mình."),
  "entities.process.deadline": t("Thời gian vai xử lý có để xử lý, quá hạn thì bị đánh dấu. Số kèm m (phút), h (giờ) hoặc d (ngày), ví dụ 90m, 72h, 3d."),
  "entities.process.auto_close": t("Tự đóng hồ sơ chờ người nộp: 7d (khung work_item) hoặc {after: 7d, action: <hành động của người nộp>}."),
  "entities.process.auto_close.after": t("Thời gian chờ, ví dụ 7d."),
  "entities.process.auto_close.action": t("Hành động của người nộp mà hệ thống thực hiện thay họ khi hết thời gian chờ."),
  "entities.process.booking": t("Chỉ với khung reservation: trường tài nguyên, thời gian bắt đầu, kết thúc và sức chứa. Lượt đặt vượt sức chứa bị từ chối."),
  "entities.process.booking.resource": t("Tên trường reference chỉ tài nguyên được đặt."),
  "entities.process.booking.starts": t("Tên trường datetime chỉ giờ bắt đầu."),
  "entities.process.booking.ends": t("Tên trường datetime chỉ giờ kết thúc."),
  "entities.process.booking.capacity": t("Số lượt được chạy cùng lúc trên một tài nguyên, mặc định 1."),
  "entities.process.after": t("Hành động hoàn tất: khóa là trạng thái cuối, giá trị là danh sách thao tác chạy khi hồ sơ đến đó."),
};

type Node = Record<string, unknown>;

// The schema with the help of each key as its description, so the editor shows it on hover and in
// the suggestions.
export function describeSchema(schema: Node): Node {
  const walk = (node: unknown, path: string): unknown => {
    if (node === null || typeof node !== "object" || Array.isArray(node)) return node;
    const copy: Node = { ...(node as Node) };
    const properties = copy.properties;
    if (properties && typeof properties === "object") {
      const next: Node = {};
      Object.entries(properties as Node).forEach(([key, child]) => {
        const childPath = path ? `${path}.${key}` : key;
        const walked = walk(child, childPath) as Node;
        const help = SCHEMA_HELP[childPath];
        next[key] = help && walked && typeof walked === "object" ? { ...walked, description: help } : walked;
      });
      copy.properties = next;
    }
    for (const key of ["items", "additionalProperties"] as const) {
      if (copy[key] && typeof copy[key] === "object") copy[key] = walk(copy[key], path);
    }
    for (const key of ["oneOf", "anyOf"] as const) {
      if (Array.isArray(copy[key])) copy[key] = (copy[key] as unknown[]).map((item) => walk(item, path));
    }
    return copy;
  };
  return walk(schema, "") as Node;
}
