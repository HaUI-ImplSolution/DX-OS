// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

// items: elements of the current page
// page: zero-based page number
// size: maximum elements per page
// totalElements, totalPages: totals over the whole result
public record PageResponse<T>(List<T> items, int page, int size, long totalElements,
    int totalPages) {

  public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
    return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
        page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  public <R> PageResponse<R> map(Function<T, R> mapper) {
    return new PageResponse<>(items.stream().map(mapper).toList(), page, size, totalElements,
        totalPages);
  }
}
