// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.lab.service;

import HaUI.ImplSolution.dxos.lab.model.TicketCategory;
import HaUI.ImplSolution.dxos.lab.model.TicketPriority;
import HaUI.ImplSolution.dxos.lab.repository.TicketCategoryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Suggests a category and a priority for a description with rules, and says why. The rules are a
// keyword list per category plus priority escalations, kept in a file so they can change without
// code; keywords an administrator adds to a category are matched as well. Matching ignores case,
// diacritics and punctuation and works on whole words.
@Component
@Transactional(readOnly = true)
public class TicketClassifier {

  public record Suggestion(TicketCategory category, TicketPriority priority,
      List<String> matchedKeywords, List<String> reasons) {}

  private record Keyword(String display, Pattern pattern, int length) {}

  private record PriorityRule(String id, TicketPriority raiseTo, List<Keyword> keywords,
      String reason) {}

  private final TicketCategoryRepository categoryRepository;
  private final Map<String, List<Keyword>> fileKeywords = new LinkedHashMap<>();
  private final List<PriorityRule> priorityRules = new ArrayList<>();
  private final String defaultCategory;

  public TicketClassifier(TicketCategoryRepository categoryRepository, ObjectMapper objectMapper,
      ResourceLoader resourceLoader,
      @Value("${lab.classifier.rules-path:classpath:lab/classifier-rules.json}") String rulesPath)
      throws IOException {
    this.categoryRepository = categoryRepository;
    Resource resource = resourceLoader.getResource(rulesPath);
    try (InputStream in = resource.getInputStream()) {
      JsonNode root = objectMapper.readTree(in);
      this.defaultCategory = root.path("defaultCategory").asText("OTHER");
      for (JsonNode category : root.path("categories")) {
        fileKeywords.put(category.path("code").asText(), keywords(category.path("keywords")));
      }
      for (JsonNode rule : root.path("priorityRules")) {
        priorityRules.add(new PriorityRule(rule.path("id").asText(),
            TicketPriority.valueOf(rule.path("raiseTo").asText()), keywords(rule.path("keywords")),
            rule.path("reason").asText()));
      }
    }
  }

  // Returns null when the system has no active category at all.
  public Suggestion classify(String text) {
    List<TicketCategory> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
    if (categories.isEmpty()) {
      return null;
    }
    String normalized = normalize(text);
    TicketCategory best = null;
    List<String> bestMatches = List.of();
    int bestScore = 0;
    int bestLength = 0;
    for (TicketCategory category : categories) {
      List<Keyword> keywords =
          new ArrayList<>(fileKeywords.getOrDefault(category.getCode(), List.of()));
      keywords.addAll(keywords(category.getKeywords()));
      List<String> matches = new ArrayList<>();
      int length = 0;
      for (Keyword keyword : keywords) {
        if (keyword.pattern().matcher(normalized).find() && !matches.contains(keyword.display())) {
          matches.add(keyword.display());
          length += keyword.length();
        }
      }
      if (matches.size() > bestScore
          || (matches.size() == bestScore && !matches.isEmpty() && length > bestLength)) {
        best = category;
        bestMatches = matches;
        bestScore = matches.size();
        bestLength = length;
      }
    }
    List<String> reasons = new ArrayList<>();
    if (best == null) {
      best = categories.stream().filter(c -> c.getCode().equals(defaultCategory)).findFirst()
          .orElse(categories.get(0));
      reasons.add("Không khớp từ khóa nào, dùng loại mặc định \"%s\"".formatted(best.getName()));
    } else {
      reasons.add("Khớp từ khóa %s nên chọn loại \"%s\"".formatted(
          String.join(", ", bestMatches.stream().map(k -> "\"" + k + "\"").toList()),
          best.getName()));
    }
    TicketPriority priority = best.getDefaultPriority();
    Set<String> matched = new LinkedHashSet<>(bestMatches);
    for (PriorityRule rule : priorityRules) {
      List<String> hits = new ArrayList<>();
      for (Keyword keyword : rule.keywords()) {
        if (keyword.pattern().matcher(normalized).find()) {
          hits.add(keyword.display());
        }
      }
      if (hits.isEmpty()) {
        continue;
      }
      matched.addAll(hits);
      if (rule.raiseTo().ordinal() < priority.ordinal()) {
        priority = rule.raiseTo();
      }
      reasons.add("%s (khớp \"%s\"), ưu tiên tối thiểu %s".formatted(rule.reason(),
          String.join("\", \"", hits), rule.raiseTo()));
    }
    return new Suggestion(best, priority, List.copyOf(matched), List.copyOf(reasons));
  }

  private static List<Keyword> keywords(JsonNode array) {
    List<Keyword> result = new ArrayList<>();
    for (JsonNode node : array) {
      addKeyword(result, node.asText());
    }
    return result;
  }

  private static List<Keyword> keywords(String commaSeparated) {
    List<Keyword> result = new ArrayList<>();
    if (commaSeparated != null) {
      for (String part : commaSeparated.split(",")) {
        addKeyword(result, part);
      }
    }
    return result;
  }

  private static void addKeyword(List<Keyword> into, String raw) {
    String display = raw == null ? "" : raw.strip();
    String normalized = normalize(display).strip();
    if (normalized.isEmpty()) {
      return;
    }
    into.add(new Keyword(display,
        Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalized) + "(?![\\p{L}\\p{N}])"),
        normalized.length()));
  }

  // Lower case, no diacritics, every other character a single space.
  static String normalize(String value) {
    if (value == null) {
      return "";
    }
    String text = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "").replace('đ', 'd');
    return " " + text.replaceAll("[^\\p{L}\\p{N}]+", " ").strip() + " ";
  }
}
