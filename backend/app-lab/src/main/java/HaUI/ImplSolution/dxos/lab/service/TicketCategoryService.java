// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.core.api.exception.PokaYokeViolationException;
import HaUI.ImplSolution.dxos.core.api.exception.ResourceNotFoundException;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.TicketCategoryRequest;
import HaUI.ImplSolution.dxos.lab.dto.CatalogDtos.TicketCategoryResponse;
import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import HaUI.ImplSolution.dxos.lab.repository.TicketCategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The incident categories users choose from; a retired category is kept for old tickets.
@Service
@Transactional(readOnly = true)
public class TicketCategoryService {

  private final TicketCategoryRepository categoryRepository;

  public TicketCategoryService(TicketCategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  public List<TicketCategoryResponse> list(boolean includeInactive) {
    List<TicketCategory> categories = includeInactive ? categoryRepository.findAllByOrderByNameAsc()
        : categoryRepository.findByActiveTrueOrderByNameAsc();
    return categories.stream().map(TicketCategoryResponse::of).toList();
  }

  @Transactional
  public TicketCategoryResponse create(TicketCategoryRequest request) {
    String code = request.code().strip().toUpperCase();
    if (categoryRepository.existsByCode(code)) {
      throw new PokaYokeViolationException("CATEGORY_CODE_TAKEN",
          "Mã loại sự cố %s đã tồn tại".formatted(code));
    }
    TicketCategory category = TicketCategory.builder().code(code).build();
    fill(category, request);
    return TicketCategoryResponse.of(categoryRepository.save(category));
  }

  // The code identifies the category in the classifier rules, so it never changes.
  @Transactional
  public TicketCategoryResponse update(Long id, TicketCategoryRequest request) {
    TicketCategory category = categoryRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Ticket category not found: " + id));
    if (!category.getCode().equalsIgnoreCase(request.code().strip())) {
      throw new IllegalArgumentException("The code of a category cannot be changed");
    }
    fill(category, request);
    return TicketCategoryResponse.of(categoryRepository.save(category));
  }

  private static void fill(TicketCategory category, TicketCategoryRequest request) {
    category.setName(request.name().strip());
    category.setDefaultPriority(request.defaultPriority());
    category.setKeywords(request.keywords() == null || request.keywords().isBlank() ? null
        : request.keywords().strip());
    category.setActive(request.active() == null || request.active());
  }
}
