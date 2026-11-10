// SPDX-License-Identifier: Apache-2.0
import { randomUUID } from 'node:crypto';
import { ApiError } from '../errors.js';
import { Store, type Owner } from '../storage/store.js';
import { Runtimes } from '../runtime/slots.js';

export type Telegram = { token: string; botId: string; username: string; accountId?: string; userId?: string };

export class TelegramAccounts {
  constructor(private readonly store: Store, private readonly runtimes: Runtimes) {}

  status(owner: Owner) {
    const saved = this.store.getSecret<Telegram>(owner, 'telegram');
    return { configured: !!saved, paired: !!saved?.userId,
      username: saved?.username ?? null, url: saved ? `https://t.me/${saved.username}` : null };
  }

  async configure(owner: Owner, token: string): Promise<void> {
    if (!/^[0-9]{5,20}:[A-Za-z0-9_-]{25,120}$/.test(token))
      throw new ApiError(400, 'ERR_TELEGRAM_TOKEN', 'Enter the bot token issued by BotFather');
    const bot = await this.call<{ id: number; username: string }>(token, 'getMe');
    const webhook = await this.call<{ url?: string }>(token, 'getWebhookInfo');
    if (webhook.url) throw new ApiError(409, 'ERR_TELEGRAM_WEBHOOK', 'Use a private bot without an existing webhook');
    await this.runtimes.update(owner, () => {
      this.store.bindTelegram(owner, String(bot.id), { token, botId: String(bot.id), username: bot.username, accountId: `bot-${bot.id}-${randomUUID()}` });
    });
  }

  async unlink(owner: Owner): Promise<void> {
    await this.runtimes.update(owner, () => this.store.unbindTelegram(owner));
  }

  async pair(owner: Owner, code: string): Promise<void> {
    if (!/^[A-Z0-9]{6,12}$/.test(code)) throw new ApiError(400, 'ERR_PAIRING_CODE', 'Enter the code shown by your Telegram bot');
    const current = this.store.getSecret<Telegram>(owner, 'telegram');
    if (!current) throw new ApiError(409, 'ERR_TELEGRAM_REQUIRED', 'Configure Telegram first');
    if (current.userId) throw new ApiError(409, 'ERR_ALREADY_PAIRED', 'Configure the bot again before changing its user');
    await this.runtimes.pair(owner, code, userId => {
      const saved = this.store.getSecret<Telegram>(owner, 'telegram');
      if (!saved) throw new ApiError(409, 'ERR_TELEGRAM_REQUIRED', 'Configure Telegram first');
      if (saved.userId) throw new ApiError(409, 'ERR_ALREADY_PAIRED', 'Configure the bot again before changing its user');
      this.store.setSecret(owner, 'telegram', { ...saved, userId });
    });
  }

  private async call<T>(token: string, method: 'getMe' | 'getWebhookInfo'): Promise<T> {
    try {
      const response = await fetch(`https://api.telegram.org/bot${token}/${method}`, {
        redirect: 'error', signal: AbortSignal.timeout(10000),
      });
      const value = await response.json() as { ok: boolean; result?: T };
      if (!response.ok || !value.ok || !value.result) throw new Error('Telegram refused');
      return value.result;
    } catch { throw new ApiError(502, 'ERR_TELEGRAM_UNAVAILABLE', 'Telegram could not verify this bot; check its token and connection'); }
  }
}
