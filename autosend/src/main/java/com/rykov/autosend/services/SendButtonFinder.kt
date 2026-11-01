package com.rykov.autosend.services

import android.view.accessibility.AccessibilityNodeInfo
import com.rykov.autosend.core.SendLabels
import com.rykov.autosend.core.TargetApp

/**
 * Поиск кнопки «Отправить» в дереве узлов.
 *
 * Возвращает кликабельный узел (саму кнопку или её кликабельного родителя);
 * вызывающий обязан освободить его через [recycleSafely]. Все прочие полученные
 * узлы освобождаются здесь же. Текст узлов сравнивается только с подписями кнопки
 * и никуда не сохраняется.
 */
internal object SendButtonFinder {
    /** Насколько высоко подниматься к кликабельному родителю (FrameLayout и т.п.). */
    private const val MAX_PARENT_DEPTH = 4
    private const val MAX_TREE_DEPTH = 40

    fun find(root: AccessibilityNodeInfo, app: TargetApp, expectedText: String? = null): AccessibilityNodeInfo? {
        // Основной сценарий: по идентификатору ресурса.
        for (viewId in app.sendViewIds) {
            firstClickable(root.findAccessibilityNodeInfosByViewId(viewId)) { true }?.let { return it }
        }
        // Резервный сценарий: по contentDescription или тексту.
        for (query in SendLabels.queries) {
            firstClickable(root.findAccessibilityNodeInfosByText(query)) {
                SendLabels.matches(it.contentDescription) || SendLabels.matches(it.text)
            }?.let { return it }
        }
        if (app.rightOfInputFallback) return findRightOfInput(root, expectedText)
        return null
    }

    /**
     * Крайняя правая кликабельная кнопка в строке поля ввода — только если в поле уже стоит текст
     * (и, когда он известен, это начало нашего сообщения). Пустое поле — там микрофон, ничего не нажимаем.
     */
    private fun findRightOfInput(root: AccessibilityNodeInfo, expectedText: String?): AccessibilityNodeInfo? {
        val input = findInput(root) ?: return null
        val row = android.graphics.Rect()
        try {
            val text = input.text?.toString()?.trim().orEmpty()
            if (input.isShowingHintText || text.isEmpty()) return null
            if (!expectedText.isNullOrBlank() && !sameStart(text, expectedText)) return null
            input.getBoundsInScreen(row)
        } finally {
            input.recycleSafely()
        }
        if (row.isEmpty) return null
        var best: AccessibilityNodeInfo? = null
        var bestRight = Int.MIN_VALUE
        val r = android.graphics.Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TREE_DEPTH) return
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                child.getBoundsInScreen(r)
                val inRow = r.centerY() in row.top..row.bottom || row.centerY() in r.top..r.bottom
                if (child.isClickable && child.isEnabled && child.isVisibleToUser && !child.isEditable &&
                    inRow && r.left >= row.right - row.height() / 2 && r.right > bestRight
                ) {
                    best?.recycleSafely()
                    best = child; bestRight = r.right
                    continue        // кнопку не обходим вглубь и не освобождаем — она кандидат
                }
                walk(child, depth + 1)
                child.recycleSafely()
            }
        }
        walk(root, 0)
        return best
    }

    /** Поле начинается с нашего текста (мессенджер мог обрезать пробелы и переносы). */
    internal fun sameStart(field: String, expected: String): Boolean {
        fun norm(s: String) = s.replace(Regex("\\s+"), " ").trim().lowercase()
        val a = norm(field); val b = norm(expected)
        val n = minOf(20, a.length, b.length)
        return n > 0 && a.take(n) == b.take(n)
    }

    /** Поле ввода сообщения: сначала узел с фокусом ввода, затем первый видимый редактируемый. */
    fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let {
            if (it.isEditable && it.isVisibleToUser) return it
            it.recycleSafely()
        }
        return findEditable(root, 0)
    }

    private fun findEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_TREE_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isEditable && child.isVisibleToUser) return child
            val found = findEditable(child, depth + 1)
            child.recycleSafely()
            if (found != null) return found
        }
        return null
    }

    private inline fun firstClickable(
        nodes: List<AccessibilityNodeInfo>?,
        accept: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (nodes.isNullOrEmpty()) return null
        var found: AccessibilityNodeInfo? = null
        for (node in nodes) {
            if (found == null && node.isVisibleToUser && accept(node)) {
                found = clickableSelfOrAncestor(node)
                if (found === node) continue // отдаём вызывающему, не освобождаем
            }
            node.recycleSafely()
        }
        return found
    }

    /** Сам узел, если он кликабелен, иначе ближайший кликабельный родитель (новый экземпляр). */
    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) return node
        var current = node.parent
        var depth = 1
        while (current != null) {
            if (current.isClickable && current.isEnabled) return current
            if (depth >= MAX_PARENT_DEPTH) break
            val next = current.parent
            current.recycleSafely()
            current = next
            depth++
        }
        current?.recycleSafely()
        return null
    }
}

/**
 * recycle() обязателен до Android 13 (иначе утечка пула узлов); с API 33 это no-op.
 * Повторное освобождение на старых версиях бросает IllegalStateException — глушим.
 */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleSafely() {
    try {
        recycle()
    } catch (_: IllegalStateException) {
    }
}
