// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { randomBytes } from 'node:crypto';
import { existsSync, lstatSync, readFileSync, renameSync } from 'node:fs';
import { join, isAbsolute } from 'node:path';
import { Store } from './storage/store.js';
import { own, write } from './files.js';

// Replaces the master key and seals the state again under it; the assistant must be stopped.
// The new key is written to master.key.next first, so an interrupted run is finished by running it again.
const root = process.argv[2];
if (!root || !isAbsolute(root) || root === '/') throw new Error('Use rotate with the private absolute state directory');
const master = join(root, 'private/master.key'), next = master + '.next', database = join(root, 'control/assistant.sqlite');
const read = (path: string) => {
  const entry = lstatSync(path);
  if (!entry.isFile() || entry.isSymbolicLink() || (entry.mode & 0o077) !== 0) throw new Error('Use ordinary private key files with mode 0600');
  return readFileSync(path);
};
if (!existsSync(master) || !existsSync(database)) throw new Error('There is no assistant state to rotate');
let done = false;
if (existsSync(next)) {
  // The state may already hold the new key from an interrupted run.
  try { new Store(database, read(next)).close(); renameSync(next, master); done = true; } catch { /* the state still holds the current key */ }
}
if (!done) {
  const store = new Store(database, read(master));
  try {
    const key = randomBytes(32);
    write(next, key);
    store.rotateKey(key);
  } finally { store.close(); }
  renameSync(next, master);
}
if (process.getuid?.() === 0) own(root);
console.log('The master key was replaced and the assistant state was sealed again under it.');
