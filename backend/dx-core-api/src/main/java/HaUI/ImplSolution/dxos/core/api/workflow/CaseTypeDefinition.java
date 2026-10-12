// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workflow;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// The lifecycle of one case type: its statuses and the steps between them. An app registers one
// as a bean per case type; the core accepts only registered case types.
public record CaseTypeDefinition(String caseType,String initialStatus,List<CaseStatus>statuses,List<CaseTransition>transitions){

public CaseTypeDefinition{if(caseType==null||caseType.isBlank()){throw new IllegalArgumentException("A case type needs a name");}statuses=List.copyOf(statuses);transitions=List.copyOf(transitions);Set<String>codes=statuses.stream().map(CaseStatus::code).collect(Collectors.toSet());if(codes.size()!=statuses.size()){throw new IllegalArgumentException("Duplicate status in case type "+caseType);}if(!codes.contains(initialStatus)){throw new IllegalArgumentException("Initial status %s is not declared in case type %s".formatted(initialStatus,caseType));}for(CaseTransition transition:transitions){if(!codes.contains(transition.from())||!codes.contains(transition.to())){throw new IllegalArgumentException("Transition %s -> %s of case type %s uses an undeclared".formatted(transition.from(),transition.to(),caseType)+" status");}}for(CaseStatus status:statuses){if(status.terminal()&&transitions.stream().anyMatch(t->t.from().equals(status.code()))){throw new IllegalArgumentException("Terminal status %s of case type %s has a way out".formatted(status.code(),caseType));}}}

public Optional<CaseStatus>status(String code){return statuses.stream().filter(status->status.code().equals(code)).findFirst();}

public Optional<CaseTransition>transition(String from,String to){return transitions.stream().filter(t->t.from().equals(from)&&t.to().equals(to)).findFirst();}

// Statuses in which a case waits for staff.
public Set<String>pendingStatuses(){return statuses.stream().filter(CaseStatus::pending).map(CaseStatus::code).collect(Collectors.toUnmodifiableSet());}

// PENDING -> APPROVED, REJECTED or CANCELLED; a REJECTED case may be resubmitted to PENDING;
// approved and cancelled cases are final.
public static CaseTypeDefinition approval(String caseType){return new CaseTypeDefinition(caseType,WorkflowStatus.PENDING,List.of(CaseStatus.pending(WorkflowStatus.PENDING),CaseStatus.terminal(WorkflowStatus.APPROVED),CaseStatus.waiting(WorkflowStatus.REJECTED),CaseStatus.terminal(WorkflowStatus.CANCELLED)),List.of(new CaseTransition(WorkflowStatus.PENDING,WorkflowStatus.APPROVED),new CaseTransition(WorkflowStatus.PENDING,WorkflowStatus.REJECTED),new CaseTransition(WorkflowStatus.PENDING,WorkflowStatus.CANCELLED),new CaseTransition(WorkflowStatus.REJECTED,WorkflowStatus.PENDING,WorkflowAction.RESUBMITTED)));}}
