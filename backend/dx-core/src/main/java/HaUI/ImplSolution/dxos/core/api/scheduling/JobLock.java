// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.scheduling;

// Lets a scheduled job run on one backend instance at a time.
public interface JobLock {

  // Runs the job unless another instance is running the job of the same name; returns whether it
  // ran.
  boolean runIfFree(String name, Runnable job);
}
