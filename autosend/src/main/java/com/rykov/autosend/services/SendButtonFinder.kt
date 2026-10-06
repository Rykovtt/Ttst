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

    fun find(root: AccessibilityNodeInfo, app: TargetApp): AccessibilityNodeInfo? {
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
