import 'dart:convert';
import 'package:flutter_v2ray/flutter_v2ray.dart';

class Server {
  Server({required this.id, required this.name, required this.raw, required this.isJson});
  final String id;
  final String name;
  final String raw;
  final bool isJson;

  String config() {
    if (isJson) {
      final value = jsonDecode(raw);
      if (value is! Map || value['outbounds'] is! List || (value['outbounds'] as List).isEmpty) {
        throw const FormatException('JSON فاقد outbounds معتبر است');
      }
      return raw;
    }
    final result = FlutterV2ray.parseFromURL(raw);
    final value = result.getFullConfiguration();
    final decoded = jsonDecode(value);
    if (decoded is! Map || decoded['outbounds'] is! List || (decoded['outbounds'] as List).isEmpty) {
      throw const FormatException('کانفیگ تولیدشده معتبر نیست');
    }
    return value;
  }

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'raw': raw, 'isJson': isJson};
  factory Server.fromJson(Map<String, dynamic> value) => Server(
    id: value['id'].toString(), name: value['name'].toString(),
    raw: value['raw'].toString(), isJson: value['isJson'] == true);
}

List<Server> parseServers(String input) {
  final text = input.trim();
  if (text.isEmpty) return [];
  final found = <Server>[];
  final seen = <String>{};
  void add(String raw, {bool json = false, String? name}) {
    if (!seen.add(raw)) return;
    try {
      final server = Server(id: DateTime.now().microsecondsSinceEpoch.toString(),
        name: name ?? (json ? 'JSON config' : raw.split('://').first.toUpperCase()),
        raw: raw, isJson: json);
      server.config();
      found.add(server);
    } catch (_) {}
  }
  if (text.startsWith('{')) {
    try {
      final decoded = jsonDecode(text);
      if (decoded is Map) add(text, json: true, name: (decoded['remarks'] ?? decoded['remark'] ?? 'JSON').toString());
    } catch (_) {}
  }
  final regex = RegExp(r'(?:vmess|vless|trojan|ss|socks)://[^\\s"<>]+', caseSensitive: false);
  for (final match in regex.allMatches(text)) {
    final link = match.group(0)!.replaceAll(RegExp(r'[),;]+$'), '');
    try {
      final parsed = FlutterV2ray.parseFromURL(link);
      add(link, name: parsed.remark.isEmpty ? link.split('://').first.toUpperCase() : parsed.remark);
    } catch (_) {}
  }
  if (found.isEmpty && !text.startsWith('{')) {
    try {
      final decoded = utf8.decode(base64.decode(base64.normalize(text.replaceAll(RegExp(r'\\s'), ''))));
      if (decoded != text) return parseServers(decoded);
    } catch (_) {}
  }
  return found;
}
