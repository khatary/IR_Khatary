import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter_v2ray/flutter_v2ray.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'server.dart';

class AppState extends ChangeNotifier {
  final List<Server> servers = [];
  String? selectedId;
  V2RayStatus status = V2RayStatus();
  String? error;
  bool _ready = false;
  late final FlutterV2ray v2ray;

  String get state => status.state;
  bool get connected => state == 'CONNECTED';
  bool get connecting => state == 'CONNECTING';

  Server? get selected {
    for (final s in servers) {
      if (s.id == selectedId) return s;
    }
    return null;
  }

  Future<void> init() async {
    v2ray = FlutterV2ray(onStatusChanged: (s) {
      status = s;
      notifyListeners();
    });
    await v2ray.initializeV2Ray();
    _ready = true;
    final p = await SharedPreferences.getInstance();
    final raw = p.getString('servers');
    if (raw != null) {
      for (final j in jsonDecode(raw)) {
        servers.add(Server.fromJson(Map<String, dynamic>.from(j)));
      }
    }
    selectedId = p.getString('selected');
    if (selectedId == null && servers.isNotEmpty) selectedId = servers.first.id;
    notifyListeners();
  }

  Future<void> _save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString(
        'servers', jsonEncode(servers.map((e) => e.toJson()).toList()));
    if (selectedId != null) await p.setString('selected', selectedId!);
  }

  void select(String id) {
    selectedId = id;
    _save();
    notifyListeners();
  }

  void remove(String id) {
    servers.removeWhere((s) => s.id == id);
    if (selectedId == id) {
      selectedId = servers.isEmpty ? null : servers.first.id;
    }
    _save();
    notifyListeners();
  }

  void clearAll() {
    servers.clear();
    selectedId = null;
    _save();
    notifyListeners();
  }

  /// سرورها را اضافه می‌کند و تعداد جدیدها را برمی‌گرداند.
  int addServers(List<Server> list) {
    final existing = servers.map((e) => e.raw).toSet();
    var n = 0;
    for (final s in list) {
      if (existing.add(s.raw)) {
        servers.add(s);
        n++;
      }
    }
    selectedId ??= servers.isEmpty ? null : servers.first.id;
    _save();
    notifyListeners();
    return n;
  }

  Future<ImportResult> importFromUrl(String url) async {
    try {
      final c = HttpClient()..connectionTimeout = const Duration(seconds: 15);
      final req = await c.getUrl(Uri.parse(url));
      req.headers.set('User-Agent', 'v2rayNG/1.8.0');
      final res = await req.close().timeout(const Duration(seconds: 20));
      final body = await res.transform(utf8.decoder).join();
      c.close();
      return parseServers(body);
    } catch (e) {
      return ImportResult([], message: 'دریافت لینک ناموفق بود: $e');
    }
  }

  Future<void> toggle() async {
    if (!_ready) return;
    error = null;
    if (connected || connecting) {
      v2ray.stopV2Ray();
      return;
    }
    final s = selected;
    if (s == null) {
      error = 'اول یک سرور انتخاب یا اضافه کن';
      notifyListeners();
      return;
    }
    try {
      final ok = await v2ray.requestPermission();
      if (!ok) {
        error = 'مجوز VPN داده نشد';
        notifyListeners();
        return;
      }
      v2ray.startV2Ray(
        remark: s.name,
        config: s.fullConfig(),
        proxyOnly: false,
        bypassSubnets: null,
        blockedApps: null,
      );
    } catch (e) {
      error = 'خطا در اتصال: $e';
      notifyListeners();
    }
  }

  Future<void> pingOne(Server s) async {
    s.ping = -3; // در حال تست
    notifyListeners();
    try {
      final d = await v2ray
          .getServerDelay(config: s.fullConfig())
          .timeout(const Duration(seconds: 8));
      s.ping = d;
    } catch (_) {
      s.ping = -1;
    }
    notifyListeners();
  }

  Future<void> pingAll() async {
    final list = List<Server>.from(servers);
    for (var i = 0; i < list.length; i += 4) {
      await Future.wait(list.skip(i).take(4).map(pingOne));
    }
  }
}
