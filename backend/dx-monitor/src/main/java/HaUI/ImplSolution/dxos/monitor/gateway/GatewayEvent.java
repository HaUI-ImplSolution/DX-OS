// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.monitor.gateway;

import java.time.Instant;

// One gateway request kept for tracing. The path never carries a query string.
//
// suppressed: further events of the same address and kind that were counted here instead of kept
public record GatewayEvent(Instant occurredAt,GatewayEventKind kind,String ip,String host,String method,String path,int status,int requestTimeMs,String upstream,String requestId,String cfRay,String userAgent,int suppressed){

public GatewayEvent withSuppressed(int count){return new GatewayEvent(occurredAt,kind,ip,host,method,path,status,requestTimeMs,upstream,requestId,cfRay,userAgent,count);}}
