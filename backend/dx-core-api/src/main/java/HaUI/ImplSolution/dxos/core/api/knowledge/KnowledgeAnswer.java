// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

import java.util.List;

// Passages that answer a question, best first; empty when none is relevant enough.
public record KnowledgeAnswer(String question,List<KnowledgePassage>passages,String scopeStatus,String reasonCode,String namespace){

public KnowledgeAnswer(String question,List<KnowledgePassage>passages){this(question,passages,passages.isEmpty()?"IN_SCOPE_NO_EVIDENCE":"ANSWERABLE",passages.isEmpty()?"NO_EVIDENCE":"EVIDENCE_FOUND",null);}}
