export interface TelegramUser {
  id: number;
  first_name?: string;
  last_name?: string;
  username?: string;
  language_code?: string;
}

export interface VerifiedInitData {
  user: TelegramUser;
  authDate: number;
  startParam?: string;
}

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

async function hmacSha256(keyData: Uint8Array, message: string): Promise<ArrayBuffer> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyData,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  return await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(message));
}

/**
 * Verifies Telegram Mini App initData using HMAC-SHA256 per Telegram WebApp specification.
 */
export async function verifyTelegramInitData(
  initData: string,
  botToken: string,
  maxAgeSeconds = 86400
): Promise<VerifiedInitData> {
  if (!initData || !botToken) {
    throw new Error("initData yoki botToken bo'sh");
  }

  const params = new URLSearchParams(initData);
  const hash = params.get("hash");
  if (!hash) {
    throw new Error("initData ichida hash topilmadi");
  }
  params.delete("hash");

  const entries = Array.from(params.entries()).sort(([a], [b]) => a.localeCompare(b));
  const dataCheckString = entries.map(([k, v]) => `${k}=${v}`).join("\n");

  const secretKeyBuf = await hmacSha256(new TextEncoder().encode("WebAppData"), botToken);
  const calculatedBuf = await hmacSha256(new Uint8Array(secretKeyBuf), dataCheckString);
  const calculatedHex = toHex(calculatedBuf);

  if (!timingSafeEqual(calculatedHex, hash.toLowerCase())) {
    throw new Error("initData HMAC imzosi yaroqsiz");
  }

  const authDate = Number(params.get("auth_date") || "0");
  const nowSec = Math.floor(Date.now() / 1000);
  if (!authDate || nowSec - authDate > maxAgeSeconds) {
    throw new Error("initData muddati eskirgan");
  }

  const userRaw = params.get("user");
  if (!userRaw) {
    throw new Error("initData ichida user ma'lumoti yo'q");
  }

  const user = JSON.parse(userRaw) as TelegramUser;
  if (!user?.id) {
    throw new Error("Telegram user.id topilmadi");
  }

  return {
    user,
    authDate,
    startParam: params.get("start_param") || undefined,
  };
}

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
};
