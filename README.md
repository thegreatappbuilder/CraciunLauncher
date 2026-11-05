# 🎄 Teme de Crăciun – launcher Android

Launcher complet de Crăciun pentru orice telefon Android 7.0+ (Samsung, Xiaomi/Redmi/POCO, Huawei/Honor,
Oppo, Realme, OnePlus, Motorola, Pixel, tablete și pliabile). Toată grafica e desenată în cod,
deci nu există imagini cu drepturi de autor și aplicația are doar câteva sute de KB.

## Ce conține

**5 variante de temă**
| Temă | Atmosferă |
|---|---|
| Noapte Sfântă | lună plină, sat, brad împodobit, om de zăpadă |
| Pădurea de Argint | aurora boreală animată peste o pădure înzăpezită |
| Lumini de Aur | elegant: aur, șampanie, lumini bokeh care plutesc |
| Satul de Poveste | amurg roz-violet, case luminate, biserica satului, fum din hornuri |
| Sania lui Moș Crăciun | lună uriașă, sania cu reni (și Rudolf cu nasul roșu) trece des |

**Efecte de sărbători:** ninsoare pe 3 planuri cu vânt și cristale de zăpadă, ghirlandă de becuri care se
aprind pe rând, luminițe care pâlpâie în brad și pe case, stea strălucitoare în vârf, stele căzătoare,
sclipiri pe zăpadă, scântei aurii la atingere, fum din hornuri.

**Launcher:** ceas, dată în română, numărătoare inversă până la Crăciun, aplicații favorite, bară de jos,
sertar cu toate aplicațiile (glisare în sus) și căutare fără diacritice, meniu la apăsare lungă
(adaugă/scoate, informații, dezinstalare).

**Pictograme festive (5 stiluri):** globuleț, cadou, coroniță, căciulă de zăpadă, crenguță de ilex –
aplicate automat peste pictograma fiecărei aplicații.

**Pentru cine nu vrea alt launcher:** fundal animat (merge peste One UI, MIUI/HyperOS, EMUI etc.) și
fundal static pentru ecranul principal și ecranul de blocare.

**Se adaptează la orice ecran:** 3–8 coloane în funcție de lățime, pictograme mai mari pe tablete,
afișare sub decupajul camerei, portret și peisaj.

## Cum îl rulezi

1. Instalează [Android Studio](https://developer.android.com/studio) (gratuit).
2. *File → Open* și alege folderul `CraciunLauncher`. Așteaptă sincronizarea Gradle (prima dată descarcă uneltele).
3. Conectează telefonul (cu *Depanare USB* activată) sau pornește un emulator și apasă ▶ *Run*.
4. Apasă butonul Acasă și alege **Teme de Crăciun** → *Întotdeauna*. Sau din aplicație: *🎄 Teme → Folosește ca ecran principal*.

## Cum faci APK-ul

*Build → Build App Bundle(s) / APK(s) → Build APK(s)*. Fișierul apare în `app/build/outputs/apk/debug/`.
Pentru Google Play: *Build → Generate Signed App Bundle*, apoi încarci `.aab`-ul în Play Console.
Verifică în Play Console cerința actuală de `targetSdk` și, dacă e mai mare de 35, crește `compileSdk`/`targetSdk`
în `app/build.gradle.kts` (Android Studio îți propune și actualizarea pluginului Gradle).

## Structura codului

```
app/src/main/java/ro/craciun/launcher/
├── MainActivity.kt            ecranul principal (launcher)
├── SettingsActivity.kt        alegerea temei, pictogramelor, efectelor
├── theme/ChristmasTheme.kt    cele 5 teme (culori, elemente de scenă)
├── scene/ChristmasRenderer.kt motorul grafic: scena + toate efectele
├── scene/ChristmasSceneView.kt bucla de animație
├── apps/                      citirea aplicațiilor + pictogramele festive
├── wallpaper/                 fundalul animat pentru orice launcher
└── ui/, util/                 grilă adaptivă, setări, dată și numărătoare
```

**Adaugi o temă nouă:** copiezi un bloc din `ThemeCatalog` (în `ChristmasTheme.kt`), schimbi culorile și
numărul de brazi/case, apoi o adaugi în lista `ALL`. Apare automat în setări.
