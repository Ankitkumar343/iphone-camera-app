iPhone 17 Pro Camera - Android starter project

यह Android Studio project है, तैयार APK नहीं।

APK बनाने के लिए:
1. Android Studio (एक कंप्यूटर पर) install करें।
2. इस ZIP को extract करें और Android Studio में folder खोलें।
3. Gradle sync पूरा होने दें (internet जरूरी है ताकि AndroidX CameraX dependencies डाउनलोड हों)।
4. Build > Build Bundle(s) / APK(s) > Build APK(s) चुनें।
5. APK app/build/outputs/apk/debug/app-debug.apk में मिलेगा।

इस starter version में:
- Native Android CameraX preview
- Front/back camera switch
- Photo capture और Gallery में save
- Camera permission prompt
- Permission help dialog जो Android App Settings खोलता है
- Basic look selector (UI selection only)

महत्वपूर्ण सीमाएँ:
- यह ZIP source project है, compiled/signed APK नहीं।
- True 4K capture, computational Night Mode, Portrait depth blur और AI face enhancement अभी शामिल नहीं हैं।
  इनका implementation फोन के camera hardware, depth support और ML model/integration पर निर्भर है।
- किसी भी वेबसाइट/app को user द्वारा block की गई permission चुपके से override करने की अनुमति Android नहीं देता।
