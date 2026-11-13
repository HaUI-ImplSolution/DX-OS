// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Checks model files with the reader the backend uses, so a file passes here exactly when the
// backend accepts it as a model. Needs no running system; what depends on the running models is
// left to a plan.
//
//   java -jar dxos-lint.jar [--name LABEL] FILE... | -
//
// Every problem is printed as FILE:LINE:COLUMN: MESSAGE (FILE: MESSAGE when it has no single
// position). The exit code is 0 when every file is a valid model, 1 when a file has problems and 2
// when the command or a file cannot be read.
public final class ModelLint {

  static final int OK = 0;
  static final int PROBLEMS = 1;
  static final int UNUSABLE = 2;

  static final String USAGE = "usage: dxos-lint [--name LABEL] FILE... | -";

  static final String NOT_CHECKED =
      "Checked the file alone. A plan also compares it with the running model, the webhook"
          + " targets and the existing data.";

  private ModelLint() {}

  public static void main(String[] args) {
    System.exit(run(args, System.in, System.out, System.err));
  }

  static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
    String label = null;
    List<String> files = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("--name") && i + 1 < args.length) {
        label = args[++i];
      } else if (arg.equals("-h") || arg.equals("--help")) {
        out.println(USAGE);
        return OK;
      } else if (arg.startsWith("-") && !arg.equals("-")) {
        err.println("dxos-lint: unknown option: " + arg);
        err.println(USAGE);
        return UNUSABLE;
      } else {
        files.add(arg);
      }
    }
    if (files.isEmpty() || (label != null && !files.equals(List.of("-")))) {
      err.println(USAGE);
      return UNUSABLE;
    }

    int exit = OK;
    for (String file : files) {
      boolean fromStdin = file.equals("-");
      String name = fromStdin ? (label == null ? "<stdin>" : label) : file;
      String content;
      try {
        content =
            new String(
                fromStdin ? stdin.readAllBytes() : Files.readAllBytes(Path.of(file)),
                StandardCharsets.UTF_8);
      } catch (NoSuchFileException e) {
        err.println("dxos-lint: " + name + ": no such file");
        exit = Math.max(exit, UNUSABLE);
        continue;
      } catch (IOException | RuntimeException e) {
        err.println("dxos-lint: " + name + ": cannot be read: " + e.getMessage());
        exit = Math.max(exit, UNUSABLE);
        continue;
      }
      try {
        ModelParser.parse(content);
        out.println(name + ": ok");
      } catch (ModelException e) {
        e.problems().forEach(problem -> out.println(describe(name, problem)));
        exit = Math.max(exit, PROBLEMS);
      } catch (RuntimeException e) {
        err.println("dxos-lint: " + name + ": the reader failed: " + e);
        exit = Math.max(exit, UNUSABLE);
      }
    }
    if (exit == OK) {
      out.println(NOT_CHECKED);
    }
    return exit;
  }

  static String describe(String file, ModelException.Problem problem) {
    return problem.line() > 0
        ? file + ":" + problem.line() + ":" + problem.column() + ": " + problem.message()
        : file + ": " + problem.message();
  }
}
