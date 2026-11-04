import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'app_state.dart';

void main() => runApp(ChangeNotifierProvider(create: (_) => AppState()..init(), child: const VpnKingApp()));

class VpnKingApp extends StatelessWidget {
  const VpnKingApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false, title: 'VPN KING',
    theme: ThemeData.dark(useMaterial3: true).copyWith(
      scaffoldBackgroundColor: const Color(0xff101014),
      colorScheme: const ColorScheme.dark(primary: Color(0xffffc247), secondary: Color(0xffd99b25)),
      cardColor: const Color(0xff1d1d24)),
    home: const HomePage());
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override State<HomePage> createState() => _HomePageState();
}
class _HomePageState extends State<HomePage> {
  final input = TextEditingController();
  static const gold = Color(0xffffc247);
  void importDialog() {
    input.clear();
    showDialog(context: context, builder: (ctx) => AlertDialog(
      title: const Text('افزودن کانفیگ'), content: TextField(controller: input, maxLines: 7,
        decoration: const InputDecoration(hintText: 'لینک vmess / vless / trojan / ss یا JSON یا Base64')),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('لغو')),
        FilledButton(onPressed: () async {
          final text = input.text.trim(); Navigator.pop(ctx);
          await context.read<AppState>().add(text);
          if (mounted) ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(context.read<AppState>().servers.isEmpty ? 'کانفیگ معتبر پیدا نشد' : 'کانفیگ‌ها اضافه شدند')));
        }, child: const Text('افزودن'))
      ]));
  }
  @override Widget build(BuildContext context) {
    final app = context.watch<AppState>();
    return Scaffold(
      appBar: AppBar(title: const Text('👑 VPN KING', style: TextStyle(fontWeight: FontWeight.bold, color: gold)),
        actions: [IconButton(onPressed: () async {
          final data = await Clipboard.getData('text/plain');
          if (data?.text != null) { await context.read<AppState>().add(data!.text!); }
        }, icon: const Icon(Icons.content_paste))]),
      floatingActionButton: FloatingActionButton.extended(onPressed: importDialog,
        backgroundColor: gold, foregroundColor: Colors.black, icon: const Icon(Icons.add), label: const Text('افزودن سرور')),
      body: !app.initialized && app.error == null ? const Center(child: CircularProgressIndicator()) :
      Padding(padding: const EdgeInsets.all(18), child: Column(children: [
        const SizedBox(height: 28),
        Container(width: 170, height: 170, decoration: BoxDecoration(shape: BoxShape.circle,
          border: Border.all(color: gold, width: 3), boxShadow: [BoxShadow(color: gold.withValues(alpha: .16), blurRadius: 28)]),
          child: Center(child: Icon(app.connected ? Icons.shield : Icons.shield_outlined, size: 82, color: app.connected ? Colors.greenAccent : gold))),
        const SizedBox(height: 16),
        Text(app.connected ? 'متصل' : (app.connecting ? 'در حال اتصال...' : 'قطع'), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16),
        SizedBox(width: 180, height: 48, child: FilledButton(
          onPressed: app.initialized && !app.connecting ? app.toggle : null,
          style: FilledButton.styleFrom(backgroundColor: app.connected ? Colors.redAccent : gold, foregroundColor: Colors.black),
          child: Text(app.connected ? 'قطع اتصال' : (app.connecting ? 'لطفاً صبر کن' : 'اتصال')))),
        if (app.error != null) Padding(padding: const EdgeInsets.all(10), child: Text(app.error!, style: const TextStyle(color: Colors.redAccent))),
        const SizedBox(height: 20),
        Row(children: [const Text('سرورها', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const Spacer(), Text('${app.servers.length} مورد', style: const TextStyle(color: Colors.white54))]),
        const SizedBox(height: 8),
        Expanded(child: app.servers.isEmpty ? const Center(child: Text('برای شروع، کانفیگ خودت را اضافه کن', style: TextStyle(color: Colors.white54))) :
          ListView.builder(itemCount: app.servers.length, itemBuilder: (_, i) {
            final s = app.servers[i]; final selected = s.id == app.selectedId;
            return Card(child: ListTile(onTap: () => app.choose(s.id),
              leading: Icon(selected ? Icons.radio_button_checked : Icons.radio_button_off, color: selected ? gold : Colors.white54),
              title: Text(s.name, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(s.isJson ? 'Xray JSON' : s.raw.split('://').first.toUpperCase()),
              trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => app.delete(s.id))));
          })),
      ])));
  }
  @override void dispose() { input.dispose(); super.dispose(); }
}
