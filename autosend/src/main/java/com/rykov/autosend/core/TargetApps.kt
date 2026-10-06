package com.rykov.autosend.core

/** Мессенджер, в котором служба умеет нажимать «Отправить». */
data class TargetApp(
    val packageName: String,
    /** Основной сценарий: идентификаторы ресурса кнопки отправки. */
    val sendViewIds: List<String>,
)

object TargetApps {
    val all: List<TargetApp> = listOf(
        TargetApp("com.whatsapp", listOf("com.whatsapp:id/send")),
        TargetApp("com.whatsapp.w4b", listOf("com.whatsapp.w4b:id/send")),
        // Telegram рисует интерфейс своими View без resource-id: только резервный поиск по описанию.
        TargetApp("org.telegram.messenger", emptyList()),
        TargetApp("org.telegram.messenger.web", emptyList()),
    )

    /** Должен совпадать с android:packageNames в accessibility_service_config.xml. */
    val packageNames: Set<String> = all.mapTo(linkedSetOf()) { it.packageName }

    fun forPackage(packageName: String?): TargetApp? = all.firstOrNull { it.packageName == packageName }
}

/** Резервный сценарий: подписи кнопки (contentDescription или текст). */
object SendLabels {
    /** Строки для findAccessibilityNodeInfosByText — система ищет вхождение без учёта регистра. */
    val queries: List<String> = listOf("Отправить", "Send", "Надіслати")

    private val exact: Set<String> = setOf("отправить", "send", "надіслати", "send message", "отправить сообщение")

    /**
     * Точное совпадение подписи: findAccessibilityNodeInfosByText находит и сообщения
     * в переписке, содержащие слово «send», — их нажимать нельзя.
     */
    fun matches(label: CharSequence?): Boolean {
        if (label.isNullOrBlank()) return false
        return label.toString().trim().lowercase() in exact
    }
}
