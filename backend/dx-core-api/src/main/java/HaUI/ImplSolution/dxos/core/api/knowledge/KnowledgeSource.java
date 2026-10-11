// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

// A document a passage of the corpus is based on. reviewStatus is IN_REVIEW until an expert
// confirmed the document.
public record KnowledgeSource(String sourceId,String title,String organization,String url,String license,String reviewStatus){}
