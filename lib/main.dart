import 'dart:convert';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_vless/flutter_vless.dart';
import 'package:flutter_wireguard/flutter_wireguard.dart' as wg;
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const gold = Color(0xFFD6AE52);
const bg = Color(0xFF090A0D);
const panel = Color(0xFF15171D);

void _validateWireGuardConfig(String source) {
  final sections = <String, List<Map<String, String>>>{};
  String? current;
  for (final original in source.replaceAll('\r', '').split('\n')) {
    final line = original.trim();
    if (line.isEmpty || line.startsWith('#') || line.startsWith(';')) continue;
    if (line.startsWith('[') && line.endsWith(']')) {
      current = line.substring(1, line.length - 1).trim().toLowerCase();
      sections.putIfAbsent(current, () => <Map<String, String>>[]);
      if (current == 'peer') sections[current]!.add(<String, String>{});
      else if (sections[current]!.isEmpty) sections[current]!.add(<String, String>{});
      continue;
    }
    if (current == null || !line.contains('=')) continue;
    final split = line.indexOf('=');
    final key = line.substring(0, split).trim().toLowerCase();
    final value = line.substring(split + 1).trim();
    if (sections[current]!.isEmpty) sections[current]!.add(<String, String>{});
    sections[current]!.last[key] = value;
  }
  final interfaces = sections['interface'];
  final peers = sections['peer'] ?? const <Map<String, String>>[];
  if (interfaces == null || interfaces.isEmpty) throw const FormatException('بخش [Interface] پیدا نشد.');
  final iface = interfaces.first;
  if (!_isWireGuardKey(iface['privatekey'] ?? '')) throw const FormatException('PrivateKey معتبر نیست.');
  if ((iface['address'] ?? '').isEmpty) throw const FormatException('Address در [Interface] وجود ندارد.');
  if (peers.isEmpty) throw const FormatException('حداقل یک بخش [Peer] لازم است.');
  var hasEndpoint = false;
  for (final peer in peers) {
    if (!_isWireGuardKey(peer['publickey'] ?? '')) throw const FormatException('PublicKey یکی از Peerها معتبر نیست.');
    if ((peer['allowedips'] ?? '').isEmpty) throw const FormatException('AllowedIPs در Peer وجود ندارد.');
    if ((peer['endpoint'] ?? '').isNotEmpty) hasEndpoint = true;
  }
  if (!hasEndpoint) throw const FormatException('Endpoint در هیچ Peer تعریف نشده است.');
}

bool _isWireGuardKey(String value) {
  try { return base64.decode(base64.normalize(value.trim())).length == 32; }
  catch (_) { return false; }
}

bool _looksLikeBase64Subscription(String value) {
  final compact = value.replaceAll(RegExp(r'\s'), '').replaceAll('-', '+').replaceAll('_', '/');
  return compact.length >= 16 && !compact.contains('://') &&
      RegExp(r'^[A-Za-z0-9+/]*={0,2}$').hasMatch(compact) &&
      compact.length % 4 != 1;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const IRKhataryApp());
}

class IRKhataryApp extends StatelessWidget {
  const IRKhataryApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'IR_Khatary VPN',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: bg,
        colorScheme: const ColorScheme.dark(
          primary: gold, secondary: gold, surface: panel,
        ),
        fontFamily: 'sans-serif',
        appBarTheme: const AppBarTheme(
          backgroundColor: bg, foregroundColor: Colors.white,
          centerTitle: false, elevation: 0,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true, fillColor: panel,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: BorderSide.none,
          ),
        ),
      ),
      home: const Directionality(
        textDirection: TextDirection.rtl,
        child: HomePage(),
      ),
    );
  }
}

class VpnProfile {
  VpnProfile({required this.name, required this.config, required this.protocol});
  final String name;
  final String config;
  final String protocol;
  Map<String, dynamic> toJson() => {'name': name, 'config': config, 'protocol': protocol};
  factory VpnProfile.fromJson(Map<String, dynamic> json) => VpnProfile(
    name: json['name'] as String,
    config: json['config'] as String,
    protocol: (json['protocol'] ?? 'Xray') as String,
  );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<VpnProfile> _profiles = [];
  VlessStatus _status = VlessStatus();
  late final FlutterVless _engine = FlutterVless(onStatusChanged: (status) {
    if (!mounted) return;
    setState(() => _status = status);
  });
  int _selected = -1;
  bool _ready = false;
  bool _busy = false;
  String? _engineError;
  int? _delay;
  String _core = 'Xray';
  bool _wireGuardConnected = false;
  bool get _connected => _wireGuardConnected || _status.connectionState == VlessConnectionState.connected;
  bool get _connecting => _status.connectionState == VlessConnectionState.connecting ||
      _status.connectionState == VlessConnectionState.disconnecting || _busy;

