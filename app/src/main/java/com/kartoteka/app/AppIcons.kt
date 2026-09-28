package com.kartoteka.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Значок и название на рабочем столе. Каждый вариант — activity-alias в манифесте;
 * включён ровно один. Маскировочные варианты помогают сохранить архив в тайне.
 */
enum class AppIcon(
    val alias: String,
    @StringRes val label: Int,
    @DrawableRes val foreground: Int,
    val bgStart: Long,
    val bgEnd: Long,
    val disguise: Boolean,
    /** Готовая картинка значка целиком (для основного варианта). */
    @DrawableRes val fullImage: Int? = null,
) {
    DEFAULT("Default", R.string.app_name, R.drawable.rvault_mono, 0xFF1B6FA0, 0xFF12305F, false, R.drawable.rvault_bg),
    DARK("Dark", R.string.app_name, R.drawable.rvault_mono, 0xFF2B2B33, 0xFF0D0D12, false),
    NOTES("Notes", R.string.alias_notes, R.drawable.icfg_notes, 0xFFFFD54F, 0xFFFFB300, true),
    CALC("Calc", R.string.alias_calc, R.drawable.icfg_calc, 0xFF455A64, 0xFF263238, true),
    WEATHER("Weather", R.string.alias_weather, R.drawable.icfg_weather, 0xFF4FC3F7, 0xFF1E88E5, true),
    ORGANIZER("Organizer", R.string.alias_organizer, R.drawable.icfg_calendar, 0xFFEF5350, 0xFFC62828, true),
    FILES("Files", R.string.alias_files, R.drawable.icfg_files, 0xFF5C6BC0, 0xFF3949AB, true),
    CONTACTS("Contacts", R.string.alias_contacts, R.drawable.icfg_contacts, 0xFF26A69A, 0xFF00796B, true);

    fun component(context: Context) = ComponentName(context.packageName, "com.kartoteka.app.alias.$alias")
}

object AppIcons {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(icon.component(context))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.DEFAULT
                else -> false
            }
        } ?: AppIcon.DEFAULT
    }

    /** Название в шапке, на экране блокировки и в списке недавних приложений. */
    fun title(context: Context, custom: String): String =
        custom.trim().ifBlank { context.getString(current(context).label) }

    /** Значок для уведомлений — под текущую маскировку. */
    fun notificationIcon(context: Context): Int = current(context).foreground

    /** Сначала включаем новый вариант, потом выключаем остальные — чтобы значок не пропал совсем. */
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
