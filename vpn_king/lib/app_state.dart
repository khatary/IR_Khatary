import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter_v2ray/flutter_v2ray.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'server.dart';

class AppState extends ChangeNotifier {
  final List<Server> servers = [];
  String? selectedId;
  V2RayStatus status = V2RayStatus();
  late final FlutterV2ray engine;
  bool initialized = false;
  bool busy = false;
  String? error;

  bool get connected => status.state.toUpperCase() == 'CONNECTED';
  bool get connecting => busy || status.state.toUpperCase() == 'CONNECTING';
  Server? get selected => servers.where((s) => s.id == selectedId).firstOrNull;

  Future<void> init() async {
    try {
      engine = FlutterV2ray(onStatusChanged: (value) {
        status = value;
        if (value.state.toUpperCase() == 'CONNECTED' || value.state.toUpperCase() == 'DISCONNECTED') busy = false;
        notifyListeners();
      });
      await engine.initializeV2Ray();
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString('servers');
      if (saved != null) {
        final list = jsonDecode(saved);
        if (list is List) {
          for (final item in list) {
            try { servers.add(Server.fromJson(Map<String, dynamic>.from(item))); } catch (_) {}
          }
        }
      }
      selectedId = prefs.getString('selected');
      if (!servers.any((s) => s.id == selectedId)) selectedId = servers.isEmpty ? null : servers.first.id;
      initialized = true;
    } catch (e) {
      error = 'موتور VPN راه‌اندازی نشد: $e';
    }
    notifyListeners();
  }

  Future<void> save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('servers', jsonEncode(servers.map((s) => s.toJson()).toList()));
    if (selectedId == null) { await prefs.remove('selected'); } else { await prefs.setString('selected', selectedId!); }
  }

  Future<void> add(String text) async {
    final items = parseServers(text);
    final existing = servers.map((s) => s.raw).toSet();
    for (final item in items) { if (existing.add(item.raw)) servers.add(item); }
    selectedId ??= servers.isEmpty ? null : servers.first.id;
    await save(); notifyListeners();
  }

  Future<void> choose(String id) async { selectedId = id; await save(); notifyListeners(); }
  Future<void> delete(String id) async {
    servers.removeWhere((s) => s.id == id);
    if (selectedId == id) selectedId = servers.isEmpty ? null : servers.first.id;
    await save(); notifyListeners();
  }

  Future<void> toggle() async {
    if (!initialized || busy) return;
    if (connected || connecting) {
      busy = true; notifyListeners();
      try { await engine.stopV2Ray(); } catch (e) { error = '$e'; busy = false; notifyListeners(); }
      return;
    }
    final server = selected;
    if (server == null) { error = 'ابتدا یک سرور اضافه و انتخاب کن'; notifyListeners(); return; }
    busy = true; error = null; notifyListeners();
    try {
      final allowed = await engine.requestPermission();
      if (!allowed) { busy = false; error = 'اجازه اتصال VPN داده نشد'; notifyListeners(); return; }
      await engine.startV2Ray(remark: server.name, config: server.config(),
        proxyOnly: false, bypassSubnets: null, blockedApps: null);
    } catch (e) {
      busy = false; error = 'اتصال شروع نشد: $e'; notifyListeners();
    }
  }
}
