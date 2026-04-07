-- ============================================
-- Vibe Tags i18n - ALL Supported Languages
-- ============================================
-- Populate label_i18n column for all active theme vibe tags
--
-- Supported Languages:
-- - pt: Portuguese (Português)
-- - es: Spanish (Español)
-- - ar: Arabic (العربية)
-- - hi: Hindi (हिन्दी)
-- - id: Indonesian (Bahasa Indonesia)
-- - fil: Filipino
-- - tr: Turkish (Türkçe)
--
-- Usage:
-- 1. Open Supabase SQL Editor
-- 2. Copy and paste this entire file
-- 3. Execute
--
-- Note: English (en) is NOT included - display_name column serves as English fallback
-- ============================================

-- Birthday
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Aniversário",
  "es": "Cumpleaños",
  "ar": "عيد ميلاد",
  "hi": "जन्मदिन",
  "id": "Ulang Tahun",
  "fil": "Kaarawan",
  "tr": "Doğum Günü"
}'::jsonb WHERE id = 'birthday';

-- Celebration
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Celebração",
  "es": "Celebración",
  "ar": "احتفال",
  "hi": "उत्सव",
  "id": "Perayaan",
  "fil": "Pagdiriwang",
  "tr": "Kutlama"
}'::jsonb WHERE id = 'celebration';

-- Wedding
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Casamento",
  "es": "Boda",
  "ar": "زفاف",
  "hi": "शादी",
  "id": "Pernikahan",
  "fil": "Kasal",
  "tr": "Düğün"
}'::jsonb WHERE id = 'wedding';

-- Love
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Amor",
  "es": "Amor",
  "ar": "حب",
  "hi": "प्यार",
  "id": "Cinta",
  "fil": "Pag-ibig",
  "tr": "Aşk"
}'::jsonb WHERE id = 'love';

-- Couple
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Casal",
  "es": "Pareja",
  "ar": "زوجان",
  "hi": "जोड़े",
  "id": "Pasangan",
  "fil": "Mag-asawa",
  "tr": "Çift"
}'::jsonb WHERE id = 'couple';

-- Family
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Família",
  "es": "Familia",
  "ar": "عائلة",
  "hi": "परिवार",
  "id": "Keluarga",
  "fil": "Pamilya",
  "tr": "Aile"
}'::jsonb WHERE id = 'family';

-- Party
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Festa",
  "es": "Fiesta",
  "ar": "حفلة",
  "hi": "पार्टी",
  "id": "Pesta",
  "fil": "Salo-salo",
  "tr": "Parti"
}'::jsonb WHERE id = 'party';

-- Memories
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Memórias",
  "es": "Recuerdos",
  "ar": "ذكريات",
  "hi": "यादें",
  "id": "Kenangan",
  "fil": "Alaala",
  "tr": "Anılar"
}'::jsonb WHERE id = 'memories';

-- Selfie
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Selfie",
  "es": "Selfie",
  "ar": "سيلفي",
  "hi": "सेल्फी",
  "id": "Selfie",
  "fil": "Selfie",
  "tr": "Özçekim"
}'::jsonb WHERE id = 'selfie';

-- Fashion
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Moda",
  "es": "Moda",
  "ar": "موضة",
  "hi": "फैशन",
  "id": "Mode",
  "fil": "Moda",
  "tr": "Moda"
}'::jsonb WHERE id = 'fashion';

-- Travel
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Viagem",
  "es": "Viaje",
  "ar": "سفر",
  "hi": "यात्रा",
  "id": "Perjalanan",
  "fil": "Paglalakbay",
  "tr": "Seyahat"
}'::jsonb WHERE id = 'travel';

-- Daily Life
UPDATE vibe_tags SET label_i18n = '{
  "pt": "Vida Diária",
  "es": "Vida Diaria",
  "ar": "الحياة اليومية",
  "hi": "दैनिक जीवन",
  "id": "Kehidupan Sehari-hari",
  "fil": "Araw-araw na Buhay",
  "tr": "Günlük Hayat"
}'::jsonb WHERE id = 'daily_life';