  @override
  void initState() {
    super.initState();
    _loadProfiles();
    _initializeEngine();
  }

  Future<void> _initializeEngine() async {
    try {
      await _engine.initializeVless(
        providerBundleIdentifier: 'com.irkhatary.ir_khatary_vpn',
        groupIdentifier: 'group.com.irkhatary.ir_khatary_vpn',
      );
      final version = await _engine.getCoreVersion();
      if (!mounted) return;
      setState(() { _ready = true; _core = version; });
    } catch (e) {
      if (!mounted) return;
      setState(() { _engineError = e.toString(); });
    }
  }

  Future<void> _loadProfiles() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('profiles_v1');
    if (raw == null) return;
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      if (!mounted) return;
      setState(() {
        _profiles.addAll(decoded.map((e) => VpnProfile.fromJson(Map<String, dynamic>.from(e as Map))));
        final selected = prefs.getInt('selected_profile') ?? -1;
        _selected = selected >= 0 && selected < _profiles.length ? selected : (_profiles.isEmpty ? -1 : 0);
      });
    } catch (_) {}
  }

  Future<void> _saveProfiles() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('profiles_v1', jsonEncode(_profiles.map((e) => e.toJson()).toList()));
    await prefs.setInt('selected_profile', _selected);
  }

  String _protocol(String input) {
    final lower = input.toLowerCase();
    for (final p in ['vless://', 'vmess://', 'trojan://', 'ss://']) {
      if (lower.startsWith(p)) return 'V2RAY';
    }
    return 'V2RAY';
  }

  bool _containsUnsupportedProxyScheme(String input) {
    final schemes = RegExp(r'(?i)([a-z][a-z0-9+.-]*)://');
    const allowed = {'vless', 'vmess', 'trojan', 'ss', 'https', 'http'};
    return schemes.allMatches(input).any((m) => !allowed.contains(m.group(1)!.toLowerCase()));
  }

  Future<void> _importText(String input) async {
    final raw = input.replaceFirst('\uFEFF', '').trim();
    if (raw.isEmpty) return;
    try {
      String payload = raw;
      if (raw.startsWith('[Interface]') || RegExp(r'(?m)^\s*\[Interface\]\s*$').hasMatch(raw)) {
        _validateWireGuardConfig(raw);
        final endpoint = RegExp(r'(?im)^Endpoint\s*=\s*(.+)$').firstMatch(raw)?.group(1)?.trim();
        final profile = VpnProfile(name: endpoint == null ? 'WireGuard ${_profiles.length + 1}' : 'WireGuard • $endpoint', config: raw, protocol: 'WIREGUARD');
        if (_profiles.any((p) => p.config == profile.config)) { _toast('این کانفیگ قبلاً اضافه شده است'); return; }
        _profiles.add(profile);
        if (_selected < 0) _selected = 0;
        await _saveProfiles();
        if (!mounted) return;
        setState(() {});
        _toast('کانفیگ WireGuard با موفقیت اضافه شد');
        return;
      }
      if (raw.startsWith('https://') || raw.startsWith('http://')) {
        final uri = Uri.parse(raw);
        if (uri.path.toLowerCase().endsWith('.json') || uri.path.toLowerCase().endsWith('.yaml') ||
            uri.path.toLowerCase().endsWith('.yml') || !raw.contains('://vless')) {
          final response = await http.get(uri).timeout(const Duration(seconds: 20));
          if (response.statusCode < 200 || response.statusCode >= 300) throw Exception('دریافت اشتراک ناموفق بود: HTTP ${response.statusCode}');
          payload = response.body;
        }
      }
      if (_containsUnsupportedProxyScheme(payload)) {
        throw const FormatException('فقط کانفیگ‌های WireGuard و لینک/اشتراک V2Ray شامل VLESS، VMess، Trojan و Shadowsocks پشتیبانی می‌شوند.');
      }
      List<FlutterVlessURL> parsed = <FlutterVlessURL>[];
      Object? parseError;
      try { parsed = FlutterVless.parseMany(payload); } catch (e) { parseError = e; }
      if (parsed.isEmpty && _looksLikeBase64Subscription(payload)) {
        final compact = payload.replaceAll(RegExp(r'\s'), '').replaceAll('-', '+').replaceAll('_', '/');
        payload = utf8.decode(base64.decode(base64.normalize(compact)));
        parsed = FlutterVless.parseMany(payload);
      }
      if (parsed.isEmpty) throw FormatException('کانفیگ V2Ray شناسایی نشد؛ لینک یا اشتراک معتبر VLESS، VMess، Trojan یا Shadowsocks وارد کن. ${parseError ?? ''}');
      int added = 0;
      for (final item in parsed) {
        final config = item.getFullConfiguration();
        final name = (item.remark?.trim().isNotEmpty ?? false) ? item.remark!.trim() : '${_protocol(payload)} - ${_profiles.length + 1}';
        final profile = VpnProfile(name: name, config: config, protocol: _protocol(payload));
        if (!_profiles.any((p) => p.config == profile.config)) { _profiles.add(profile); added++; }
      }
      if (_profiles.isNotEmpty && _selected < 0) _selected = 0;
      await _saveProfiles();
      if (!mounted) return;
      setState(() {});
      _toast(added == 0 ? 'کانفیگ تکراری بود' : '$added کانفیگ اضافه شد');
    } catch (e) { _toast('ورود کانفیگ ناموفق بود: $e'); }
  }
  Future<void> _importDialog() async {
    final controller = TextEditingController();
    await showModalBottomSheet<void>(
      context: context, isScrollControlled: true, backgroundColor: panel,
      builder: (ctx) => Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('افزودن کانفیگ WireGuard یا V2Ray', style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          TextField(controller: controller, maxLines: 5, minLines: 3,
            decoration: const InputDecoration(hintText: 'لینک VLESS / VMess / Trojan / Shadowsocks، اشتراک V2Ray یا فایل WireGuard (.conf)')),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: OutlinedButton.icon(
              onPressed: () async {
                final data = await Clipboard.getData('text/plain');
                if (data?.text != null) controller.text = data!.text!;
              }, icon: const Icon(Icons.content_paste), label: const Text('چسباندن'))),
            const SizedBox(width: 8),
            Expanded(child: OutlinedButton.icon(
              onPressed: () async {
                final result = await FilePicker.platform.pickFiles(withData: true);
                if (result == null || result.files.isEmpty) return;
                final file = result.files.single;
                final extension = (file.extension ?? '').toLowerCase();
                const proprietary = {'ehi', 'sip', 'sksplus', 'lnk', 'tls'};
                if (proprietary.contains(extension)) {
                  _toast('این قالب جزو فرمت‌های پشتیبانی‌شده نیست. فقط فایل WireGuard با پسوند .conf یا خروجی متنی/اشتراک V2Ray وارد کن.');
                  return;
                }
                final bytes = file.bytes;
                if (bytes == null) {
                  _toast('خواندن فایل ممکن نشد؛ دوباره انتخابش کن.');
                  return;
                }
                try {
                  controller.text = utf8.decode(bytes);
                } on FormatException {
                  _toast('این فایل متن قابل‌خواندن نیست. فایل WireGuard .conf یا لینک/اشتراک متنی V2Ray انتخاب کن.');
                }
              }, icon: const Icon(Icons.folder_open), label: const Text('انتخاب فایل')),
            ),
          ]),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () async {
              final value = controller.text;
              Navigator.pop(ctx);
              await _importText(value);
            },
            child: const Text('وارد کردن کانفیگ')),
        ]),
      ),
    );
  }

  Future<void> _connect() async {
    if (_selected < 0 || _selected >= _profiles.length) {
      _toast('ابتدا یک کانفیگ وارد و انتخاب کن'); return;
    }
    final profile = _profiles[_selected];
    if (profile.protocol == 'WIREGUARD') {
      setState(() => _busy = true);
      try {
        await wg.start('irkhatary', profile.config);
        final status = await wg.status('irkhatary');
        if (!mounted) return;
        setState(() => _wireGuardConnected = status.state == wg.TunnelState.up);
        _toast(_wireGuardConnected ? 'تونل WireGuard فعال شد' : 'وضعیت WireGuard: ${status.state}');
      } catch (e) { _toast('اتصال WireGuard برقرار نشد: $e'); }
      finally { if (mounted) setState(() => _busy = false); }
      return;
    }
    if (!_ready) { _toast(_engineError ?? 'هسته V2Ray/Xray در حال آماده‌سازی است'); return; }
    setState(() => _busy = true);
    try {
      if (!await _engine.requestPermission()) { _toast('مجوز VPN داده نشد'); return; }
      await _engine.startVless(remark: profile.name, config: profile.config);
      _toast('درخواست اتصال ارسال شد؛ منتظر تأیید هسته هستیم');
    } catch (e) {
      _toast('اتصال برقرار نشد: $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _disconnect() async {
    setState(() => _busy = true);
    try {
      if (_wireGuardConnected || (_selected >= 0 && _selected < _profiles.length && _profiles[_selected].protocol == 'WIREGUARD')) {
        await wg.stop('irkhatary');
        if (mounted) setState(() => _wireGuardConnected = false);
      } else { await _engine.stopVless(); }
    } catch (e) { _toast('قطع اتصال ناموفق بود: $e'); }
    finally { if (mounted) setState(() => _busy = false); }
  }

  Future<void> _testDelay() async {
    if (_selected < 0 || !_ready || _profiles[_selected].protocol == 'WIREGUARD') return;
    try {
      final value = _connected
          ? await _engine.getConnectedServerDelay()
          : await _engine.getServerDelay(config: _profiles[_selected].config);
      if (mounted) setState(() => _delay = value);
    } catch (_) { if (mounted) setState(() => _delay = -1); }
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selected >= 0 && _selected < _profiles.length ? _profiles[_selected] : null;
    final statusText = _connected ? 'متصل' : _connecting ? 'در حال اتصال' : 'قطع';
    final statusColor = _connected ? const Color(0xFF45D483) : _connecting ? gold : Colors.white54;
    return Scaffold(
      appBar: AppBar(
        title: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('IR_Khatary VPN', style: TextStyle(fontWeight: FontWeight.w900, letterSpacing: 0.4)),
          Text('WireGuard • V2Ray', style: TextStyle(fontSize: 11, color: Colors.white54)),
        ]),
        actions: [
          IconButton(tooltip: 'راهنما', onPressed: () => showAboutDialog(
            context: context, applicationName: 'IR_Khatary VPN',
            applicationVersion: '1.0.0',
            children: const [Text('برای اتصال واقعی، یک کانفیگ معتبر یا اشتراک فعال وارد کنید. این برنامه سرور رایگان تولید نمی‌کند.')],
          ), icon: const Icon(Icons.info_outline)),
        ],
      ),
      body: SafeArea(
        child: ListView(padding: const EdgeInsets.fromLTRB(18, 8, 18, 24), children: [
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF211C12), Color(0xFF121319)], begin: Alignment.topRight, end: Alignment.bottomLeft),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: gold.withOpacity(.45)),
            ),
            child: Column(children: [
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                const Text('وضعیت اتصال', style: TextStyle(color: Colors.white70)),
                Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(color: statusColor.withOpacity(.12), borderRadius: BorderRadius.circular(30)),
                  child: Row(children: [Container(width: 7, height: 7, decoration: BoxDecoration(color: statusColor, shape: BoxShape.circle)),
                    const SizedBox(width: 6), Text(statusText, style: TextStyle(color: statusColor, fontWeight: FontWeight.bold))])),
              ]),
              const SizedBox(height: 22),
              GestureDetector(
                onTap: _connecting ? null : (_connected ? _disconnect : _connect),
                child: Container(
                  width: 154, height: 154,
                  decoration: BoxDecoration(shape: BoxShape.circle,
                    gradient: const RadialGradient(colors: [Color(0xFF3A301B), Color(0xFF17130D), Color(0xFF101116)]),
                    border: Border.all(color: _connected ? const Color(0xFF45D483) : gold, width: 2),
                    boxShadow: [BoxShadow(color: (_connected ? const Color(0xFF45D483) : gold).withOpacity(.2), blurRadius: 28, spreadRadius: 4)]),
                  child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                    Icon(_connected ? Icons.shield : Icons.power_settings_new, size: 42, color: _connected ? const Color(0xFF45D483) : gold),
                    const SizedBox(height: 8),
                    Text(_connected ? 'قطع اتصال' : 'اتصال', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  ]),
                ),
              ),
              const SizedBox(height: 16),
              Text(selected?.name ?? 'هیچ کانفیگی انتخاب نشده', maxLines: 1, overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text(selected?.protocol == 'WIREGUARD' ? 'WireGuard • تونل مستقل' : (_ready ? 'V2Ray / Xray • $_core' : (_engineError == null ? 'در حال راه‌اندازی هسته…' : 'هسته آماده نشد')),
                style: const TextStyle(color: Colors.white54, fontSize: 11)),
              if (_engineError != null) Padding(padding: const EdgeInsets.only(top: 6),
                child: Text(_engineError!, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.redAccent, fontSize: 10))),
            ]),
          ),
          const SizedBox(height: 16),
          Row(children: [
            Expanded(child: _metric('پینگ', _delay == null ? '—' : (_delay! < 0 ? 'ناموفق' : '$_delay ms'), Icons.speed)),
            const SizedBox(width: 10),
            Expanded(child: _metric('پروفایل‌ها', '${_profiles.length}', Icons.dns)),
          ]),
          const SizedBox(height: 22),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            const Text('کانفیگ‌های من', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            TextButton.icon(onPressed: _importDialog, icon: const Icon(Icons.add, size: 18), label: const Text('افزودن')),
          ]),
          if (_profiles.isEmpty)
            Container(padding: const EdgeInsets.all(20), decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(18)),
              child: const Column(children: [Icon(Icons.cloud_download_outlined, color: gold, size: 34), SizedBox(height: 8),
                Text('هنوز کانفیگی اضافه نشده'), SizedBox(height: 4),
                Text('فایل WireGuard یا لینک و اشتراک V2Ray وارد کن', style: TextStyle(color: Colors.white54, fontSize: 12))]))
          else ..._profiles.asMap().entries.map((entry) {
            final i = entry.key; final p = entry.value; final active = i == _selected;
            return Container(
              margin: const EdgeInsets.only(bottom: 9),
              decoration: BoxDecoration(color: active ? const Color(0xFF272318) : panel,
                borderRadius: BorderRadius.circular(16), border: Border.all(color: active ? gold.withOpacity(.7) : Colors.white10)),
              child: ListTile(
                onTap: () async {
                  if (_connected) { _toast('ابتدا اتصال را قطع کن'); return; }
                  setState(() { _selected = i; _delay = null; }); await _saveProfiles();
                },
                leading: Icon(active ? Icons.radio_button_checked : Icons.radio_button_off, color: active ? gold : Colors.white38),
                title: Text(p.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text(p.protocol, style: const TextStyle(color: Colors.white54, fontSize: 11)),
                trailing: PopupMenuButton<String>(
                  onSelected: (v) async {
                    if (v == 'delete') {
                      if (_connected && active) { _toast('ابتدا اتصال را قطع کن'); return; }
                      setState(() { _profiles.removeAt(i); _selected = _profiles.isEmpty ? -1 : (_selected >= _profiles.length ? _profiles.length - 1 : 0); });
                      await _saveProfiles();
                    } else if (v == 'copy') {
                      await Clipboard.setData(ClipboardData(text: p.config)); _toast('کانفیگ کپی شد');
                    }
                  },
                  itemBuilder: (_) => const [
                    PopupMenuItem(value: 'copy', child: Text('کپی JSON کانفیگ')),
                    PopupMenuItem(value: 'delete', child: Text('حذف')),
                  ],
                ),
              ),
            );
          }),
          const SizedBox(height: 6),
          OutlinedButton.icon(onPressed: _selected >= 0 && _ready ? _testDelay : null,
            icon: const Icon(Icons.speed), label: const Text('بررسی تأخیر سرور')),
          const SizedBox(height: 18),
          const Center(child: Text('IR_Khatary • اتصال واقعی، بدون وضعیت ساختگی', style: TextStyle(color: Colors.white30, fontSize: 10))),
        ]),
      ),
    );
  }

  Widget _metric(String title, String value, IconData icon) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    decoration: BoxDecoration(color: panel, borderRadius: BorderRadius.circular(16), border: Border.all(color: Colors.white10)),
    child: Row(children: [Icon(icon, color: gold, size: 20), const SizedBox(width: 9),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(color: Colors.white54, fontSize: 11)),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
      ]))]),
  );
}
