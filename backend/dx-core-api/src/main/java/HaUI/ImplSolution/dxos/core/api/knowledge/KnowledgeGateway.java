// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

import java.util.Map;

// Question answering over the corpus of one namespace. Passages come from the AI retriever and
// carry the documents they are based on.
public interface KnowledgeGateway {

  // filters: narrows the search by fields of the retriever (such as one disease of a catalogue).
  // Throws AiServiceException when the AI service is unavailable.
  KnowledgeAnswer ask(String namespace, String question, Map<String, String> filters, int topK);
}
