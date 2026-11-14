// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.util.List;
import java.util.stream.Collectors;

// A model file that cannot be used, with every problem found and where.
public class ModelException extends RuntimeException {

  // line and column count from 1; 0 means the problem has no single position.
  public record Problem(int line, int column, String message) {
    @Override
    public String toString() {
      return line > 0 ? "line " + line + ", column " + column + ": " + message : message;
    }
  }

  private final List<Problem> problems;

  public ModelException(List<Problem> problems) {
    super(problems.stream().map(Problem::toString).collect(Collectors.joining("; ")));
    this.problems = List.copyOf(problems);
  }

  public List<Problem> problems() {
    return problems;
  }
}
