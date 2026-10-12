// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

import java.util.List;
import java.util.Map;

// Text quoted from a document of the corpus, never generated. score: similarity to the question,
// 0 to 1. attributes: other fields the retriever returned for the document, keyed by name.
public record KnowledgePassage(String content,String documentTitle,double score,Map<String,String>attributes,List<KnowledgeSource>sources){}
