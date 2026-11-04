import 'dart:convert';
import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_state.dart';
import 'server.dart';

const gold = Color(0xFFFFC83D);
const goldDeep = Color(0xFFFF9A1F);
const bg = Color(0xFF0A0B14);
const card = Color(0xFF151728);
const green = Color(0xFF2EE59D);

final app = AppState();

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(SystemUiOverlayStyle.light);
  runApp(const VpnKingApp());
  app.init();
}

class VpnKingApp extends StatelessWidget {
  const VpnKingApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'VPN KING',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          scaffoldBackgroundColor: bg,
          colorScheme: const ColorScheme.dark(primary: gold, surface: card),
          useMaterial3: true,
        ),
        builder: (c, w) => Directionality(
            textDirection: TextDirection.rtl, child: w ?? const SizedBox()),
        home: const Shell(),
      );
}

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: app,
      builder: (context, _) => Scaffold(
        body: SafeArea(
          child: IndexedStack(
              index: tab, children: const [HomePage(), ServersPage()]),
        ),
        bottomNavigationBar: NavigationBar(
          backgroundColor: card,
          indicatorColor: gold.withOpacity(.2),
          selectedIndex: tab,
          onDestinationSelected: (i) => setState(() => tab = i),
          destinations: const [
            NavigationDestination(
                icon: Icon(Icons.shield_outlined),
                selectedIcon: Icon(Icons.shield, color: gold),
                label: 'اتصال'),
            NavigationDestination(
                icon: Icon(Icons.dns_outlined),
                selectedIcon: Icon(Icons.dns, color: gold),
                label: 'سرورها'),
          ],
        ),
      ),
    );
  }
}

String fmtBytes(num b) {
  if (b < 1024) return '${b.toStringAsFixed(0)} B';
  if (b < 1048576) return '${(b / 1024).toStringAsFixed(1)} KB';
  if (b < 1073741824) return '${(b / 1048576).toStringAsFixed(1)} MB';
  return '${(b / 1073741824).toStringAsFixed(2)} GB';
}

// ───────────────────────── صفحه اتصال ─────────────────────────
class HomePage extends StatelessWidget {
  const HomePage({super.key});
  @override
  Widget build(BuildContext context) {
    final on = app.connected;
    final busy = app.connecting;
    final color = on ? green : (busy ? goldDeep : gold);
    final s = app.selected;
    return Padding(
      padding: const EdgeInsets.all(20),
      child: Column(children: [
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          const Icon(Icons.workspace_premium, color: gold, size: 30),
          const SizedBox(width: 8),
          ShaderMask(
            shaderCallback: (r) =>
                const LinearGradient(colors: [gold, goldDeep]).createShader(r),
            child: const Text('VPN KING',
                style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 3,
                    color: Colors.white)),
          ),
        ]),
        const Spacer(),
        GestureDetector(
          onTap: app.toggle,
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 400),
            width: 190,
            height: 190,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                  colors: [color.withOpacity(.35), card], radius: .9),
              border: Border.all(color: color, width: 3),
              boxShadow: [
                BoxShadow(
                    color: color.withOpacity(on ? .55 : .25),
                    blurRadius: on ? 60 : 30,
                    spreadRadius: on ? 8 : 2)
              ],
            ),
            child: busy
                ? Center(child: CircularProgressIndicator(color: color))
                : Icon(Icons.power_settings_new, size: 86, color: color),
          ),
        ),
        const SizedBox(height: 28),
        Text(
          on ? 'متصل شد' : (busy ? 'در حال اتصال...' : 'قطع است'),
          style: TextStyle(
              fontSize: 22, fontWeight: FontWeight.bold, color: color),
        ),
        const SizedBox(height: 6),
        Text(on ? app.status.duration : 'برای اتصال لمس کن',
            style: const TextStyle(color: Colors.white54, fontSize: 16)),
        if (app.error != null)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text(app.error!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.redAccent)),
          ),
        const Spacer(),
        if (on)
          Row(children: [
            _stat(Icons.arrow_downward, 'دانلود',
                '${fmtBytes(app.status.downloadSpeed)}/s', fmtBytes(app.status.download)),
            const SizedBox(width: 12),
            _stat(Icons.arrow_upward, 'آپلود',
                '${fmtBytes(app.status.uploadSpeed)}/s', fmtBytes(app.status.upload)),
          ]),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: card, borderRadius: BorderRadius.circular(18)),
          child: Row(children: [
            const CircleAvatar(
                backgroundColor: Color(0x33FFC83D),
                child: Icon(Icons.public, color: gold)),
            const SizedBox(width: 12),
            Expanded(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                  Text(s?.name ?? 'سروری انتخاب نشده',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 16)),
                  Text(s == null ? 'از تب سرورها اضافه کن' : s.protocol.toUpperCase(),
                      style: const TextStyle(color: Colors.white54)),
                ])),
          ]),
        ),
      ]),
    );
  }

  Widget _stat(IconData i, String t, String speed, String total) => Expanded(
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: card, borderRadius: BorderRadius.circular(18)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(i, size: 16, color: green),
              const SizedBox(width: 4),
              Text(t, style: const TextStyle(color: Colors.white54)),
            ]),
            const SizedBox(height: 6),
            Text(speed,
                style:
                    const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            Text('مجموع: $total',
                style: const TextStyle(color: Colors.white38, fontSize: 12)),
          ]),
        ),
      );
}

