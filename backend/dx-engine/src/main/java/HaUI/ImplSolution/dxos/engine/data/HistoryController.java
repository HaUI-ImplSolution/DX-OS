// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.data;

import HaUI.ImplSolution.dxos.core.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "History",
    description = "Checks that the history of records was not changed; ADMIN only")
public class HistoryController {

  private final HistoryChain chain;
  private final HistoryAnchor anchor;

  public HistoryController(HistoryChain chain, HistoryAnchor anchor) {
    this.chain = chain;
    this.anchor = anchor;
  }

  @GetMapping("/verify")
  @Operation(
      summary = "Check the history chain against itself and against the anchors",
      description =
          "intact is true when every line matches its hash and the line before it, and"
              + " every anchor in the object store matches the line it names. firstBreak names the"
              + " first line that does not match and why; mismatchedAnchors lists the anchors whose"
              + " line is gone or holds another hash, which is what a chain rewritten as a whole"
              + " shows.")
  public ApiResponse<Map<String, Object>> verify() {
    Map<String, Object> firstBreak = chain.firstBreak();
    List<Map<String, Object>> anchors = anchor.anchors();
    List<Map<String, Object>> mismatched = new ArrayList<>();
    for (Map<String, Object> one : anchors) {
      long id = ((Number) one.get("id")).longValue();
      String found = chain.hashOf(id);
      if (!Objects.equals(found, one.get("hash"))) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("anchored", one.get("hash"));
        entry.put("found", found);
        mismatched.add(entry);
      }
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("intact", firstBreak == null && mismatched.isEmpty());
    body.put("head", chain.head());
    body.put("firstBreak", firstBreak);
    body.put("anchors", anchors.size());
    body.put("mismatchedAnchors", mismatched);
    return ApiResponse.success(body);
  }

  @PostMapping("/anchor")
  @Operation(
      summary = "Copy the head of the history chain to the object store now",
      description =
          "The service also does this at a set interval. Returns the anchor, or null"
              + " when there is no history yet.")
  public ApiResponse<Map<String, Object>> anchorNow() {
    return ApiResponse.success(anchor.anchor());
  }
}
