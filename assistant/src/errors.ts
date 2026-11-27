// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
export class ApiError extends Error {
  constructor(public readonly status: number, public readonly errorCode: string, message: string,
    public readonly reason?: string) {
    super(message);
  }
}