// ───────────────────────── صفحه سرورها ─────────────────────────
class ServersPage extends StatelessWidget {
  const ServersPage({super.key});

  void _toast(BuildContext c, String m) => ScaffoldMessenger.of(c)
      .showSnackBar(SnackBar(content: Text(m), behavior: SnackBarBehavior.floating));

  void _report(BuildContext c, ImportResult r) {
    final n = app.addServers(r.servers);
    var msg = '$n سرور اضافه شد';
    if (r.unsupported > 0) msg += ' • ${r.unsupported} مورد پشتیبانی نمی‌شود';
    if (r.servers.isEmpty && r.message != null) msg = r.message!;
    if (r.servers.isEmpty && r.message == null) {
      msg = 'سرور معتبری پیدا نشد';
    }
    _toast(c, msg);
  }

  Future<void> _clipboard(BuildContext c) async {
    final d = await Clipboard.getData('text/plain');
    final t = d?.text ?? '';
    if (!c.mounted) return;
    if (t.trim().startsWith('http')) {
      _toast(c, 'در حال دریافت سابسکریپشن...');
      final r = await app.importFromUrl(t.trim());
      if (c.mounted) _report(c, r);
    } else {
      _report(c, parseServers(t));
    }
  }

  Future<void> _file(BuildContext c) async {
    final res = await FilePicker.platform.pickFiles(withData: true);
    if (res == null || res.files.isEmpty || !c.mounted) return;
    final all = <Server>[];
    var uns = 0;
    var binary = 0;
    for (final f in res.files) {
      final bytes = f.bytes ?? (f.path != null ? await File(f.path!).readAsBytes() : null);
      if (bytes == null) continue;
      final r = parseServers(utf8.decode(bytes, allowMalformed: true));
      if (r.servers.isEmpty) binary++;
      all.addAll(r.servers);
      uns += r.unsupported;
    }
    final total = ImportResult(all, unsupported: uns);
    if (!c.mounted) return;
    if (all.isEmpty && binary > 0) {
      showDialog(
        context: c,
        builder: (_) => AlertDialog(
          backgroundColor: card,
          title: const Text('فایل قفل‌دار است'),
          content: const Text(
              'این فایل رمزنگاری‌شده است (مثل .ehi / .sip / .sksplus / .tls) و فقط توسط اپ اصلی خودش باز می‌شود.\n\n'
              'فرمت‌های قابل‌قبول: لینک vmess / vless / trojan / ss / socks، فایل متنی یا JSON حاوی آن‌ها، و لینک سابسکریپشن.'),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(c), child: const Text('باشه'))
          ],
        ),
      );
      return;
    }
    _report(c, total);
  }

  void _text(BuildContext c) {
    final ctl = TextEditingController();
    showDialog(
      context: c,
      builder: (_) => AlertDialog(
        backgroundColor: card,
        title: const Text('چسباندن لینک / کانفیگ'),
        content: TextField(
          controller: ctl,
          maxLines: 8,
          minLines: 4,
          decoration: const InputDecoration(
              hintText: 'vmess://...  vless://...  لینک سابسکریپشن  یا JSON',
              border: OutlineInputBorder()),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(c), child: const Text('لغو')),
          FilledButton(
            onPressed: () async {
              final t = ctl.text.trim();
              Navigator.pop(c);
              if (t.startsWith('http')) {
                final r = await app.importFromUrl(t);
                if (c.mounted) _report(c, r);
              } else {
                _report(c, parseServers(t));
              }
            },
            child: const Text('افزودن'),
          ),
        ],
      ),
    );
  }

  void _addSheet(BuildContext c) {
    showModalBottomSheet(
      context: c,
      backgroundColor: card,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (_) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const SizedBox(height: 12),
          const Text('افزودن سرور',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          ListTile(
              leading: const Icon(Icons.content_paste, color: gold),
              title: const Text('وارد کردن از کلیپ‌بورد'),
              subtitle: const Text('لینک سرور یا سابسکریپشن'),
              onTap: () {
                Navigator.pop(c);
                _clipboard(c);
              }),
          ListTile(
              leading: const Icon(Icons.folder_open, color: gold),
              title: const Text('وارد کردن از فایل'),
              subtitle: const Text('txt / json / فایل حاوی لینک'),
              onTap: () {
                Navigator.pop(c);
                _file(c);
              }),
          ListTile(
              leading: const Icon(Icons.edit_note, color: gold),
              title: const Text('چسباندن دستی'),
              onTap: () {
                Navigator.pop(c);
                _text(c);
              }),
          const SizedBox(height: 8),
        ]),
      ),
    );
  }

  Color _pingColor(int p) =>
      p < 0 ? Colors.redAccent : (p < 300 ? green : (p < 800 ? gold : goldDeep));

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: gold,
        foregroundColor: Colors.black,
        onPressed: () => _addSheet(context),
        icon: const Icon(Icons.add),
        label: const Text('افزودن'),
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 12, 8),
          child: Row(children: [
            const Text('سرورها',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
            const SizedBox(width: 8),
            Text('(${app.servers.length})',
                style: const TextStyle(color: Colors.white54)),
            const Spacer(),
            IconButton(
                tooltip: 'تست پینگ همه',
                onPressed: app.servers.isEmpty ? null : app.pingAll,
                icon: const Icon(Icons.speed, color: gold)),
            IconButton(
                tooltip: 'حذف همه',
                onPressed: app.servers.isEmpty ? null : app.clearAll,
                icon: const Icon(Icons.delete_sweep, color: Colors.white54)),
          ]),
        ),
        Expanded(
          child: app.servers.isEmpty
              ? const Center(
                  child: Text('هنوز سروری اضافه نکردی\nدکمه «افزودن» رو بزن',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white54, height: 1.8)))
              : ListView.builder(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 90),
                  itemCount: app.servers.length,
                  itemBuilder: (_, i) {
                    final s = app.servers[i];
                    final sel = s.id == app.selectedId;
                    return Dismissible(
                      key: ValueKey(s.id),
                      onDismissed: (_) => app.remove(s.id),
                      background: Container(color: Colors.red.withOpacity(.4)),
                      child: Card(
                        color: card,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                            side: BorderSide(
                                color: sel ? gold : Colors.transparent,
                                width: 1.5)),
                        child: ListTile(
                          onTap: () => app.select(s.id),
                          leading: Icon(
                              sel ? Icons.check_circle : Icons.circle_outlined,
                              color: sel ? gold : Colors.white30),
                          title: Text(s.name,
                              maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(s.protocol.toUpperCase(),
                              style: const TextStyle(color: Colors.white54)),
                          trailing: GestureDetector(
                            onTap: () => app.pingOne(s),
                            child: s.ping == -3
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2))
                                : Text(
                                    s.ping == -2
                                        ? 'تست'
                                        : (s.ping == -1 ? 'ناموفق' : '${s.ping} ms'),
                                    style: TextStyle(
                                        fontWeight: FontWeight.bold,
                                        color: s.ping == -2
                                            ? Colors.white54
                                            : _pingColor(s.ping))),
                          ),
                        ),
                      ),
                    );
                  }),
        ),
      ]),
    );
  }
}
