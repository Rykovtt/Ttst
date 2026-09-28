import SwiftUI

struct RootView: View {
    @EnvironmentObject private var lock: AppLock
    @EnvironmentObject private var router: Router
    @EnvironmentObject private var store: Store
    @Environment(\.scenePhase) private var phase
    @AppStorage(Prefs.privacy) private var privacy = true
    @AppStorage(Prefs.appTitle) private var appTitle = ""
    @State private var obscured = false

    var body: some View {
        ZStack {
            TabView(selection: $router.tab) {
                PeopleView().tabItem { Label("Люди", systemImage: "person.2.fill") }.tag(0)
                CalendarView().tabItem { Label("Календарь", systemImage: "calendar") }.tag(1)
                PeopleMapView().tabItem { Label("Карта", systemImage: "map.fill") }.tag(2)
                BroadcastView().tabItem { Label("Рассылка", systemImage: "paperplane.fill") }.tag(3)
                SettingsView().tabItem { Label("Настройки", systemImage: "gearshape.fill") }.tag(4)
            }
            if lock.locked {
                LockScreen(title: appTitle.isBlank ? "RVault" : appTitle) { lock.unlock() }
                    .transition(.opacity)
            } else if obscured && privacy {
                // Скрываем содержимое в переключателе приложений.
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    Image(systemName: "lock.fill").font(.system(size: 44)).foregroundStyle(.secondary)
                }
                .ignoresSafeArea()
            }
        }
        .sheet(item: $router.sendReminder) { req in
            ReminderSendSheet(request: req)
        }
        .onChange(of: phase) { _, newPhase in
            switch newPhase {
            case .active:
                obscured = false
                lock.cameForeground()
                if lock.locked { lock.unlock() }
                Reminders.reschedule()
            case .inactive:
                obscured = true
            case .background:
                obscured = true
                lock.wentBackground()
            @unknown default:
                break
            }
        }
        .onAppear {
            Reminders.requestPermission()
            if lock.locked { lock.unlock() }
        }
    }
}

struct LockScreen: View {
    let title: String
    let unlock: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock.fill").font(.system(size: 48)).foregroundStyle(Color.brand)
                .frame(width: 110, height: 110).background(Color.brand.opacity(0.14), in: Circle())
            Text(title).font(.title2.bold())
            Text("Подтвердите, что это вы").foregroundStyle(.secondary)
            Button(action: unlock) {
                Label("Разблокировать", systemImage: "faceid").padding(.horizontal, 12).padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}
