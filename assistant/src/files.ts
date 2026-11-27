// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, existsSync, chmodSync, chownSync, lstatSync, readdirSync, renameSync, rmSync } from 'node:fs';
import { join } from 'node:path';

// Replaces a private file atomically with mode 0600.
export function write(path: string, value: string | Buffer) {
  if (existsSync(path) && (!lstatSync(path).isFile() || lstatSync(path).isSymbolicLink()))
    throw new Error('Use ordinary private configuration files');
  const data = Buffer.isBuffer(value) ? value : Buffer.from(value);
  if (!existsSync(path) || !readFileSync(path).equals(data)) {
    const temporary = path + '.' + randomBytes(8).toString('hex') + '.tmp';
    try {
      writeFileSync(temporary, data, { mode: 0o600, flag: 'wx' });
      renameSync(temporary, path);
    } finally { rmSync(temporary, { force: true }); }
  }
  chmodSync(path, 0o600);
}

// The service uses the node uid/gid 1000.
export function own(path: string) {
  const entry = lstatSync(path);
  if (entry.isSymbolicLink()) return;
  if (entry.uid !== 1000 || entry.gid !== 1000) chownSync(path, 1000, 1000);
  if (entry.isDirectory()) for (const name of readdirSync(path)) own(join(path, name));
}
