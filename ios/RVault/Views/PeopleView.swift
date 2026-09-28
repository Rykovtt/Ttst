import SwiftUI

enum PeopleFilter: Hashable {
    case all, favorites, group(UUID)
}

struct PeopleView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router
    @AppStorage(Prefs.sort) private var sortRaw = SortMode.name.rawValue
    @AppStorage(Prefs.appTitle) private var appTitle = ""
    @State private var query = ""
    @State private var filter = PeopleFilter.all
    @State private var path = NavigationPath()
    @State private var editing: Person?
    @State private var showGroups = false
    @State private var showImport = false

    private var sort: SortMode { SortMode(rawValue: sortRaw) ?? .name }

    private var results: [ArchiveLogic.SearchHit] {
        let base: [Person]
        switch filter {
        case .all: base = store.people
        case .favorites: base = store.people.filter(\.favorite)
        case .group(let id): base = store.people.filter { $0.groupIds.contains(id) }
        }
        return ArchiveLogic.sort(ArchiveLogic.search(base, query: query, groups: store.groups), mode: sort)
    }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    filterRow
                    if query.isEmpty, filter == .all {
                        let bds = ArchiveLogic.upcomingBirthdays(store.people, within: 30)
                        if !bds.isEmpty { birthdayStrip(bds) }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))

                if store.people.isEmpty {
                    EmptyStateView(icon: "person.crop.rectangle.stack", title: "Пока никого нет",
                                   text: "Добавьте первого человека вручную или импортируйте контакты iPhone.")
                        .listRowBackground(Color.clear)
                    Button("Импорт из контактов") { showImport = true }
                } else if results.isEmpty {
                    EmptyStateView(icon: "magnifyingglass", title: "Ничего не найдено", text: "Попробуйте изменить запрос или фильтр.")
                        .listRowBackground(Color.clear)
                } else if sort == .name && query.isEmpty {
                    let grouped = Dictionary(grouping: results) { hit in
                        hit.person.sortKey.first.map { String($0).uppercased() } ?? "#"
                    }
                    ForEach(grouped.keys.sorted(), id: \.self) { letter in
                        Section(letter) {
                            ForEach(grouped[letter]!) { hit in row(hit) }
                        }
                    }
                } else {
                    Section("Найдено: \(results.count)") {
                        ForEach(results) { hit in row(hit) }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(appTitle.isBlank ? "RVault" : appTitle)
            .searchable(text: $query, prompt: "Поиск: имя, хобби, город…")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showGroups = true } label: { Image(systemName: "circle.grid.3x3.fill") }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Picker("Сортировка", selection: $sortRaw) {
                            ForEach(SortMode.allCases) { Text($0.title).tag($0.rawValue) }
                        }
                    } label: { Image(systemName: "arrow.up.arrow.down") }
                    Button { editing = Person() } label: { Image(systemName: "person.badge.plus") }
                }
            }
            .navigationDestination(for: UUID.self) { id in PersonDetailView(personId: id) }
            .sheet(item: $editing) { p in
                PersonEditView(person: p) { saved in path.append(saved.id) }
            }
            .sheet(isPresented: $showGroups) { GroupsView() }
            .sheet(isPresented: $showImport) { ImportContactsView() }
            .onChange(of: router.openPerson) { _, id in
                if let id { path.append(id); router.openPerson = nil }
            }
        }
    }

    private var filterRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Chip(title: "Все", selected: filter == .all) { filter = .all }
                Chip(title: "Избранные", selected: filter == .favorites, systemImage: "star.fill") { filter = .favorites }
                ForEach(store.groups) { g in
                    Chip(title: g.title, selected: filter == .group(g.id), color: Color(hex: g.color)) {
                        filter = filter == .group(g.id) ? .all : .group(g.id)
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func birthdayStrip(_ list: [(Person, Int)]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Скоро дни рождения", systemImage: "birthday.cake.fill").font(.headline).foregroundStyle(Color.accent2)
                .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(list.indices, id: \.self) { i in
                        let p = list[i].0
                        let days = list[i].1
                        Button { path.append(p.id) } label: {
                            VStack(spacing: 4) {
                                AvatarView(person: p, size: 56)
                                Text(p.firstName.isBlank ? p.displayName : p.firstName).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text(ArchiveLogic.daysString(days)).font(.caption).foregroundStyle(Color.accent2)
                                if let age = ArchiveLogic.turningAge(p) {
                                    Text(ArchiveLogic.ageString(age)).font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                            .frame(width: 110).padding(.vertical, 12)
                            .background(days == 0 ? Color.accent2.opacity(0.15) : Color.cardBG, in: RoundedRectangle(cornerRadius: 20))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func row(_ hit: ArchiveLogic.SearchHit) -> some View {
        NavigationLink(value: hit.person.id) { PersonRow(person: hit.person, matchedIn: hit.matchedIn) }
    }
}

struct PersonRow: View {
    let person: Person
    var matchedIn: String? = nil
    @EnvironmentObject private var store: Store

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(person: person, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(person.displayName).font(.headline).lineLimit(1)
                    if person.favorite { Image(systemName: "star.fill").font(.caption).foregroundStyle(Color.accent2) }
                    ForEach(store.groups(of: person).prefix(4)) { g in Circle().fill(Color(hex: g.color)).frame(width: 7, height: 7) }
                }
                if let m = matchedIn {
                    Text(m).font(.caption).foregroundStyle(Color.brand).lineLimit(1)
                } else {
                    let sub = [person.relation, person.company.isBlank ? person.position : person.company, person.city].filter { !$0.isBlank }.joined(separator: " · ")
                    if !sub.isEmpty { Text(sub).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                }
            }
            Spacer(minLength: 0)
            if let d = ArchiveLogic.daysUntilBirthday(person), d <= 7 { Badge(text: "🎂 " + ArchiveLogic.daysString(d)) }
        }
    }
}
