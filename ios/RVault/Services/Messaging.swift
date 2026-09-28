import Foundation
import MessageUI
import SwiftUI
import UIKit

/// Звонки, чаты мессенджеров и SMS. На iOS сообщение открывается готовым,
/// а кнопку «Отправить» нажимает сам пользователь — так требует система.
enum Messaging {
    @MainActor
    static func open(_ string: String) {
        guard let url = URL(string: string) else { return }
        UIApplication.shared.open(url)
    }

    static func enc(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? s
    }

    @MainActor static func call(_ phone: String) { open("tel:" + phone.filter { $0.isNumber || $0 == "+" }) }

    static func whatsappURL(_ phone: String, text: String = "") -> String {
        "https://wa.me/\(PhoneFormat.digitsForLink(phone))" + (text.isEmpty ? "" : "?text=" + enc(text))
    }

    static func telegramURL(_ handle: String, text: String = "") -> String {
        let h = handle.trimmed
        let isPhone = h.filter(\.isNumber).count >= 7 && !h.contains(where: \.isLetter)
        let base: String
        if isPhone {
            base = "https://t.me/+" + PhoneFormat.digitsForLink(h)
        } else {
            let name = h.replacingOccurrences(of: "@", with: "").components(separatedBy: "t.me/").last ?? h
            base = "https://t.me/" + name
        }
        return base + (text.isEmpty ? "" : "?text=" + enc(text))
    }

    @MainActor static func whatsapp(_ phone: String, text: String = "") { open(whatsappURL(phone, text: text)) }

    @MainActor static func telegram(_ handle: String, text: String = "") {
        if !text.isEmpty { UIPasteboard.general.string = text }
        open(telegramURL(handle, text: text))
    }

    @MainActor static func viber(_ phone: String) { open("viber://chat?number=%2B" + PhoneFormat.digitsForLink(phone)) }

    @MainActor static func email(_ address: String, subject: String = "", body: String = "") {
        open("mailto:\(address)?subject=\(enc(subject))&body=\(enc(body))")
    }

    @MainActor static func maps(_ place: Place, name: String) {
        if let lat = place.lat, let lng = place.lng {
            open("http://maps.apple.com/?ll=\(lat),\(lng)&q=\(enc(name))")
        } else {
            open("http://maps.apple.com/?q=\(enc(place.address))")
        }
    }

    @MainActor
    static func openContact(_ c: ContactItem) {
        let v = c.value.trimmed
        switch c.type {
        case .phone: call(v)
        case .email: email(v)
        case .telegram: telegram(v)
        case .whatsapp: whatsapp(v)
        case .viber: viber(v)
        case .instagram: open(v.hasPrefix("http") ? v : "https://instagram.com/" + v.replacingOccurrences(of: "@", with: ""))
        case .vk: open(v.hasPrefix("http") ? v : "https://vk.com/" + v.replacingOccurrences(of: "@", with: ""))
        case .facebook: open(v.hasPrefix("http") ? v : "https://facebook.com/" + v)
        case .website: open(v.hasPrefix("http") ? v : "https://" + v)
        case .other: UIPasteboard.general.string = v
        }
    }

    static var canSendSMS: Bool { MFMessageComposeViewController.canSendText() }
}

extension CharacterSet {
    static let urlQueryValueAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&+=?#")
        return set
    }()
}

/// Окно SMS с готовым текстом. `onFinish(true)` — сообщение отправлено.
struct MessageComposer: UIViewControllerRepresentable {
    let recipients: [String]
    let body: String
    let onFinish: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.recipients = recipients
        vc.body = body
        vc.messageComposeDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: MFMessageComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let onFinish: (Bool) -> Void
        init(onFinish: @escaping (Bool) -> Void) { self.onFinish = onFinish }

        func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
            controller.dismiss(animated: true)
            onFinish(result == .sent)
        }
    }
}

/// Системное меню «Поделиться» — для отправки текста в групповой чат любого мессенджера.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}
