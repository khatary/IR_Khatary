import 'package:flutter_test/flutter_test.dart';
import 'package:ir_khatary_vpn/main.dart';

void main() {
  testWidgets('IR Khatary VPN app renders', (tester) async {
    await tester.pumpWidget(const IRKhataryApp());
    expect(find.text('IR_Khatary VPN'), findsOneWidget);
  });
}
