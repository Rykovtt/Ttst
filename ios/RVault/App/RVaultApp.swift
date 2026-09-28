import SwiftUI
import UserNotifications

@main
struct RVaultApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var store = Store.shared
    @StateObject private var lock = AppLock()
    @StateObject private var router = Router.shared

    init() {
        Prefs.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(lock)
                .environmentObject(router)
                .tint(.brand)
        }
    }
}

/// Куда перейти после нажатия на уведомление.
@MainActor
final class Router: ObservableObject {
    static let shared = Router()
    @Published var tab = 0
    @Published var openPerson: UUID?
    @Published var sendReminder: ReminderRequest?
    @Published var openAppointment: UUID?
}

struct ReminderRequest: Identifiable {
    let appointmentId: UUID
    let key: String
    var id: String { appointmentId.uuidString + key }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .list])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        let appointment = (info["appointment"] as? String).flatMap(UUID.init(uuidString:))
        let key = info["key"] as? String
        let target = info["target"] as? String
        let person = (info["person"] as? String).flatMap(UUID.init(uuidString:))
        Task { @MainActor in
            let router = Router.shared
            if let appointment, let key, target == "CLIENT" {
                router.tab = 1
                router.sendReminder = ReminderRequest(appointmentId: appointment, key: key)
            } else if let appointment {
                router.tab = 1
                router.openAppointment = appointment
            } else if let person {
                router.tab = 0
                router.openPerson = person
            }
        }
        completionHandler()
    }
}
