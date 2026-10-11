// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

import java.util.List;

// Passages that answer a question, best first; empty when none is relevant enough.
public record KnowledgeAnswer(String question,List<KnowledgePassage>passages){}
