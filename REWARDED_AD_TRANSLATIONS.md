# Rewarded Ad Translations

## Strings to Add to Each Language File

### values/strings.xml (English) ✅ DONE
```xml
<string name="export_watch_ad_title">Watch an ad to download?</string>
<string name="export_watch_ad_subtitle">Help us keep this app free by watching a short ad. Your video will download right after!</string>
<string name="export_watch_ad_button">Watch Ad</string>
<string name="export_watch_ad_close">Close</string>
```

### values-ar/strings.xml (Arabic)
```xml
<string name="export_watch_ad_title">شاهد إعلان للتنزيل؟</string>
<string name="export_watch_ad_subtitle">ساعدنا في الحفاظ على هذا التطبيق مجانيًا بمشاهدة إعلان قصير. سيتم تنزيل الفيديو الخاص بك مباشرة بعد ذلك!</string>
<string name="export_watch_ad_button">شاهد الإعلان</string>
<string name="export_watch_ad_close">إغلاق</string>
```

### values-es/strings.xml (Spanish)
```xml
<string name="export_watch_ad_title">¿Ver un anuncio para descargar?</string>
<string name="export_watch_ad_subtitle">Ayúdanos a mantener esta aplicación gratis viendo un breve anuncio. ¡Tu video se descargará justo después!</string>
<string name="export_watch_ad_button">Ver Anuncio</string>
<string name="export_watch_ad_close">Cerrar</string>
```

### values-fil/strings.xml (Filipino/Tagalog)
```xml
<string name="export_watch_ad_title">Manood ng ad para mag-download?</string>
<string name="export_watch_ad_subtitle">Tulungan mo kaming panatilihing libre ang app na ito sa pamamagitan ng panonood ng maikling ad. Mag-download ang iyong video pagkatapos!</string>
<string name="export_watch_ad_button">Manood ng Ad</string>
<string name="export_watch_ad_close">Isara</string>
```

### values-hi/strings.xml (Hindi)
```xml
<string name="export_watch_ad_title">डाउनलोड करने के लिए एक विज्ञापन देखें?</string>
<string name="export_watch_ad_subtitle">एक छोटा विज्ञापन देखकर इस ऐप को मुफ़्त रखने में हमारी मदद करें। आपका वीडियो तुरंत बाद डाउनलोड हो जाएगा!</string>
<string name="export_watch_ad_button">विज्ञापन देखें</string>
<string name="export_watch_ad_close">बंद करें</string>
```

### values-id/strings.xml (Indonesian)
```xml
<string name="export_watch_ad_title">Tonton iklan untuk mengunduh?</string>
<string name="export_watch_ad_subtitle">Bantu kami menjaga aplikasi ini gratis dengan menonton iklan singkat. Video Anda akan diunduh segera setelahnya!</string>
<string name="export_watch_ad_button">Tonton Iklan</string>
<string name="export_watch_ad_close">Tutup</string>
```

### values-pt/strings.xml (Portuguese)
```xml
<string name="export_watch_ad_title">Assistir a um anúncio para baixar?</string>
<string name="export_watch_ad_subtitle">Ajude-nos a manter este aplicativo gratuito assistindo a um breve anúncio. Seu vídeo será baixado logo em seguida!</string>
<string name="export_watch_ad_button">Assistir Anúncio</string>
<string name="export_watch_ad_close">Fechar</string>
```

### values-tr/strings.xml (Turkish)
```xml
<string name="export_watch_ad_title">İndirmek için reklam izle?</string>
<string name="export_watch_ad_subtitle">Kısa bir reklam izleyerek bu uygulamayı ücretsiz tutmamıza yardımcı olun. Videonuz hemen ardından indirilecek!</string>
<string name="export_watch_ad_button">Reklam İzle</string>
<string name="export_watch_ad_close">Kapat</string>
```

## How to Apply

For each language folder, add these 4 strings after the existing export strings (around line 233).

Example for values-es/strings.xml:
1. Open `app/src/main/res/values-es/strings.xml`
2. Find the export section (search for "export_templates_load_failed")
3. Add the 4 strings above after that line
4. Save the file

Repeat for all language folders.
