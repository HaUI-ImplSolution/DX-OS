// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.notification;

// A kind of message an app sends. code is stored with each message and shown to clients; label
// and icon are the default way to present it.
public record NotificationTypeDefinition(String code,String label,String icon){

public NotificationTypeDefinition{if(code==null||code.isBlank()){throw new IllegalArgumentException("A notification type needs a code");}}}
