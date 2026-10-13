// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

// A status a case type declares.
// pending: the case waits for staff, so it counts as an open request and may be assigned.
// terminal: the case is finished and has no way out.
public record CaseStatus(String code,boolean pending,boolean terminal){

public CaseStatus{if(code==null||code.isBlank()){throw new IllegalArgumentException("A case status needs a code");}}

public static CaseStatus pending(String code){return new CaseStatus(code,true,false);}

public static CaseStatus waiting(String code){return new CaseStatus(code,false,false);}

public static CaseStatus terminal(String code){return new CaseStatus(code,false,true);}}
