// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.farm.model;

import HaUI.ImplSolution.dxos.core.api.knowledge.KnowledgeSource;
import java.util.List;

// A knowledge passage retrieved for a diagnosis, stored as it was shown to the engineer.
public record KnowledgeEvidence(String content,String documentTitle,double score,List<KnowledgeSource>sources){}
