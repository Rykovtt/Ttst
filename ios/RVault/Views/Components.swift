import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
    static let brand = Color(hex: 0x4F3FD1)
    static let accent2 = Color(hex: 0xB4436C)
    static let cardBG = Color(uiColor: .secondarySystemGroupedBackground)
}

let accentPalette: [UInt32] = [0x5B4BD6, 0x0E8A7E, 0xD9544D, 0xE08E2B, 0x2F7ED8, 0xB4436C, 0x6C9A2F, 0x8E44AD, 0x00897B, 0x546E7A]

func accentFor(_ key: String) -> Color {
    var h: UInt32 = 5381
    for b in key.utf8 { h = (h &* 33) &+ UInt32(b) }
    return Color(hex: accentPalette[Int(h % UInt32(accentPalette.count))])
}

/// Фото человека или инициалы на цветном круге.
struct AvatarView: View {
    let person: Person
    var size: CGFloat = 48
    @EnvironmentObject private var store: Store

    var body: some View {
        if let img = store.image(person.avatar) {
            Image(uiImage: img).resizable().scaledToFill()
                .frame(width: size, height: size).clipShape(Circle())
        } else {
            let c = accentFor(person.displayName)
            Circle().fill(LinearGradient(colors: [c, c.opacity(0.65)], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: size, height: size)
                .overlay(Text(person.initials).font(.system(size: size * 0.38, weight: .semibold)).foregroundStyle(.white))
        }
    }
}

/// Раскладка «чипов» с переносом строк.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > 0 && x + sz.width > width { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing
            maxX = max(maxX, x - spacing)
            rowH = max(rowH, sz.height)
        }
        return CGSize(width: maxX, height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + sz.width > bounds.maxX { x = bounds.minX; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(sz))
            x += sz.width + spacing
            rowH = max(rowH, sz.height)
        }
    }
}

struct Chip: View {
    let title: String
    var selected = false
    var color: Color? = nil
    var systemImage: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let color { Circle().fill(color).frame(width: 9, height: 9) }
                if let systemImage { Image(systemName: systemImage).font(.caption) }
                Text(title).font(.subheadline.weight(.medium)).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(selected ? Color.brand.opacity(0.18) : Color(uiColor: .tertiarySystemFill), in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.brand : .clear, lineWidth: 1))
            .foregroundStyle(selected ? Color.brand : .primary)
        }
        .buttonStyle(.plain)
    }
}

/// Скруглённая карточка раздела в карточке человека.
struct SectionCard<Content: View, Action: View>: View {
    let title: String
    let icon: String
    @ViewBuilder var action: () -> Action
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon).foregroundStyle(Color.brand)
                Text(title).font(.headline)
                Spacer()
                action()
            }
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.cardBG, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 16)
    }
}

extension SectionCard where Action == EmptyView {
    init(title: String, icon: String, @ViewBuilder content: @escaping () -> Content) {
        self.init(title: title, icon: icon, action: { EmptyView() }, content: content)
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    var icon: String? = nil

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let icon { Image(systemName: icon).foregroundStyle(.secondary).frame(width: 22) }
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(value).font(.body).textSelection(.enabled)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}

struct Stars: View {
    let value: Int
    var onChange: ((Int) -> Void)? = nil
    var size: CGFloat = 18

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { i in
                Image(systemName: i <= value ? "star.fill" : "star")
                    .font(.system(size: size))
                    .foregroundStyle(Color.accent2)
                    .onTapGesture { onChange?(value == i ? 0 : i) }
            }
        }
    }
}

struct Badge: View {
    let text: String
    var body: some View {
        Text(text).font(.caption2.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Color.accent2.opacity(0.15), in: Capsule())
            .foregroundStyle(Color.accent2)
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let text: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 40)).foregroundStyle(Color.brand)
                .frame(width: 88, height: 88).background(Color.brand.opacity(0.12), in: Circle())
            Text(title).font(.title3.weight(.semibold))
            Text(text).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(32).frame(maxWidth: .infinity)
    }
}

func contactIcon(_ t: ContactType) -> String {
    switch t {
    case .phone: return "phone.fill"
    case .email: return "envelope.fill"
    case .telegram: return "paperplane.fill"
    case .whatsapp: return "message.fill"
    case .viber: return "bubble.left.fill"
    case .instagram: return "camera.fill"
    case .vk: return "at"
    case .facebook: return "person.2.fill"
    case .website: return "globe"
    case .other: return "ellipsis.circle.fill"
    }
}

func channelIcon(_ c: NotifyChannel) -> String {
    switch c {
    case .whatsapp: return "message.fill"
    case .telegram: return "paperplane.fill"
    case .sms: return "bubble.left.fill"
    case .none: return "bell.slash"
    }
}
