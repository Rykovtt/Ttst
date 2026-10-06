# Автоотправка CRM (модуль `:autosend`)

Отдельное приложение со службой специальных возможностей (`AccessibilityService`), которая
нажимает «Отправить» в WhatsApp / WhatsApp Business / Telegram **только по команде CRM** и
**не более одного раза на команду**.

## Как это работает

1. CRM подставляет текст и открывает чат (например, `https://wa.me/<номер>?text=...`).
2. CRM отправляет команду `ACTION_ARM_SEND` (см. ниже).
3. Служба ждёт событий `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` целевого пакета,
   выжидает 400 мс (анимация открытия чата) и ищет кнопку:
   - по `resource-id` (`com.whatsapp:id/send`, `com.whatsapp.w4b:id/send`);
   - запасной вариант — по `contentDescription`/тексту «Отправить» / «Send» / «Надіслати»
     (точное совпадение, чтобы не нажать на сообщение со словом «send»).
   Если кнопка сама не кликабельна, служба поднимается к кликабельному родителю (до 4 уровней).
4. Команда снимается **до** клика — повторного нажатия не бывает. Если кнопка не появилась
   за время команды (по умолчанию 15 с), команда истекает со статусом `timeout`.
5. Итог возвращается CRM широковещательным `ACTION_SEND_RESULT`.

Без взведённой команды служба ничего не нажимает. Текст сообщений, контакты и переписка не
читаются, не сохраняются и не передаются; у приложения нет разрешения `INTERNET`.

## Протокол для CRM

Константы — в `core/AutoSendContract.kt`. Команды защищены разрешением
`com.rykov.autosend.permission.CONTROL` с `protectionLevel="signature"`: **CRM должна быть
подписана тем же ключом**, что и это приложение, и объявить у себя:

```xml
<uses-permission android:name="com.rykov.autosend.permission.CONTROL" />
<!-- Android 11+: видимость пакета -->
<queries><package android:name="com.rykov.autosend" /></queries>
```

Взвести (широковещательный Intent должен быть явным — с `setPackage`):

```kotlin
context.sendBroadcast(
    Intent("com.rykov.autosend.action.ARM_SEND")
        .setPackage("com.rykov.autosend")
        .putExtra("target_package", "com.whatsapp")   // необязательно
        .putExtra("request_id", "msg-42")             // необязательно, вернётся в ответе
        .putExtra("reply_package", context.packageName) // без него ответа не будет
        .putExtra("timeout_ms", 15_000L)              // 3000..60000
)
```

Отменить: `Intent("com.rykov.autosend.action.CANCEL").setPackage("com.rykov.autosend")`.

Ответ (`com.rykov.autosend.action.SEND_RESULT`, адресован пакету `reply_package`) содержит
`status`, `request_id`, `target_package`. Статусы: `sent`, `click_failed`, `timeout`,
`cancelled`, `service_disabled`, `unsupported_package`.

## Подпись

Положите рядом `autosend/keystore.properties` (в git не попадает):

```properties
storeFile=/путь/к/crm.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

Без файла release-сборка подписывается debug-ключом (для проверки на своём устройстве).

## Сборка и тесты

```
./gradlew :autosend:testDebugUnitTest :autosend:assembleRelease
```

## Ограничения

- Google Play допускает AccessibilityService не для людей с ограничениями только с декларацией
  и явным раскрытием; `isAccessibilityTool` здесь не ставится. Модуль рассчитан на установку
  вне Play (корпоративные устройства).
- Правила WhatsApp запрещают автоматизацию массовых рассылок — служба делает одно нажатие
  на одно действие оператора, но ответственность за сценарий использования на CRM.
- Идентификаторы и подписи кнопок могут меняться с обновлениями мессенджеров —
  список в `core/TargetApps.kt`.
