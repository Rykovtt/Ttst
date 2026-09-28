import MapKit
import PhotosUI
import SwiftUI

struct PersonDetailView: View {
    let personId: UUID
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var editing: Person?
    @State private var confirmDelete = false
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var showCamera = false
    @State private var showJournal = false
    @State private var showRelation = false
    @State private var viewerIndex: Int?
    @State private var newAppointment: Appointment?
    @State private var openAppointment: Appointment?
    @State private var composeTemplate = false

    var body: some View {
        if let p = store.person(personId) {
            content(p)
        } else {
            Color.clear.onAppear { dismiss() }
        }
    }

    private func content(_ p: Person) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                hero(p)
                quickActions(p)
                if let bd = ArchiveLogic.formatBirthday(p) {
                    SectionCard(title: "День рождения", icon: "birthday.cake.fill") {
                        let parts = [ArchiveLogic.daysUntilBirthday(p).map { "🎂 " + ArchiveLogic.daysString($0) },
                                     ArchiveLogic.turningAge(p).map { "исполнится " + ArchiveLogic.ageString($0) }].compactMap { $0 }
                        InfoRow(label: parts.joined(separator: " · "), value: bd)
                    }
                }
                if !p.contacts.isEmpty {
                    SectionCard(title: "Контакты", icon: "phone.circle.fill") {
                        ForEach(p.contacts) { c in
                            Button { Messaging.openContact(c) } label: {
                                InfoRow(label: c.label.isBlank ? c.type.title : c.label,
                                        value: c.type.isPhone ? PhoneFormat.pretty(c.value) : c.value, icon: contactIcon(c.type))
                            }
                            .buttonStyle(.plain)
                            .contextMenu { Button("Скопировать") { UIPasteboard.general.string = c.value } }
                        }
                    }
                }
                mainInfo(p)
                if !p.places.isEmpty { placesCard(p) }
                relationsCard(p)
                appointmentsCard(p)
                let cats = Dictionary(grouping: p.details) { $0.category.isBlank ? "Разное" : $0.category }
                ForEach(orderedCategories(p.details), id: \.self) { cat in
                    SectionCard(title: cat, icon: "checklist") {
                        ForEach(cats[cat] ?? []) { f in InfoRow(label: f.name.isBlank ? "—" : f.name, value: f.value) }
                    }
                }
                photosCard(p)
                journalCard(p)
                if !p.notes.isBlank {
                    SectionCard(title: "Заметки", icon: "note.text") { Text(p.notes).textSelection(.enabled) }
                }
            }
            .padding(.bottom, 32)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { store.update(p.id) { $0.favorite.toggle() } } label: { Image(systemName: p.favorite ? "star.fill" : "star") }
                Button { editing = p } label: { Image(systemName: "pencil") }
                Menu {
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Удалить", systemImage: "trash") }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
        .sheet(item: $editing) { PersonEditView(person: $0) }
        .sheet(isPresented: $showJournal) { JournalSheet(personId: p.id) }
        .sheet(isPresented: $showRelation) { AddRelationSheet(me: p) }
        .sheet(isPresented: $showCamera) {
            CameraPicker { img in store.addPhoto(img, to: p.id) }.ignoresSafeArea()
        }
        .sheet(item: $newAppointment) { AppointmentEditView(appointment: $0, isNew: true) }
        .sheet(item: $openAppointment) { AppointmentEditView(appointment: $0, isNew: false) }
        .sheet(isPresented: $composeTemplate) { NavigationStack { BroadcastView(initialIds: [p.id]) } }
        .fullScreenCover(item: Binding(get: { viewerIndex.map { IndexBox(value: $0) } }, set: { viewerIndex = $0?.value })) { box in
            PhotoViewer(personId: p.id, start: box.value)
        }
        .onChange(of: photoItems) { _, items in
            Task {
                for item in items {
                    if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                        store.addPhoto(img, to: p.id)
                    }
                }
                photoItems = []
            }
        }
        .confirmationDialog("Удалить \(p.displayName)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) { store.delete(p.id); dismiss() }
        } message: {
            Text("Карточка, фото и хроника будут удалены без возможности восстановления.")
        }
    }

    private func orderedCategories(_ details: [DetailField]) -> [String] {
        var seen: [String] = []
        for d in details {
            let c = d.category.isBlank ? "Разное" : d.category
            if !seen.contains(c) { seen.append(c) }
        }
        return seen
    }

    private func hero(_ p: Person) -> some View {
        ZStack(alignment: .bottomLeading) {
            Group {
                if let img = store.image(p.avatar) {
                    Image(uiImage: img).resizable().scaledToFill()
                        .onTapGesture { viewerIndex = p.photos.firstIndex { $0.file == p.avatar } ?? 0 }
                } else {
                    let c = accentFor(p.displayName)
                    LinearGradient(colors: [c, c.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .overlay(Text(p.initials).font(.system(size: 80, weight: .bold)).foregroundStyle(.white.opacity(0.9)))
                }
            }
            .frame(height: 340).frame(maxWidth: .infinity).clipped()
            LinearGradient(colors: [.clear, Color(uiColor: .systemGroupedBackground)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 4) {
                Text(p.fullName).font(.title.bold())
                let sub = [p.nickname.isBlank ? nil : "«\(p.nickname)»", p.relation.isBlank ? nil : p.relation].compactMap { $0 }.joined(separator: " · ")
                if !sub.isEmpty { Text(sub).foregroundStyle(.secondary) }
                if p.closeness > 0 { Stars(value: p.closeness, size: 15) }
            }
            .padding(20)
        }
        .frame(height: 340)
    }

    private func quickActions(_ p: Person) -> some View {
        HStack(spacing: 0) {
            if let ph = p.phone {
                action("Звонок", "phone.fill") { Messaging.call(ph) }
                action("SMS", "bubble.left.fill") { Messaging.open("sms:" + ph.filter { $0.isNumber || $0 == "+" }) }
            }
            if let wa = p.whatsapp { action("WhatsApp", "message.fill") { Messaging.whatsapp(wa) } }
            if let tg = p.telegram { action("Telegram", "paperplane.fill") { Messaging.telegram(tg) } }
            if let em = p.email { action("Почта", "envelope.fill") { Messaging.email(em) } }
            action("Записать", "calendar.badge.plus") { newAppointment = AppointmentDefaults.make(personId: p.id, date: Date()) }
            if p.phone != nil || p.telegram != nil { action("Шаблон", "text.bubble.fill") { composeTemplate = true } }
        }
        .padding(.horizontal, 8)
    }

    private func action(_ title: String, _ icon: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 18)).frame(width: 48, height: 48)
                    .background(Color.brand.opacity(0.14), in: Circle()).foregroundStyle(Color.brand)
                Text(title).font(.caption2).foregroundStyle(.primary).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
    }

    private func mainInfo(_ p: Person) -> some View {
        SectionCard(title: "Основное", icon: "person.text.rectangle") {
            if !p.relation.isBlank { InfoRow(label: "Кем приходится", value: p.relation, icon: "hands.clap") }
            if !p.gender.isBlank { InfoRow(label: "Пол", value: p.gender, icon: "person") }
            let work = [p.position, p.company].filter { !$0.isBlank }.joined(separator: ", ")
            if !work.isEmpty { InfoRow(label: "Работа", value: work, icon: "briefcase") }
            if !p.city.isBlank { InfoRow(label: "Город", value: p.city, icon: "building.2") }
            if !p.howMet.isBlank { InfoRow(label: "Как познакомились", value: p.howMet, icon: "info.circle") }
            let groups = store.groups(of: p)
            if !groups.isEmpty {
                FlowLayout(spacing: 6) {
                    ForEach(groups) { g in Chip(title: g.title, color: Color(hex: g.color)) {} }
                }
            }
            Text(["Добавлен(а) " + p.createdAt.formatted(date: .long, time: .omitted),
                  p.lastContactAt.map { "Последний контакт " + $0.formatted(date: .long, time: .omitted) }].compactMap { $0 }.joined(separator: "\n"))
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private func placesCard(_ p: Person) -> some View {
        SectionCard(title: "Адреса", icon: "mappin.circle.fill") {
            let marked = p.places.filter(\.hasCoords)
            if !marked.isEmpty {
                Map(initialPosition: .automatic) {
                    ForEach(marked) { pl in
                        Annotation(pl.kind.title, coordinate: CLLocationCoordinate2D(latitude: pl.lat!, longitude: pl.lng!)) {
                            AvatarView(person: p, size: 34).overlay(Circle().stroke(.white, lineWidth: 2))
                        }
                    }
                }
                .frame(height: 170).clipShape(RoundedRectangle(cornerRadius: 16)).allowsHitTesting(false)
            }
            ForEach(p.places) { pl in
                Button { Messaging.maps(pl, name: p.displayName) } label: {
                    InfoRow(label: pl.label.isBlank ? pl.kind.title : pl.label, value: pl.address.isBlank ? "Точка на карте" : pl.address,
                            icon: pl.kind == .work ? "briefcase.fill" : "house.fill")
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func relationsCard(_ p: Person) -> some View {
        let views = Relations.view(for: p.id, relations: store.relations, people: Dictionary(uniqueKeysWithValues: store.people.map { ($0.id, $0) }))
        return SectionCard(title: "Семья и связи", icon: "figure.2.and.child.holdinghands", action: {
            Button { showRelation = true } label: { Image(systemName: "plus") }
        }) {
            if views.isEmpty {
                Text("Супруги, дети, родители, коллеги — связывайте карточки между собой").font(.subheadline).foregroundStyle(.secondary)
            }
            ForEach(views) { v in
                HStack {
                    NavigationLink(value: v.other.id) {
                        HStack(spacing: 12) {
                            AvatarView(person: v.other, size: 38)
                            VStack(alignment: .leading) {
                                Text(v.label).font(.caption).foregroundStyle(Color.brand)
                                Text(v.other.displayName)
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                    Button { store.deleteRelation(v.relation.id) } label: { Image(systemName: "xmark").foregroundStyle(.secondary) }
                        .buttonStyle(.plain)
                }
            }
        }
    }

    private func appointmentsCard(_ p: Person) -> some View {
        let list = store.appointments.filter { $0.personId == p.id }.sorted { $0.start > $1.start }
        let now = Date()
        let upcoming = list.filter { $0.end >= now && $0.status == .planned }.sorted { $0.start < $1.start }
        let past = list.filter { a in !upcoming.contains { $0.id == a.id } }.prefix(3)
        return SectionCard(title: "Записи", icon: "calendar", action: {
            Button { newAppointment = AppointmentDefaults.make(personId: p.id, date: Date()) } label: { Image(systemName: "plus") }
        }) {
            if list.isEmpty {
                Text("Запишите человека на встречу, приём или звонок — с напоминаниями").font(.subheadline).foregroundStyle(.secondary)
            }
            ForEach(upcoming + Array(past)) { a in
                Button { openAppointment = a } label: {
                    let status = a.status == .planned ? (a.end < now ? " · прошло" : "") : " · " + a.status.title.lowercased()
                    InfoRow(label: "\(AppointmentLogic.dateText(a.start)), \(AppointmentLogic.weekday(a.start)) · \(AppointmentLogic.timeText(a.start))\(status)",
                            value: a.title.isBlank ? "Запись" : a.title, icon: "calendar")
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func photosCard(_ p: Person) -> some View {
        SectionCard(title: "Фото (\(p.photos.count))", icon: "photo.on.rectangle", action: {
            Menu {
                PhotosPicker(selection: $photoItems, maxSelectionCount: 20, matching: .images) { Label("Из галереи", systemImage: "photo") }
                Button { showCamera = true } label: { Label("Сделать снимок", systemImage: "camera") }
            } label: { Image(systemName: "plus") }
        }) {
            if p.photos.isEmpty {
                Text("Добавьте фотографии — их увидите только вы").font(.subheadline).foregroundStyle(.secondary)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(p.photos.enumerated()), id: \.element.id) { i, ph in
                            if let img = store.image(ph.file) {
                                Image(uiImage: img).resizable().scaledToFill().frame(width: 100, height: 100)
                                    .clipShape(RoundedRectangle(cornerRadius: 14)).onTapGesture { viewerIndex = i }
                            }
                        }
                    }
                }
            }
        }
    }

    private func journalCard(_ p: Person) -> some View {
        SectionCard(title: "Хроника", icon: "clock.arrow.circlepath", action: {
            Button { showJournal = true } label: { Image(systemName: "plus") }
        }) {
            if p.journal.isEmpty {
                Text("Встречи, звонки, важные события — всё, что хочется помнить").font(.subheadline).foregroundStyle(.secondary)
            }
            ForEach(p.journal.sorted { $0.date > $1.date }) { e in
                HStack(alignment: .top, spacing: 12) {
                    Circle().fill(Color.brand).frame(width: 9, height: 9).padding(.top, 6)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(e.kind).font(.caption2.weight(.semibold)).padding(.horizontal, 8).padding(.vertical, 2)
                                .background(Color.brand.opacity(0.14), in: Capsule())
                            Text(e.date.formatted(date: .long, time: .omitted)).font(.caption).foregroundStyle(.secondary)
                        }
                        Text(e.text)
                    }
                    Spacer()
                }
                .contextMenu {
                    Button(role: .destructive) { store.update(p.id) { $0.journal.removeAll { $0.id == e.id } } } label: { Label("Удалить запись", systemImage: "trash") }
                }
            }
        }
    }
}

struct IndexBox: Identifiable {
    let value: Int
    var id: Int { value }
}

struct JournalSheet: View {
    let personId: UUID
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var kind = DetailTemplates.journalKinds[0]
    @State private var text = ""
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    FlowLayout(spacing: 6) {
                        ForEach(DetailTemplates.journalKinds, id: \.self) { k in Chip(title: k, selected: kind == k) { kind = k } }
                    }
                    DatePicker("Дата", selection: $date, displayedComponents: .date)
                }
                Section("Что произошло") { TextField("Текст", text: $text, axis: .vertical).lineLimit(4...10) }
            }
            .navigationTitle("Запись в хронику").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") { store.addJournal(personId, kind: kind, text: text.trimmed, date: date); dismiss() }.disabled(text.isBlank)
                }
            }
        }
    }
}

struct AddRelationSheet: View {
    let me: Person
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var other: Person?
    @State private var type: RelationType?

    var body: some View {
        NavigationStack {
            Group {
                if let o = other {
                    Form {
                        Section("Семья") {
                            FlowLayout(spacing: 6) {
                                ForEach(RelationType.allCases.filter(\.family)) { t in Chip(title: t.label(gender: o.gender), selected: type == t) { type = t } }
                            }
                        }
                        Section("Другое") {
                            FlowLayout(spacing: 6) {
                                ForEach(RelationType.allCases.filter { !$0.family }) { t in Chip(title: t.label(gender: o.gender), selected: type == t) { type = t } }
                            }
                        }
                        if let t = type {
                            Text("\(o.displayName) — \(t.label(gender: o.gender).lowercased()) для \(me.displayName), а \(me.displayName) — \(t.inverse.label(gender: me.gender).lowercased()) для \(o.displayName)")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .navigationTitle("\(o.displayName) — это…")
                } else {
                    let linked = Set(Relations.view(for: me.id, relations: store.relations,
                                                    people: Dictionary(uniqueKeysWithValues: store.people.map { ($0.id, $0) })).map(\.other.id))
                    let candidates = store.people.filter { $0.id != me.id && !linked.contains($0.id) }
                    List(ArchiveLogic.sort(ArchiveLogic.search(candidates, query: query), mode: .name)) { hit in
                        Button { other = hit.person } label: { PersonRow(person: hit.person) }.buttonStyle(.plain)
                    }
                    .searchable(text: $query)
                    .navigationTitle("С кем связан(а)?")
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(other == nil ? "Отмена" : "Назад") { if other != nil { other = nil; type = nil } else { dismiss() } }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Связать") {
                        if let o = other, let t = type { store.addRelation(me.id, o.id, t) }
                        dismiss()
                    }
                    .disabled(other == nil || type == nil)
                }
            }
        }
    }
}

/// Съёмка камерой.
struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let vc = UIImagePickerController()
        vc.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage { parent.onImage(img) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) { parent.dismiss() }
    }
}

struct PhotoViewer: View {
    let personId: UUID
    @State var start: Int
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var captionEdit: Photo?
    @State private var captionText = ""

    var body: some View {
        let photos = store.person(personId)?.photos ?? []
        NavigationStack {
            TabView(selection: $start) {
                ForEach(Array(photos.enumerated()), id: \.element.id) { i, ph in
                    ZoomableImage(image: store.image(ph.file)).tag(i)
                        .overlay(alignment: .bottom) {
                            if !ph.caption.isBlank {
                                Text(ph.caption).padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10)).padding(.bottom, 40)
                            }
                        }
                }
            }
            .tabViewStyle(.page)
            .background(Color.black.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } }
                ToolbarItemGroup(placement: .bottomBar) {
                    if photos.indices.contains(start) {
                        let ph = photos[start]
                        Button { store.update(personId) { $0.avatar = ph.file } } label: { Label("Главное", systemImage: "person.crop.circle") }
                        Spacer()
                        Button { captionText = ph.caption; captionEdit = ph } label: { Label("Подпись", systemImage: "text.bubble") }
                        Spacer()
                        Button(role: .destructive) {
                            store.deletePhoto(ph, of: personId)
                            if store.person(personId)?.photos.isEmpty ?? true { dismiss() }
                        } label: { Label("Удалить", systemImage: "trash") }
                    }
                }
            }
            .alert("Подпись к фото", isPresented: Binding(get: { captionEdit != nil }, set: { if !$0 { captionEdit = nil } })) {
                TextField("Где, когда, с кем…", text: $captionText)
                Button("Сохранить") {
                    if let ph = captionEdit {
                        store.update(personId) { p in
                            if let i = p.photos.firstIndex(where: { $0.id == ph.id }) { p.photos[i].caption = captionText }
                        }
                    }
                }
                Button("Отмена", role: .cancel) {}
            }
        }
    }
}

struct ZoomableImage: View {
    let image: UIImage?
    @State private var scale: CGFloat = 1

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFit()
                    .scaleEffect(scale)
                    .gesture(MagnificationGesture().onChanged { scale = max(1, min(5, $0)) }.onEnded { _ in if scale < 1.1 { scale = 1 } })
                    .onTapGesture(count: 2) { withAnimation { scale = scale > 1 ? 1 : 2.5 } }
            } else {
                Color.black
            }
        }
    }
}
