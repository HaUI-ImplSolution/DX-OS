// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.bi;

import java.util.List;

// The views and tables the read-only BI role may select from. Anything not listed by some
// provider stays closed to it, including tables added later.
public interface BiDatasetProvider {

  List<String> relations();
}
