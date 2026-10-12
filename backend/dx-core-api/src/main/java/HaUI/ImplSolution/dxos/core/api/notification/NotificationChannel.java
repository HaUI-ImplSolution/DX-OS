// SPDX-License-Identifier: Apache-2.0
package HaUI.ImplSolution.dxos.core.api.notification;

// A chat channel an app posts to. key names the channel in ChatWebhook; its webhook URL is read
// from mattermost.webhook.url-<key> and falls back to mattermost.webhook.url.
public record NotificationChannel(String key,String label){}
