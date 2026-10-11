// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.storage;

// Checks and cleans a file before it is stored. An app registers one per purpose.
public interface FileProcessor {

  // What the files are for, e.g. the name of the upload flow; the key FileStore looks it up by.
  String purpose();

  // Returns the file to store, or throws IllegalArgumentException for a file that is not
  // acceptable.
  ProcessedFile process(byte[] original, String filename);
}
