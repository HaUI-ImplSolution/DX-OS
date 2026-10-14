// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.repository.TicketCategoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

class TicketClassifierTest {

  private final TicketCategoryRepository categories = mock(TicketCategoryRepository.class);
  private TicketClassifier classifier;

  private static TicketCategory category(String code, TicketPriority priority, String keywords) {
    return TicketCategory.builder().code(code).name("Loại " + code).defaultPriority(priority)
        .keywords(keywords).build();
  }

  @BeforeEach
  void setUp() throws Exception {
    when(categories.findByActiveTrueOrderByNameAsc())
        .thenReturn(List.of(category("COMPUTER", TicketPriority.NORMAL, null),
            category("NETWORK", TicketPriority.NORMAL, null),
            category("OTHER", TicketPriority.LOW, "bảng tương tác"),
            category("PROJECTOR", TicketPriority.NORMAL, null)));
    classifier = new TicketClassifier(categories, JsonMapper.builder().build(),
        new DefaultResourceLoader(), "classpath:lab/classifier-rules.json");
  }

  @Test
  void matchedKeywordsChooseTheCategoryAndAreExplained() {
    var suggestion = classifier.classify("Máy chiếu phòng A2 không lên hình");

    assertThat(suggestion.category().getCode()).isEqualTo("PROJECTOR");
    assertThat(suggestion.matchedKeywords()).contains("máy chiếu", "không lên hình");
    assertThat(suggestion.reasons().getFirst()).contains("Khớp từ khóa");
    assertThat(suggestion.priority()).isEqualTo(TicketPriority.NORMAL);
  }

  @Test
  void matchingIgnoresCaseAndDiacritics() {
    var suggestion = classifier.classify("MAY CHIEU khong len hinh");

    assertThat(suggestion.category().getCode()).isEqualTo("PROJECTOR");
  }

  @Test
  void keywordsMatchWholeWordsOnly() {
    var suggestion = classifier.classify("Lỗi hiển thị frame ảnh trong phần giới thiệu");

    assertThat(suggestion.category().getCode()).isEqualTo("OTHER");
    assertThat(suggestion.matchedKeywords()).isEmpty();
    assertThat(suggestion.reasons().getFirst()).contains("loại mặc định");
  }

  @Test
  void keywordsAddedToACategoryAreMatched() {
    var suggestion = classifier.classify("Bảng tương tác phòng B3 không nhận bút");

    assertThat(suggestion.category().getCode()).isEqualTo("OTHER");
    assertThat(suggestion.matchedKeywords()).contains("bảng tương tác");
  }

  @Test
  void problemDuringAnExamRaisesThePriorityToUrgent() {
    var suggestion = classifier.classify("Cả phòng máy mất mạng giữa giờ thi");

    assertThat(suggestion.category().getCode()).isEqualTo("NETWORK");
    assertThat(suggestion.priority()).isEqualTo(TicketPriority.URGENT);
    assertThat(suggestion.reasons()).anyMatch(reason -> reason.contains("giờ thi"));
  }

  @Test
  void noActiveCategoryGivesNoSuggestion() {
    when(categories.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

    assertThat(classifier.classify("máy chiếu hỏng")).isNull();
  }
}
