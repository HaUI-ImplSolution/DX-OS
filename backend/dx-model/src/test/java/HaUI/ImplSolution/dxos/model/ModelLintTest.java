// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelLintTest {

  private static final String VALID =
      "format: 1\nname: x\nroles:\n  - name: U\nentities:\n  i:\n    fields:\n"
          + "      a: {type: text}\n    access: {U: all}\n";

  private record Result(int exit, String out, String err) {}

  private static Result run(String stdin, String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int exit =
        ModelLint.run(
            args,
            new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Result(
        exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void aValidFileIsOk(@TempDir Path dir) throws IOException {
    Path file = Files.writeString(dir.resolve("model.yaml"), VALID);

    Result result = run("", file.toString());

    assertEquals(ModelLint.OK, result.exit());
    assertEquals(file + ": ok\n" + ModelLint.NOT_CHECKED + "\n", result.out());
  }

  @Test
  void aProblemIsPrintedWithItsFileLineAndColumn(@TempDir Path dir) throws IOException {
    Path file =
        Files.writeString(
            dir.resolve("bad.yaml"), VALID.replace("access: {U: all}", "access: {G: all}"));

    Result result = run("", file.toString());

    assertEquals(ModelLint.PROBLEMS, result.exit());
    assertTrue(result.out().startsWith(file + ":9:"), result.out());
    assertTrue(result.out().contains("role G is not declared in roles"), result.out());
  }

  @Test
  void aFileThatExtendsATemplateIsCheckedForItsOwnPartOnly(@TempDir Path dir) throws IOException {
    Path file = Files.writeString(dir.resolve("mine.yaml"), "extends: support_desk\nname: mine\n");

    Result result = run("", file.toString());

    assertEquals(ModelLint.OK, result.exit());
    assertTrue(result.out().contains("ok (extends support_desk"), result.out());
  }

  @Test
  void standardInputCanBeLabelled() {
    Result result = run(VALID, "--name", "pipeline.yaml", "-");

    assertEquals(ModelLint.OK, result.exit());
    assertEquals("pipeline.yaml: ok\n" + ModelLint.NOT_CHECKED + "\n", result.out());
    assertTrue(run(VALID, "-").out().startsWith("<stdin>: ok\n"));
  }

  @Test
  void everyFileIsCheckedAndTheWorstResultIsTheExitCode(@TempDir Path dir) throws IOException {
    Path good = Files.writeString(dir.resolve("good.yaml"), VALID);
    Path bad = Files.writeString(dir.resolve("bad.yaml"), "format: 1\nname: x\n");

    Result result = run("", good.toString(), bad.toString());

    assertEquals(ModelLint.PROBLEMS, result.exit());
    assertTrue(result.out().contains(good + ": ok"));
    assertTrue(result.out().contains(bad.toString()));
  }

  @Test
  void aMissingFileIsUnusable(@TempDir Path dir) {
    Result result = run("", dir.resolve("nothing.yaml").toString());

    assertEquals(ModelLint.UNUSABLE, result.exit());
    assertTrue(result.err().contains("no such file"), result.err());
  }

  @Test
  void theCommandNeedsFilesAndKnownOptions() {
    assertEquals(ModelLint.UNUSABLE, run("").exit());
    assertTrue(run("").err().contains(ModelLint.USAGE));
    assertEquals(ModelLint.UNUSABLE, run("", "--bogus", "x").exit());
    assertTrue(run("", "--bogus", "x").err().contains("unknown option: --bogus"));
    assertEquals(
        ModelLint.UNUSABLE, run("", "--name", "L", "file.yaml").exit(), "a label needs stdin");
  }

  @Test
  void helpPrintsTheUsage() {
    Result result = run("", "--help");

    assertEquals(ModelLint.OK, result.exit());
    assertEquals(ModelLint.USAGE + "\n", result.out());
  }
}
