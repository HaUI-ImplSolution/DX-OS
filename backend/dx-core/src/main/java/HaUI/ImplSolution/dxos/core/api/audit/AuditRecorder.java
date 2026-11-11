// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.audit;

// Writes an audit record in its own transaction, so it is kept even when the caller's transaction
// rolls back. A failed write is logged and never reaches the caller.
public interface AuditRecorder {

  void record(AuditEvent event);
}
