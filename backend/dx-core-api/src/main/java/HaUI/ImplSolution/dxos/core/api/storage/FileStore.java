// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.storage;

// Stores uploaded files in object storage after the processor of their purpose accepted them.
public interface FileStore {

  // Runs the processor of the purpose without storing the result.
  ProcessedFile process(String purpose, byte[] original, String filename);

  // Processes and stores the file; returns the object key.
  String store(String purpose, byte[] original, String filename, String uploadedBy);
}
