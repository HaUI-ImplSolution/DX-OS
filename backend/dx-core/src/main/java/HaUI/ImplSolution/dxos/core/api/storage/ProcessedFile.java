// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.storage;

// A file after processing: the bytes to store, the content type they carry and the extension of
// the object key.
public record ProcessedFile(byte[]data,String contentType,String extension){}
