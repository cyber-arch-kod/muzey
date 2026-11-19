import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";
import { corsHeaders, verifyTelegramInitData } from "../_shared/telegramAuth.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

    const body = await req.json();
    const initData = String(body.initData ?? "");
    const verified = await verifyTelegramInitData(initData, botToken);
    const tgUserId = `tg_${verified.user.id}`;

    const supabase = createClient(supabaseUrl, serviceRoleKey);

    // Fetch or initialize subscription linked to this Telegram user
    const { data: existingSub } = await supabase
      .from("subscriptions")
      .select("*")
      .eq("user_id", tgUserId)
      .maybeSingle();

    let subscription = existingSub;
    const now = Date.now();

    if (!subscription) {
      const newSub = {
        id: crypto.randomUUID(),
        user_id: tgUserId,
        plan: "FREE",
        starts_at: now,
        expires_at: 0,
        status: "ACTIVE",
        telegram_chat_id: verified.user.id,
        reminder_3d_sent: false,
      };
      await supabase.from("subscriptions").insert(newSub);
      subscription = newSub;
    } else {
      // Sync chat ID & enforce expiry automatically
      const isExpired =
        subscription.plan !== "FREE" &&
        subscription.status === "ACTIVE" &&
        Number(subscription.expires_at) <= now;

      const patch: Record<string, unknown> = {
        telegram_chat_id: verified.user.id,
      };
      if (isExpired) {
        patch.status = "EXPIRED";
        subscription = { ...subscription, status: "EXPIRED" };
      }
      await supabase.from("subscriptions").update(patch).eq("user_id", tgUserId);
    }

    // Load museums owned by this Telegram user
    const { data: museums } = await supabase
      .from("museums")
      .select("*, museum_photos(*)")
      .eq("owner_user_id", tgUserId)
      .order("created_at", { ascending: false });

    return new Response(
      JSON.stringify({
        ok: true,
        userId: tgUserId,
        telegramUser: verified.user,
        startParam: verified.startParam ?? null,
        subscription,
        museums: museums ?? [],
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (err) {
    return new Response(
      JSON.stringify({ ok: false, error: (err as Error).message }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 401,
      }
    );
  }
});
