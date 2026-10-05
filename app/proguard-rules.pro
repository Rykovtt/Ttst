# Сжатие только за счёт неиспользуемых иконок (material-icons-extended — тысячи классов, из них нужны десятки).
# Всё остальное сохраняется как есть: без переименования, без оптимизаций, без удаления — поведение не меняется.
-dontobfuscate
-dontoptimize
-keep class !androidx.compose.material.icons.**, ** { *; }
-keepattributes *
-dontwarn **
