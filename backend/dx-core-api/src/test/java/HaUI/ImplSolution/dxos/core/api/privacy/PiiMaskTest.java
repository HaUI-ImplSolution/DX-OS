// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiMaskTest {

  @Test
  void hidesPhoneNumbersInTheCommonWritings() {
    assertThat(PiiMask.mask("gọi 0901234567 nhé")).isEqualTo("gọi [đã ẩn] nhé");
    assertThat(PiiMask.mask("SĐT: 090 123 4567")).isEqualTo("SĐT: [đã ẩn]");
    assertThat(PiiMask.mask("090-123-4567")).isEqualTo("[đã ẩn]");
    assertThat(PiiMask.mask("0901.234.567.")).isEqualTo("[đã ẩn].");
    assertThat(PiiMask.mask("+84 901 234 567")).isEqualTo("+[đã ẩn]");
  }

  @Test
  void hidesCitizenStudentAndAccountNumbers() {
    assertThat(PiiMask.mask("CCCD 001203004567")).isEqualTo("CCCD [đã ẩn]");
    assertThat(PiiMask.mask("mã SV 2021602345")).isEqualTo("mã SV [đã ẩn]");
    assertThat(PiiMask.mask("STK 1234567890123")).isEqualTo("STK [đã ẩn]");
  }

  @Test
  void hidesEmailAddresses() {
    assertThat(PiiMask.mask("liên hệ sv.nguyen+lab@haui.edu.vn gấp"))
        .isEqualTo("liên hệ [đã ẩn] gấp");
  }

  @Test
  void leavesOrdinaryNumbersCodesAndAddressesAlone() {
    assertThat(PiiMask.mask("TK-2026-000123 quá hạn")).isEqualTo("TK-2026-000123 quá hạn");
    assertThat(PiiMask.mask("phòng 204, 30 máy, 28/10/2026 15:30"))
        .isEqualTo("phòng 204, 30 máy, 28/10/2026 15:30");
    assertThat(PiiMask.mask("từ địa chỉ 192.168.100.200 và 203.0.113.5"))
        .isEqualTo("từ địa chỉ 192.168.100.200 và 203.0.113.5");
    assertThat(PiiMask.mask("12345678")).isEqualTo("12345678");
    assertThat(PiiMask.mask(null)).isNull();
    assertThat(PiiMask.mask("")).isEmpty();
  }

  @Test
  void masksNestedPayloadsButNotUrlsOrNumbers() {
    Map<String, Object> payload = Map.of("text", "gọi 0901234567", "attachments",
        List.of(Map.of("title", "Máy hỏng, SĐT 0912345678", "title_link",
            "http://app/tickets/12345678901", "fields",
            List.of(Map.of("title", "Mã", "value", "TK-2026-000123", "short", true)))),
        "n", 7);

    Map<String, Object> masked = PiiMask.maskPayload(payload);

    assertThat(masked.get("text")).isEqualTo("gọi [đã ẩn]");
    assertThat(masked.get("n")).isEqualTo(7);
    @SuppressWarnings("unchecked")
    Map<String, Object> attachment = ((List<Map<String, Object>>) masked.get("attachments")).get(0);
    assertThat(attachment.get("title")).isEqualTo("Máy hỏng, SĐT [đã ẩn]");
    assertThat(attachment.get("title_link")).isEqualTo("http://app/tickets/12345678901");
    assertThat(payload.get("text")).isEqualTo("gọi 0901234567");
  }
}
