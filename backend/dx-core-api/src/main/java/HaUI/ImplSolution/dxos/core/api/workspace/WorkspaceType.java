// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.workspace;

// A kind of workspace an app registers: codePrefix starts every generated code (prefix H gives
// H0001, H0002, ...).
public record WorkspaceType(String type,String codePrefix,String label){}
