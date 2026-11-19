import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const MINI_APP_URL = Deno.env.get("MINI_APP_URL") ?? "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

async function tgCall(method: string, payload: Record<string, unknown>) {
  const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return res.json();
}

function mainMenuInlineKeyboard(slugToOpen?: string) {
  const rows: Array<Array<Record<string, unknown>>> = [];
  if (slugToOpen) {
    rows.push([
      {
        text: "3D Muzeyni ochish",
        web_app: { url: `${MINI_APP_URL}?startapp=${encodeURIComponent(slugToOpen)}` },
      },
    ]);
  }
  rows.push([
    {
      text: "Muzey yaratish",
      web_app: { url: `${MINI_APP_URL}?page=create` },
    },
  ]);
  rows.push([
    { text: "Mening muzeylarim", callback_data: "my_museums" },
    { text: "Tarif", callback_data: "pricing" },
  ]);
  return { inline_keyboard: rows };
}

serve(async (req) => {
  try {
    const update = await req.json();
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    if (update.message) {
      const msg = update.message;
      const chatId = msg.chat.id;
      const fromId = msg.from?.id ?? chatId;
      const tgUserId = `tg_${fromId}`;
      const text: string = msg.text ?? "";

      // Ensure subscription row records chatId for 3-day expiry reminders
      const { data: existingSub } = await supabase
        .from("subscriptions")
        .select("id")
        .eq("user_id", tgUserId)
        .maybeSingle();

      if (!existingSub) {
        await supabase.from("subscriptions").insert({
          id: crypto.randomUUID(),
          user_id: tgUserId,
          plan: "FREE",
          starts_at: Date.now(),
          expires_at: 0,
          status: "ACTIVE",
          telegram_chat_id: chatId,
          reminder_3d_sent: false,
        });
      } else {
        await supabase
          .from("subscriptions")
          .update({ telegram_chat_id: chatId })
          .eq("user_id", tgUserId);
      }

      if (text.startsWith("/start")) {
        const parts = text.trim().split(/\s+/);
        const startArg = parts.length > 1 ? parts[1] : undefined;

        const welcome =
          "Assalomu alaykum! Muzeycha botiga xush kelibsiz.\n\n" +
          "Rasmlaringizdan 3D virtual muzey yarating va yaqinlaringizga yuboring.";

        await tgCall("sendMessage", {
          chat_id: chatId,
          text: welcome,
          reply_markup: mainMenuInlineKeyboard(startArg),
        });
      } else if (text === "Mening muzeylarim") {
        await sendMyMuseums(supabase, chatId, tgUserId);
      } else if (text === "Tarif") {
        await sendPricing(supabase, chatId, tgUserId);
      } else {
        await tgCall("sendMessage", {
          chat_id: chatId,
          text: "Quyidagi tugmalardan foydalaning:",
          reply_markup: mainMenuInlineKeyboard(),
        });
      }
    } else if (update.callback_query) {
      const cq = update.callback_query;
      const chatId = cq.message.chat.id;
      const fromId = cq.from.id;
      const tgUserId = `tg_${fromId}`;

      await tgCall("answerCallbackQuery", { callback_query_id: cq.id });

      if (cq.data === "my_museums") {
        await sendMyMuseums(supabase, chatId, tgUserId);
      } else if (cq.data === "pricing") {
        await sendPricing(supabase, chatId, tgUserId);
      }
    }

    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ ok: false, error: (err as Error).message }), {
      status: 200,
    });
  }
});

async function sendMyMuseums(
  supabase: ReturnType<typeof createClient>,
  chatId: number,
  tgUserId: string
) {
  const { data: museums } = await supabase
    .from("museums")
    .select("slug, title, room_style")
    .eq("owner_user_id", tgUserId)
    .order("created_at", { ascending: false })
    .limit(8);

  if (!museums || museums.length === 0) {
    await tgCall("sendMessage", {
      chat_id: chatId,
      text: "Hali muzey yaratilmagan. Birinchi 3D muzeyingizni yarating!",
      reply_markup: {
        inline_keyboard: [
          [{ text: "Muzey yaratish", web_app: { url: `${MINI_APP_URL}?page=create` } }],
        ],
      },
    });
    return;
  }

  const buttons = museums.map((m) => [
    {
      text: `${m.title}`,
      web_app: { url: `${MINI_APP_URL}?startapp=${encodeURIComponent(m.slug)}` },
    },
  ]);

  await tgCall("sendMessage", {
    chat_id: chatId,
    text: "Sizning muzeylaringiz (ochish uchun bosing):",
    reply_markup: { inline_keyboard: buttons },
  });
}

async function sendPricing(
  supabase: ReturnType<typeof createClient>,
  chatId: number,
  tgUserId: string
) {
  const { data: sub } = await supabase
    .from("subscriptions")
    .select("plan, status, expires_at")
    .eq("user_id", tgUserId)
    .maybeSingle();

  const isPaidActive =
    sub && sub.plan !== "FREE" && sub.status === "ACTIVE" && Number(sub.expires_at) > Date.now();

  const statusLine = isPaidActive
    ? `Faol tarif: ${sub.plan} (tugash sanasi: ${new Date(Number(sub.expires_at)).toLocaleDateString("uz-UZ")})`
    : "Joriy tarif: Bepul (1 ta muzey, 5 tagacha rasm)";

  const text =
    `${statusLine}\n\n` +
    "Tariflar:\n" +
    "• Bepul: 1 ta muzey, 5 tagacha rasm, 3 ta oddiy uslub\n" +
    "• 6 oylik: 30 000 so'm — cheksiz muzey, 20 rasm, Romantik & To'y, musiqa\n" +
    "• 1 yillik: 100 000 so'm — barcha imkoniyatlar 1 yilga";

  await tgCall("sendMessage", {
    chat_id: chatId,
    text,
    reply_markup: {
      inline_keyboard: [
        [{ text: "Tarifni tanlash", web_app: { url: `${MINI_APP_URL}?page=pricing` } }],
      ],
    },
  });
}
