# Keystone Launcher (Aşama 1-3 + 5 + 6)

Samsung SM-B310E tarzı launcher. Ana ekran, 3x3 menü, uygulama listesi, D-pad / soft key / dokunmatik desteği.

## APK alma (GitHub Actions)
1. github.com'da ücretsiz hesap aç, yeni bir **boş depo** (repository) oluştur.
2. Bu klasörün İÇİNDEKİ her şeyi (`.github`, `app`, `build.gradle.kts`, ...) depoya yükle
   (Add file > Upload files; klasörleri sürükle-bırak yapabilirsin).
3. Depoda **Actions** sekmesi > son çalıştırma > sayfanın altındaki **KeystoneLauncher-APK** artifact'ını indir (zip içinde .apk).
4. APK'yı telefona at, kur. Ayarlar > Uygulamalar > Varsayılan uygulamalar > Ana ekran uygulaması: **Keystone Launcher**.

## Tuş testi
`KeystoneView.kt` içindeki `SHOW_KEYCODES = true` iken üst çubukta son basılan tuşun kodu (K:xx) görünür.
Sol/sağ yazılım tuşlarına, OK'e ve Call/End tuşlarına basıp kodları not et.
