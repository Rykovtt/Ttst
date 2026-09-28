package com.kartoteka.app.data

/**
 * Тип связи: «related — это X для person». У каждого типа есть обратный:
 * если Мария — мать Ивана (PARENT), то Иван — сын Марии (CHILD).
 * Подпись зависит от пола: Отец/Мать, Сын/Дочь и т.д.
 */
enum class RelationType(
    val male: String,
    val female: String,
    val neutral: String,
    val family: Boolean,
) {
    SPOUSE("Муж", "Жена", "Супруг(а)", true),
    PARTNER("Партнёр", "Партнёрша", "Партнёр", true),
    PARENT("Отец", "Мать", "Родитель", true),
    CHILD("Сын", "Дочь", "Ребёнок", true),
    SIBLING("Брат", "Сестра", "Брат/сестра", true),
    GRANDPARENT("Дедушка", "Бабушка", "Бабушка/дедушка", true),
    GRANDCHILD("Внук", "Внучка", "Внук/внучка", true),
    UNCLE("Дядя", "Тётя", "Дядя/тётя", true),
    NEPHEW("Племянник", "Племянница", "Племянник(ца)", true),
    COUSIN("Двоюродный брат", "Двоюродная сестра", "Двоюродный брат/сестра", true),
    PARENT_IN_LAW("Тесть / свёкор", "Тёща / свекровь", "Родитель супруга", true),
    CHILD_IN_LAW("Зять", "Невестка", "Зять/невестка", true),
    GODPARENT("Крёстный", "Крёстная", "Крёстный(ая)", true),
    GODCHILD("Крестник", "Крестница", "Крестник(ца)", true),
    EX("Бывший", "Бывшая", "Бывший(ая)", false),
    FRIEND("Друг", "Подруга", "Друг", false),
    COLLEAGUE("Коллега", "Коллега", "Коллега", false),
    BOSS("Начальник", "Начальница", "Руководитель", false),
    SUBORDINATE("Подчинённый", "Подчинённая", "Подчинённый(ая)", false),
    OTHER("Знакомый", "Знакомая", "Связь", false);

    val inverse: RelationType
        get() = when (this) {
            PARENT -> CHILD
            CHILD -> PARENT
            GRANDPARENT -> GRANDCHILD
            GRANDCHILD -> GRANDPARENT
            UNCLE -> NEPHEW
            NEPHEW -> UNCLE
            PARENT_IN_LAW -> CHILD_IN_LAW
            CHILD_IN_LAW -> PARENT_IN_LAW
            GODPARENT -> GODCHILD
            GODCHILD -> GODPARENT
            BOSS -> SUBORDINATE
            SUBORDINATE -> BOSS
            else -> this
        }

    fun label(gender: String): String = when (gender) {
        "Мужской" -> male
        "Женский" -> female
        else -> neutral
    }

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Связь с точки зрения конкретного человека: «[other] — [label] для меня». */
data class RelationView(val relation: Relation, val other: Person, val type: RelationType) {
    val label: String get() = type.label(other.gender)
}

object Relations {
    /** Все связи человека [personId] в его перспективе, семья сначала. */
    fun viewFor(personId: Long, relations: List<Relation>, people: Map<Long, Person>): List<RelationView> =
        relations.mapNotNull { r ->
            when (personId) {
                r.personId -> people[r.relatedId]?.let { RelationView(r, it, RelationType.of(r.type)) }
                r.relatedId -> people[r.personId]?.let { RelationView(r, it, RelationType.of(r.type).inverse) }
                else -> null
            }
        }.sortedWith(compareBy<RelationView>({ !it.type.family }, { it.type.ordinal }, { it.other.sortKey }))
}
