// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

import java.util.Map;

// A corpus an app owns in the knowledge base and the catalogue of its sources.
public interface KnowledgeNamespace {

  String namespace();

  // Keyed by source id.
  Map<String, KnowledgeSource> sources();
}
