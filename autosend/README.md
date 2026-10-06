# Автоотправка CRM (модуль `:autosend`)

Отдельное приложение со службой специальных возможностей (`AccessibilityService`), которая
по команде RVault открывает чат в WhatsApp / WhatsApp Business / Telegram / Viber и
**один раз** нажимает «Отправить». Без команды служба ничего не нажимает.

## Как это работает

1. RVault (рассылка, напоминание, «Отправить» в записи) шлёт команду `ACTION_ARM_SEND`
   со ссылкой на чат и текстом.
2. Служба проверяет отправителя: создатель PendingIntent из команды должен быть
   `com.rykov.rvault`, подписанным известным сертификатом (`core/TrustedCallers.kt`).
3. Служба сама открывает чат (RVault в фоне не может открывать окна на Android 10+,
   а служба специальных возможностей может).
4. Через 400 мс после события окна ищет кнопку:
   - по `resource-id` (`com.whatsapp:id/send`, `com.whatsapp.w4b:id/send`, кнопки Viber);
   - запасной вариант — по `contentDescription`/тексту «Отправить» / «Send» / «Надіслати»
     (точное совпадение, чтобы не нажать на сообщение со словом «send»).
   Если мессенджер не подставил текст по ссылке (Telegram по номеру, Viber), служба
   один раз вписывает текст в пустое поле ввода.
   Некликабельная кнопка → поиск кликабельного родителя (до 4 уровней).
5. Команда снимается **до** клика — повторного нажатия нет. Не нашлась кнопка за время
   команды → `timeout`.
6. Итог уходит обратно через PendingIntent RVault.

Служба не сохраняет и не передаёт переписку, контакты и текст; у приложения нет `INTERNET`.

## Протокол

Константы — `core/AutoSendContract.kt` (копия для клиента — `AutoSendLink` в RVault).

| Extra | Тип | |
|---|---|---|
| `callback` | PendingIntent (MUTABLE, явный адресат) | обязательно: кто прислал и куда ответить |
| `target_package` | String | мессенджер (`com.whatsapp`, `com.viber.voip`, …) |
| `open_uri` | String | ссылка на чат; служба откроет её в `target_package` |
| `text` | String | вписать в пустое поле ввода, если ссылка не подставила текст |
| `request_id` | String | вернётся в ответе |
| `timeout_ms` | Long | 3000..60000, по умолчанию 15000 |

Ответ: `status` = `sent` · `click_failed` · `timeout` · `cancelled` · `service_disabled` ·
`unsupported_package` · `open_failed` · `untrusted_caller`, плюс `request_id`, `target_package`.

## Доверенные приложения

Сертификат RVault (SHA-256) записан в `core/TrustedCallers.kt`. При смене ключа RVault
его нужно обновить: `apksigner verify --print-certs app-release.apk`.
Само приложение (экран «Проверка») доверено всегда.

## Подпись

`autosend/keystore.properties` (в git не попадает): `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`. Без файла — debug-ключ. Обновление поверх установленной версии возможно
только с тем же ключом.

## Сборка и тесты

```
./gradlew :autosend:testDebugUnitTest :autosend:assembleRelease
```

## Ограничения

- Google Play допускает такие службы только с декларацией; приложение рассчитано на
  установку вне Play.
- Правила WhatsApp и Viber запрещают массовые автоматические рассылки — ответственность
  за сценарий использования на операторе CRM.
- Идентификаторы и подписи кнопок меняются с обновлениями мессенджеров (`core/TargetApps.kt`).
  Viber на реальном устройстве не проверялся.
