# Muzeycha — Android, Telegram Mini App & Bot

Muzeycha foydalanuvchilarga 3 tadan 20 tagacha rasmdan 3D virtual foto-muzey yaratib, yaqinlariga havola (`t.me/<bot>?startapp=<slug>`) yuborish imkonini beradi.

---

## 1. BotFather'da Telegram Bot va Mini App yaratish

1. Telegram'da **@BotFather** ni oching va `/newbot` buyrug'ini yuboring.
2. Bot nomini (masalan, `Muzeycha`) va username'ini (masalan, `muzeycha_uz_bot`) kiriting.
3. @BotFather bergan **Bot Token** ni nusxalab saqlang (uni **hech qachon** ilova kodiga yozmang!).
4. Mini App ni ulash uchun @BotFather da `/newapp` buyrug'ini yuboring:
   - Botingizni tanlang (`@muzeycha_uz_bot`).
   - Sarlavha (`Muzeycha`), qisqa tavsif va 640×360 rasm yuklang.
   - Web App URL sifatida `telegram-mini-app/` papkasini joylashtirgan HTTPS manzilingizni kiriting (masalan, `https://muzeycha.uz`).
   - Short name (masalan, `app`) bering — shunda `https://t.me/<bot>/app?startapp=<slug>` va `https://t.me/<bot>?startapp=<slug>` ishlaydi.
5. Chap pastki menyu tugmasi uchun `/setmenubutton` -> botni tanlang -> Mini App URL manzilingizni va `"Muzeycha"` nomini kiriting.

---

## 2. Supabase Bazasi va Edge Functions ni sozlash

### A) SQL jadvallari va Storage
Supabase SQL Editor da ilovadagi **"SQL va sozlash"** oynasidagi SQL kodni ishga tushiring (`museums`, `museum_photos`, `subscriptions`, `wedding_wishes` jadvallari va `museum-photos` nomli public bucket yaratiladi).

### B) Edge Functions Secretlari
Supabase CLI orqali kerakli secretlarni o'rnating:

```bash
supabase secrets set \
  TELEGRAM_BOT_TOKEN="123456:ABC-..." \
  MINI_APP_URL="https://muzeycha.uz"
```

### C) Edge Functionlarni deploy qilish

```bash
supabase functions deploy telegram-auth --no-verify-jwt
supabase functions deploy telegram-bot --no-verify-jwt
supabase functions deploy expiry-reminder --no-verify-jwt
```

### D) Telegram Webhook ni ulash

```bash
curl -X POST "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://<PROJECT_REF>.supabase.co/functions/v1/telegram-bot"}'
```

### E) Obuna tugashiga 3 kun qolganda eslatma (Cron)
Supabase Dashboard -> **Integrations -> Cron** (yoki `pg_cron` + `pg_net`) orqali har kuni `09:00` da `https://<PROJECT_REF>.supabase.co/functions/v1/expiry-reminder` manziliga POST so'rov yuboruvchi vazifa qo'shing.

---

## 3. Telegram Mini App (`telegram-mini-app/`) ni joylashtirish

1. `telegram-mini-app/config.js` faylida `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `BOT_USERNAME` qiymatlarini kiriting.
2. `telegram-mini-app/` papkasini istalgan HTTPS hostingga (Vercel, Cloudflare Pages, Netlify yoki Supabase Storage/Custom Domain) joylang.
3. Ushbu HTTPS URL manzilini @BotFather (`/newapp` / `/setmenubutton`) va `MINI_APP_URL` secretiga kiriting.

---

## 4. Android ilova Secrets sozlamalari

AI Studio **Secrets** paneliga quyidagilarni kiriting:
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `MAPS_API_KEY`
- `TELEGRAM_BOT_USERNAME` (masalan, `muzeycha_uz_bot`)
