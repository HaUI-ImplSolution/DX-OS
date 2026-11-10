// SPDX-License-Identifier: Apache-2.0
import { existsSync } from 'node:fs';
import type { ChildProcess } from 'node:child_process';

// A probe succeeds when its isolated tool ran; no final model reply is needed.
export async function waitForCapability(child: ChildProcess, marker: string, timeoutMs = 90000): Promise<boolean> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let poll: ReturnType<typeof setInterval> | undefined;
  const passed = await new Promise<boolean>(resolve => {
    const finish = (result: boolean) => { clearTimeout(timer); clearInterval(poll); resolve(result); };
    child.once('error', () => finish(false));
    child.once('exit', () => finish(existsSync(marker)));
    poll = setInterval(() => { if (existsSync(marker)) finish(true); }, 100);
    timer = setTimeout(() => finish(existsSync(marker)), timeoutMs);
  });
  const signal = (name: NodeJS.Signals) => {
    if (child.pid) try { process.kill(-child.pid, name); } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ESRCH') throw error;
    }
  };
  try {
    if (child.exitCode === null && child.signalCode === null) {
      await new Promise<void>(resolve => {
        const deadline = setTimeout(resolve, 2000);
        child.once('exit', () => { clearTimeout(deadline); resolve(); });
        signal('SIGTERM');
      });
    }
  } finally { signal('SIGKILL'); }
  return passed;
}
