import 'dart:convert';
import 'package:flutter_v2ray/flutter_v2ray.dart';

/// یک سرور ذخیره‌شده. raw می‌تواند لینک (vmess://...) یا JSON کامل باشد.
class Server {
  final String id;
  String name;
  final String raw;
  final bool isJson;
  final String protocol;
  int ping; // -2: تست نشده، -1: ناموفق، >=0 میلی‌ثانیه

  Server({
    required this.id,
    required this.name,
    required this.raw,
    required this.isJson,
    required this.protocol,
    this.ping = -2,
  });

  String fullConfig() {
    if (isJson) return raw;
    return FlutterV2ray.parseFromURL(raw).getFullConfiguration();
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'raw': raw,
        'isJson': isJson,
        'protocol': protocol,
      };

  factory Server.fromJson(Map<String, dynamic> j) => Server(
        id: j['id'],
        name: j['name'],
        raw: j['raw'],
        isJson: j['isJson'] ?? false,
        protocol: j['protocol'] ?? '?',
      );
}

class ImportResult {
  final List<Server> servers;
  final int unsupported;
  final String? message;
  ImportResult(this.servers, {this.unsupported = 0, this.message});
}

const _supported = ['vmess', 'vless', 'trojan', 'ss', 'socks'];
final _linkRe = RegExp(
    r'''(vmess|vless|trojan|ss|socks|hysteria2|hysteria|hy2|tuic|ssh|wireguard)://[^\s"'<>]+''',
    caseSensitive: false);

String? _tryBase64(String s) {
  var t = s.replaceAll(RegExp(r'\s'), '');
  if (t.isEmpty || !RegExp(r'^[A-Za-z0-9+/_\-=]+$').hasMatch(t)) return null;
  t = t.replaceAll('-', '+').replaceAll('_', '/');
  while (t.length % 4 != 0) {
    t += '=';
  }
  try {
    return utf8.decode(base64.decode(t), allowMalformed: false);
  } catch (_) {
    return null;
  }
}

/// تمام فرمت‌های قابل‌پشتیبانی را از متن ورودی بیرون می‌کشد.
ImportResult parseServers(String input) {
  final text = input.trim();
  if (text.isEmpty) return ImportResult([], message: 'متن خالی است');

  // 1) JSON کامل کانفیگ xray/v2ray
  if (text.startsWith('{')) {
    try {
      final j = jsonDecode(text);
      if (j is Map && j['outbounds'] != null) {
        final remark = (j['remarks'] ?? j['remark'] ?? 'Custom JSON').toString();
        return ImportResult([
          Server(
            id: DateTime.now().microsecondsSinceEpoch.toString(),
            name: remark,
            raw: text,
            isJson: true,
            protocol: 'json',
          )
        ]);
      }
    } catch (_) {}
  }

  // 2) لینک‌های مستقیم (در صورت نیاز بعد از دیکد base64 سابسکریپشن)
  var matches = _linkRe.allMatches(text).map((m) => m.group(0)!).toList();
  if (matches.isEmpty) {
    final decoded = _tryBase64(text);
    if (decoded != null) {
      matches = _linkRe.allMatches(decoded).map((m) => m.group(0)!).toList();
    }
  }

  final servers = <Server>[];
  final seen = <String>{};
  var unsupported = 0;
  var i = 0;
  for (final link in matches) {
    final proto = link.split('://').first.toLowerCase();
    if (!_supported.contains(proto)) {
      unsupported++;
      continue;
    }
    if (!seen.add(link)) continue;
    try {
      final u = FlutterV2ray.parseFromURL(link);
      final name = u.remark.trim().isEmpty ? '$proto server' : u.remark.trim();
      servers.add(Server(
        id: '${DateTime.now().microsecondsSinceEpoch}_${i++}',
        name: name,
        raw: link,
        isJson: false,
        protocol: proto,
      ));
    } catch (_) {
      unsupported++;
    }
  }
  return ImportResult(servers, unsupported: unsupported);
}
