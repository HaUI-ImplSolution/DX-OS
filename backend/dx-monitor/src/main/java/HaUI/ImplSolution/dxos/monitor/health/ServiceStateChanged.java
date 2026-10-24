// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.health;

import java.time.Instant;

// Published when a probed service goes from UP to DOWN or back. previous is null the first time a
// service is seen.
public record ServiceStateChanged(String service,String state,String previous,String detail,Instant at){

public static final String UP="UP";public static final String DOWN="DOWN";}
