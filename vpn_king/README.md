# 👑 VPN KING

کلاینت VPN تونل‌زنی برای اندروید (هسته‌ی Xray) با اتصال واقعی، تست پینگ و طراحی تیره/طلایی.

## بیلد با GitHub Actions
1. این پوشه را در یک ریپوی GitHub آپلود کن (branch: `main`).
2. تب **Actions** → `Build VPN KING APK` → اجرا می‌شود (یا Run workflow).
3. بعد از ~۵ دقیقه، APK را از **Artifacts** دانلود کن (`arm64-v8a` برای اکثر گوشی‌ها).
4. برای ریلیز: `git tag v1.0.0 && git push --tags`

## فرمت‌های ورودی
- لینک‌ها: `vmess://` `vless://` `trojan://` `ss://` `socks://`
- لینک سابسکریپشن (http/https) و base64
- JSON کامل Xray/V2Ray
- از **کلیپ‌بورد**، **فایل** یا **چسباندن دستی**

> فایل‌های قفل‌دار و رمزنگاری‌شده (.ehi, .sip, .sksplus, .tls, .lnk) فقط با اپ اصلی خودشان باز می‌شوند و VPN KING نمی‌تواند آن‌ها را بخواند.
