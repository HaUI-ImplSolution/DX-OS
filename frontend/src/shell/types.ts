// SPDX-License-Identifier: Apache-2.0
// Envelope of every backend reply.
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  errorCode?: string;
  data: T;
}
