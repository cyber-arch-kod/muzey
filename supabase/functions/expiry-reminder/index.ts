import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const MINI_APP_URL = Deno.env.get("MINI_APP_URL") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

const THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000;

serve(async () => {
  try {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
    const now = Date.now();
    const threshold = now + THREE_DAYS_MS;

    // 1. Mark any past-due ACTIVE paid subscriptions as EXPIRED
    await supabase
      .from("subscriptions")
      .update({ status: "EXPIRED" })
      .neq("plan", "FREE")
      .eq("status", "ACTIVE")
      .lte("expires_at", now);

    // 2. Find active paid subscriptions expiring within 3 days that haven't been reminded
    const { data: dueSubs, error } = await supabase
      .from("subscriptions")
      .select("id, user_id, plan, expires_at, telegram_chat_id")
      .neq("plan", "FREE")
      .eq("status", "ACTIVE")
      .eq("reminder_3d_sent", false)
      .gt("expires_at", now)
      .lte("expires_at", threshold);

    if (error) {
      throw error;
    }

    let sentCount = 0;
    for (const sub of dueSubs ?? []) {
      const chatId = sub.telegram_chat_id;
      if (!chatId) continue;

      const dateStr = new Date(Number(sub.expires_at)).toLocaleDateString("uz-UZ");
      const msgText =
        `Eslatma: Muzeycha (${sub.plan}) obunangiz tugashiga 3 kun yoki undan kam vaqt qoldi (tugash sanasi: ${dateStr}).\n\n` +
        "Muzeylaringiz o'chib ketmaydi, ammo Romantik va To'y uslublari hamda qo'shimcha imkoniyatlar yopilmasligi uchun tarifni yangilang.";

      const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          chat_id: chatId,
          text: msgText,
          reply_markup: {
            inline_keyboard: [
              [{ text: "Tarifni yangilash", web_app: { url: `${MINI_APP_URL}?page=pricing` } }],
            ],
          },
        }),
      });

      if (res.ok) {
        await supabase
          .from("subscriptions")
          .update({ reminder_3d_sent: true })
          .eq("id", sub.id);
        sentCount++;
      }
    }

    return new Response(
      JSON.stringify({ ok: true, remindersSent: sentCount }),
      { headers: { "Content-Type": "application/json" }, status: 200 }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ ok: false, error: (err as Error).message }),
      { headers: { "Content-Type": "application/json" }, status: 500 }
    );
  }
});
