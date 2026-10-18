// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.knowledge;

// A document a passage of the corpus is based on: url is the publisher's page, documentUrl the
// original file when there is one. reviewStatus is OFFICIAL for documents issued by the
// university and IN_REVIEW until an expert confirmed the document.
public record KnowledgeSource(String sourceId,String title,String organization,String url,String documentUrl,String license,String reviewStatus){}
