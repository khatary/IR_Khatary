# IR_Khatary DNS 2.0

یک برنامه‌ی DNS سبک و سازگار با همه‌ی گوشی‌های اندروید (۵.۰ به بالا)، بدون نیاز به روت.
A lightweight DNS app for every Android phone (5.0+), no root required.

## ویژگی‌ها / Features
- اتصال با یک ضربه و نمایش وضعیت، زمان اتصال و آمار درخواست‌ها
- ۱۰ سرور آماده (IR_Khatary، شکن، الکترو، رادار گیم، ۴۰۳، بگذر، Cloudflare، Google، Quad9، AdGuard) + DNS دلخواه (IPv4 / IPv6)
- تست سرعت همه‌ی سرورها و انتخاب خودکار سریع‌ترین
- حافظه‌ی نهان، ارسال هم‌زمان به DNS دوم در صورت کندی، و تکرار خودکار با TCP برای پاسخ‌های بزرگ
- روشن ماندن صفحه (مناسب بازی)، اتصال خودکار بعد از ریستارت، رابط فارسی / انگلیسی
- فقط درخواست‌های DNS از برنامه عبور می‌کنند؛ بقیه‌ی ترافیک مستقیم می‌رود (بدون افت سرعت)

## How it works
The app opens a local `VpnService` tunnel that carries **one virtual DNS address (10.7.0.1)** and routes only that address into the tunnel. Android sends every DNS lookup there; the service forwards it to the selected resolver on a normal socket and writes the answer back. No traffic other than DNS is touched, it works on IPv4-only, IPv6-only and dual-stack networks, and the resolver itself can be IPv4 or IPv6.

## Compatibility
- `minSdk 21` (Android 5.0) → `targetSdk 35` (Android 15)
- Native UI, **no WebView and no AndroidX** — nothing depends on a system component that may be missing or outdated
- Notification channel, notification permission (Android 13+), foreground-service type (Android 14+), edge-to-edge (Android 15) and RTL layouts are all handled
- Adaptive icon on Android 8+, PNG icon below

## Build
Open in Android Studio (Ladybug or newer) and run `app`, or let GitHub Actions build it (`Build Android APK` workflow → artifact `IR-Khatary-DNS-debug-apk`). Add the four `ANDROID_KEYSTORE_*` / `ANDROID_KEY_*` secrets to also get a signed release APK.

## Notes & limits
- If the phone's **Private DNS** is set to a hostname, Android bypasses VPN DNS. Set it to Automatic or Off.
- DNS changes lookup speed only. It does not guarantee lower ping in games and may not bypass blocking.
- DNS over TCP from apps (rare) is not handled by the tunnel; UDP DNS (what Android uses) is.
- Resolver availability differs per ISP/country — use the built-in speed test.
- Resolver addresses are third-party services; verify them before relying on them.

## Other platforms
- Windows: `desktop/windows/IR-Khatary-DNS.ps1` (run as Administrator; changes system DNS, not a VPN)
- iOS: see `platforms/ios/README.md`
