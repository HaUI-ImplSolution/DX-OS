// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.catalog;

import HaUI.ImplSolution.dxos.model.Booking;
import HaUI.ImplSolution.dxos.model.Completion;
import HaUI.ImplSolution.dxos.model.Model;
import HaUI.ImplSolution.dxos.model.Model.Entity;
import HaUI.ImplSolution.dxos.model.Model.Process;
import HaUI.ImplSolution.dxos.model.ModelException;
import HaUI.ImplSolution.dxos.model.ModelException.Problem;
import HaUI.ImplSolution.dxos.model.ModelParser;
import HaUI.ImplSolution.dxos.model.ProcessFamily;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

// What the control panel draws for a model file being written: the process of every entity that has
// one, read from the file alone and never compared with the running model.
@Service
public class ProcessDiagramService {

  // A final state with the actions taken when a record reaches it.
  public record StateView(String name, String label, boolean terminal,
      List<Completion> completions) {}

  // The process of one entity: its family's states and actions, and what the file adds to them.
  // Seconds are null when the file sets no deadline or auto_close; awaiting lists the states where
  // the deadline runs.
  public record ProcessView(String entity, String label, String family, String initial,
      List<String> handlers, boolean allowSelfDecision, Long deadlineSeconds, Long autoCloseSeconds,
      String autoCloseAction, Booking booking, List<StateView> states,
      List<ProcessFamily.Action> actions, List<String> awaiting) {}

  // valid is false when the file has problems; processes is then empty.
  public record Diagram(boolean valid, List<Problem> errors, List<ProcessView> processes) {}

  public Diagram diagram(String content) {
    Model model;
    try {
      model = ModelParser.parse(content);
    } catch (ModelException e) {
      return new Diagram(false, e.problems(), List.of());
    }
    List<ProcessView> processes = new ArrayList<>();
    for (Entity entity : model.entities().values()) {
      if (entity.process() != null) {
        processes.add(view(entity));
      }
    }
    return new Diagram(true, List.of(), processes);
  }

  private static ProcessView view(Entity entity) {
    Process process = entity.process();
    ProcessFamily family = process.definition();
    List<StateView> states = family.states().stream()
        .map(state -> new StateView(state.name(), state.label(), state.terminal(),
            process.after() == null ? List.<Completion>of()
                : process.after().getOrDefault(state.name(), List.of())))
        .toList();
    List<String> awaiting = family.states().stream().map(ProcessFamily.State::name)
        .filter(family::awaitsHandler).toList();
    return new ProcessView(entity.name(), entity.label(), family.name(), family.initial(),
        process.handlers(), process.allowSelfDecision(), seconds(process.deadline()),
        seconds(process.autoClose()), process.autoCloseAction(), process.booking(), states,
        family.actions(), awaiting);
  }

  private static Long seconds(Duration duration) {
    return duration == null ? null : duration.toSeconds();
  }
}
