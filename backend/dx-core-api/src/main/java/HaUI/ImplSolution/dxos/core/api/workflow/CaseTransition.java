// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.Set;

// A step a case may take. action names the event written to the history (the target status when
// null). roles lists who may take the step; empty allows anyone the caller already authorised.
public record CaseTransition(String from,String to,String action,Set<String>roles){

public CaseTransition{if(from==null||to==null){throw new IllegalArgumentException("A transition needs a from and a to status");}action=action==null?to:action;roles=roles==null?Set.of():Set.copyOf(roles);}

public CaseTransition(String from,String to){this(from,to,null,Set.of());}

public CaseTransition(String from,String to,String action){this(from,to,action,Set.of());}

public CaseTransition withRoles(String...allowed){return new CaseTransition(from,to,action,Set.of(allowed));}}
