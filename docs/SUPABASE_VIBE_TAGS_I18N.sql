-- ============================================
-- Vibe Tags i18n - Hindi Translations
-- ============================================
-- Populate label_i18n column for all active theme vibe tags
--
-- Usage:
-- 1. Open Supabase SQL Editor
-- 2. Copy and paste this entire file
-- 3. Execute
--
-- Note: label_i18n contains ONLY Hindi - English fallback is from display_name column
-- ============================================

UPDATE vibe_tags SET label_i18n = '{"hi":"जन्मदिन"}'::jsonb WHERE id = 'birthday';

UPDATE vibe_tags SET label_i18n = '{"hi":"उत्सव"}'::jsonb WHERE id = 'celebration';

UPDATE vibe_tags SET label_i18n = '{"hi":"शादी"}'::jsonb WHERE id = 'wedding';

UPDATE vibe_tags SET label_i18n = '{"hi":"प्यार"}'::jsonb WHERE id = 'love';

UPDATE vibe_tags SET label_i18n = '{"hi":"जोड़े"}'::jsonb WHERE id = 'couple';

UPDATE vibe_tags SET label_i18n = '{"hi":"परिवार"}'::jsonb WHERE id = 'family';

UPDATE vibe_tags SET label_i18n = '{"hi":"पार्टी"}'::jsonb WHERE id = 'party';

UPDATE vibe_tags SET label_i18n = '{"hi":"यादें"}'::jsonb WHERE id = 'memories';

UPDATE vibe_tags SET label_i18n = '{"hi":"सेल्फी"}'::jsonb WHERE id = 'selfie';

UPDATE vibe_tags SET label_i18n = '{"hi":"फैशन"}'::jsonb WHERE id = 'fashion';

UPDATE vibe_tags SET label_i18n = '{"hi":"यात्रा"}'::jsonb WHERE id = 'travel';

UPDATE vibe_tags SET label_i18n = '{"hi":"दैनिक जीवन"}'::jsonb WHERE id = 'daily_life';
