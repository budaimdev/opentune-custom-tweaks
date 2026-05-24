# OpenTune Custom Tweaks

<div align="center">
  <img src="https://github.com/Arturo254/OpenTune/blob/master/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="OpenTune Banner" width="100%"/>
  
  <h3>Pokročilý a přizpůsobitelný přehrávač YouTube Music pro Android</h3>
  
  [![Aktuální verze](https://img.shields.io/badge/Verze-3.0.4--custom-blue?style=flat-square&logo=github&color=0D1117&labelColor=161B22)](https://github.com/budaimdev/opentune-custom-tweaks/releases)
  [![Licence](https://img.shields.io/github/license/Arturo254/OpenTune?style=flat-square&logo=gnu&color=2B3137&labelColor=161B22)](LICENSE)
  [![Android](https://img.shields.io/badge/Platforma-Android%208.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white&labelColor=161B22)](https://www.android.com)
</div>

---

> [!IMPORTANT]
> **Tento projekt je neoficiálním forkem (odštěpením) původního přehrávače OpenTune.**
> 
> Hlavním cílem tohoto forku **OpenTune Custom Tweaks** je přinášet specifické úpravy, optimalizace a vlastní vylepšení přehrávače. 
> - Původní a oficiální repozitář naleznete na adrese: [Arturo254/OpenTune](https://github.com/Arturo254/OpenTune)
> - Původní verze, ze které tento fork vychází: `3.0.2`
> - Aktuální verze tohoto forku: `3.0.4`

---

## 🚀 O projektu a úpravách

Tento fork staví na základech OpenTune – pokročilého přehrávače hudby z YouTube Music s čistým Material Design 3 rozhraním, integrovanými texty písní z LRC Lib a BetterLyrics, a širokou paletou audio funkcí.

Tento fork přináší:
- **Vlastní balíčky a instalace** s upraveným aplikačním ID (`cz.budaimdev.opentune`).
- **Optimalizovanou CI/CD pipeline** generující balíčky pro různé hardwarové architektury i zmenšené instalace.
- Lokální ladění a přizpůsobení uživatelského rozhraní.

---

## 📦 Balíčky a zmenšená instalace (CI/CD)

V rámci našich automatických sestavení (GitHub Actions) generujeme dva hlavní typy balíčků pro usnadnění instalace a úsporu místa:

1. **Android App Bundle (.aab)** – Hlavní distribuční formát pro Google Play a kompatibilní instalátory, který automaticky doručuje optimalizovanou a zmenšenou verzi přímo pro vaše zařízení.
2. **Architektury specifické APK (.apk)** – Namísto stahování jednoho obřího univerzálního APK souboru si můžete stáhnout zmenšené sestavení přesně pro procesor vašeho zařízení:
   - **arm64-v8a**: Pro moderní 64bitové telefony (nejčastější volba).
   - **armeabi-v7a**: Pro starší 32bitové telefony.
   - **x86 / x86_64**: Pro emulátory a specifická zařízení.
   - **universal**: Kompletní balíček obsahující knihovny pro všechny procesory.

---

## 🛠️ Kompilace ze zdrojového kódu

Chcete-li si projekt sestavit sami, ujistěte se, že máte nainstalované JDK 21 (nebo alespoň JDK 17).

### Příprava prostředí
Nadefinujte spustitelná práva pro Gradle wrapper:
```bash
chmod +x gradlew
```

### Sestavení debug verze (všechny architektury a AAB)
```bash
./gradlew assembleDebug bundleDebug
```
Výstupní soubory naleznete v:
- APK: `app/build/outputs/apk/`
- AAB: `app/build/outputs/bundle/`

### Sestavení release verze (vyžaduje podepsání)
```bash
./gradlew clean assembleRelease bundleRelease
```

---

## 📄 Licence a poděkování

Projekt je šířen pod licencí **GNU General Public License v3.0**. Podrobnosti naleznete v souboru [LICENSE](LICENSE).

Děkujeme autorům původního projektu **OpenTune** a všem přispěvatelům do projektu **InnerTune**, na němž celá aplikace staví.
