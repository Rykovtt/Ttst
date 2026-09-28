import Foundation

/// Тип связи: «related — это X для person». У каждого типа есть обратный.
enum RelationType: String, Codable, CaseIterable, Identifiable {
    case spouse = "SPOUSE", partner = "PARTNER", parent = "PARENT", child = "CHILD", sibling = "SIBLING"
    case grandparent = "GRANDPARENT", grandchild = "GRANDCHILD", uncle = "UNCLE", nephew = "NEPHEW", cousin = "COUSIN"
    case parentInLaw = "PARENT_IN_LAW", childInLaw = "CHILD_IN_LAW", godparent = "GODPARENT", godchild = "GODCHILD"
    case ex = "EX", friend = "FRIEND", colleague = "COLLEAGUE", boss = "BOSS", subordinate = "SUBORDINATE", other = "OTHER"

    var id: String { rawValue }

    private var names: (String, String, String, Bool) {
        switch self {
        case .spouse: return ("Муж", "Жена", "Супруг(а)", true)
        case .partner: return ("Партнёр", "Партнёрша", "Партнёр", true)
        case .parent: return ("Отец", "Мать", "Родитель", true)
        case .child: return ("Сын", "Дочь", "Ребёнок", true)
        case .sibling: return ("Брат", "Сестра", "Брат/сестра", true)
        case .grandparent: return ("Дедушка", "Бабушка", "Бабушка/дедушка", true)
        case .grandchild: return ("Внук", "Внучка", "Внук/внучка", true)
        case .uncle: return ("Дядя", "Тётя", "Дядя/тётя", true)
        case .nephew: return ("Племянник", "Племянница", "Племянник(ца)", true)
        case .cousin: return ("Двоюродный брат", "Двоюродная сестра", "Двоюродный брат/сестра", true)
        case .parentInLaw: return ("Тесть / свёкор", "Тёща / свекровь", "Родитель супруга", true)
        case .childInLaw: return ("Зять", "Невестка", "Зять/невестка", true)
        case .godparent: return ("Крёстный", "Крёстная", "Крёстный(ая)", true)
        case .godchild: return ("Крестник", "Крестница", "Крестник(ца)", true)
        case .ex: return ("Бывший", "Бывшая", "Бывший(ая)", false)
        case .friend: return ("Друг", "Подруга", "Друг", false)
        case .colleague: return ("Коллега", "Коллега", "Коллега", false)
        case .boss: return ("Начальник", "Начальница", "Руководитель", false)
        case .subordinate: return ("Подчинённый", "Подчинённая", "Подчинённый(ая)", false)
        case .other: return ("Знакомый", "Знакомая", "Связь", false)
        }
    }

    var family: Bool { names.3 }

    var inverse: RelationType {
        switch self {
        case .parent: return .child
        case .child: return .parent
        case .grandparent: return .grandchild
        case .grandchild: return .grandparent
        case .uncle: return .nephew
        case .nephew: return .uncle
        case .parentInLaw: return .childInLaw
        case .childInLaw: return .parentInLaw
        case .godparent: return .godchild
        case .godchild: return .godparent
        case .boss: return .subordinate
        case .subordinate: return .boss
        default: return self
        }
    }

    func label(gender: String) -> String {
        switch gender {
        case "Мужской": return names.0
        case "Женский": return names.1
        default: return names.2
        }
    }
}

/// Связь с точки зрения конкретного человека: «other — label для меня».
struct RelationView: Identifiable, Hashable {
    var relation: Relation
    var other: Person
    var type: RelationType
    var id: UUID { relation.id }
    var label: String { type.label(gender: other.gender) }
}

enum Relations {
    static func view(for personId: UUID, relations: [Relation], people: [UUID: Person]) -> [RelationView] {
        relations.compactMap { r -> RelationView? in
            if r.personId == personId, let o = people[r.relatedId] { return RelationView(relation: r, other: o, type: r.type) }
            if r.relatedId == personId, let o = people[r.personId] { return RelationView(relation: r, other: o, type: r.type.inverse) }
            return nil
        }.sorted {
            if $0.type.family != $1.type.family { return $0.type.family }
            let i0 = RelationType.allCases.firstIndex(of: $0.type)!, i1 = RelationType.allCases.firstIndex(of: $1.type)!
            if i0 != i1 { return i0 < i1 }
            return $0.other.sortKey < $1.other.sortKey
        }
    }
}
