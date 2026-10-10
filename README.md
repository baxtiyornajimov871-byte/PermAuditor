# BAXTIYOR AUDIT

Android ilova: telefondagi ilovalarning ruxsatlarini tekshiradi va har biriga **xavf bali** (0-100) beradi.
Interfeys uch tilda: o'zbek, ingliz, rus.

## Imkoniyatlar
- Ilovalar ro'yxati: ikonka, nom, xavf bali, rangli chiziq, qidiruv va filtr
- Tahlil oynasi: berilgan va so'ralgan ruxsatlar, fonda ishlash imkoniyati, batareya, ilova turi, xulosa
- Telefon xavfsizligi: ekran qulfi, yangilanish, dasturchi rejimi, USB orqali sozlash, root belgilari, xotira shifrlanishi, maxsus xizmatlar
- Yordamchi (chat): tayyor qoidalar va tekshiruv natijalariga asoslangan javoblar
- Ilovaga kirish paroli (PBKDF2 xesh, tuz, xato urinishlarda kutish) va parol kuchi tekshirgichi
- Hisobotni ulashish, maxfiylik va baholash sahifasi, til tanlash
- Ilova taymeri: kunlik vaqt limiti, limit tugagach bosh ekranga chiqarish yoki parol so'rash

## Cheklovlar
- Ilova virus qidirmaydi, faqat ruxsat va imkoniyatlarni baholaydi.
- Yuqori ball ilova zararli ekanini isbotlamaydi, past ball kafolat emas.
- Android boshqa ilova hozir ishlayotganini ko'rsatmaydi.

## APK yig'ish
Kodni GitHub'ga yuklang (`git push`). GitHub Actions avtomatik yig'adi: **Actions** bo'limi, so'nggi yozuv, **Artifacts**, `PermAuditor-APK`.

Texnologiyalar: Kotlin, Gradle 8.7, Android SDK 34 (minSdk 24).
