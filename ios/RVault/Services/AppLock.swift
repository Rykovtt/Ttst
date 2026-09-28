import Foundation
import LocalAuthentication

/// Вход по Face ID / Touch ID / коду телефона.
@MainActor
final class AppLock: ObservableObject {
    @Published var locked: Bool
    private var backgroundAt: Date?
    private var authenticating = false

    init() {
        locked = UserDefaults.standard.bool(forKey: Prefs.lock)
    }

    static var available: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    func wentBackground() { backgroundAt = Date() }

    /// Блокируем снова, если приложение было в фоне дольше минуты.
    func cameForeground() {
        guard UserDefaults.standard.bool(forKey: Prefs.lock), let t = backgroundAt else { return }
        if Date().timeIntervalSince(t) > 60 { locked = true }
        backgroundAt = nil
    }

    func unlock() {
        guard locked, !authenticating else { return }
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else {
            locked = false
            return
        }
        authenticating = true
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Подтвердите, что это вы") { ok, _ in
            Task { @MainActor in
                self.authenticating = false
                if ok { self.locked = false }
            }
        }
    }
}
