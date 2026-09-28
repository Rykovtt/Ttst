import SwiftUI
import UIKit

/// Альтернативные значки. На iOS меняется только картинка — подпись под значком всегда «RVault».
enum AppIcon: String, CaseIterable, Identifiable {
    case main, dark = "Dark", notes = "Notes", calc = "Calc", weather = "Weather", organizer = "Organizer", files = "Files", contacts = "Contacts"

    var id: String { rawValue }

    /// Имя для setAlternateIconName (nil — основной значок).
    var iconName: String? { self == .main ? nil : rawValue }

    /// Картинка для предпросмотра в настройках.
    var preview: String { "Preview" + (self == .main ? "Main" : rawValue) }

    var title: String {
        switch self {
        case .main: return "RVault"
        case .dark: return "Тёмный"
        case .notes: return "Заметки"
        case .calc: return "Калькулятор"
        case .weather: return "Погода"
        case .organizer: return "Органайзер"
        case .files: return "Файлы"
        case .contacts: return "Контакты"
        }
    }

    @MainActor static var current: AppIcon {
        AppIcon.allCases.first { $0.iconName == UIApplication.shared.alternateIconName } ?? .main
    }

    @MainActor func apply() {
        UIApplication.shared.setAlternateIconName(iconName) { _ in }
    }
}
