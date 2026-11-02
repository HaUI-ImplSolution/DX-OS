// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.security;

// How often one signed-in user may call a path each minute. The method is null for any method;
// the path is a Spring path pattern such as /api/v1/tickets/{id}/attachments.
public record RateLimitRule(String method,String pathPattern,int maxPerMinute){

public static RateLimitRule post(String pathPattern,int maxPerMinute){return new RateLimitRule("POST",pathPattern,maxPerMinute);}}
