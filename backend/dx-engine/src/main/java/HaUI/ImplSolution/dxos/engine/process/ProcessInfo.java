// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.engine.process;

// The workflow an entity runs, as shown to clients.
public record ProcessInfo(String template,String approver,boolean allowSelfDecision){}
