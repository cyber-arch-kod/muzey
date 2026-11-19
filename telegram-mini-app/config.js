export const CONFIG = {
  // Supabase loyiha ma'lumotlarini shu yerga kiriting
  SUPABASE_URL: "https://YOUR_PROJECT_REF.supabase.co",
  SUPABASE_ANON_KEY: "YOUR_SUPABASE_ANON_KEY",
  BOT_USERNAME: "muzeycha_uz_bot",

  // Tarif narxlari va limitlari (oson o'zgartirish uchun)
  PRICING: {
    FREE_MAX_MUSEUMS: 1,
    FREE_MAX_PHOTOS: 5,
    PAID_MAX_PHOTOS: 20,
    SIX_MONTHS_UZS: 30000,
    ONE_YEAR_UZS: 100000,
    SIX_MONTHS_DAYS: 180,
    ONE_YEAR_DAYS: 365,
  },

  // 3D viewer fon musiqasi manzillari (Audio API uchun dinamik yuklanadi)
  MUSIC: {
    DEFAULT_URL: "./audio/museum_bg.mp3",
    TRACKS_BY_STYLE: {
      ROMANTIC: "./audio/romantic.mp3",
      WEDDING: "./audio/wedding.mp3",
      CLASSIC_WHITE: "./audio/museum_bg.mp3",
      WARM_WOOD: "./audio/museum_bg.mp3",
      MODERN_DARK: "./audio/museum_bg.mp3",
    },
    TARGET_VOLUME: 0.55,
    FADE_SECONDS: 0.8,
  },
};
