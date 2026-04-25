# DOCX template cleanup checklist

Этот файл описывает ручные действия по очистке DOCX-шаблонов:

- `src/main/resources/templates/zayavlenie.docx`
- `src/main/resources/templates/prilozhenie_1.docx`
- `src/main/resources/templates/prilozhenie_2.docx`

## 1) Какие старые данные нужно удалить из шаблонов

Из DOCX нужно полностью удалить (или заменить на placeholders) любые реальные sample-данные предыдущего клиента, включая:

- ФИО:
  - `Захаров`
  - `Захаров Владимир Игоревич`
  - краткие формы (`Захаров В. И.` и т.п.)
- Старые кредиторы:
  - `ВЭББАНКИР`
  - `ТУРБОЗАЙМ`
  - `МИГКРЕДИТ`
- Старые суммы:
  - `1 248 887,93`
- Транспорт:
  - `MITSUBISHI RVR`
- Старые адреса (Челябинск/пр. Победы/кв.44 и связанные фрагменты)
- Старые паспортные данные
- Старые ИНН/СНИЛС
- Старые данные супруги/ребёнка
- Старые даты
- Старые договоры/номера договоров

## 2) Какие placeholders должны быть в шаблонах

Минимальный набор placeholders, который поддерживается кодом:

- `{{debtor.fullName}}`
- `{{debtor.lastName}}`
- `{{debtor.firstName}}`
- `{{debtor.middleName}}`
- `{{debtor.shortName}}`
- `{{debtor.birthDate}}`
- `{{debtor.birthPlace}}`
- `{{debtor.passportNumber}}`
- `{{debtor.inn}}`
- `{{debtor.snils}}`
- `{{debtor.registrationAddress.fullAddress}}`
- `{{debtor.registrationAddress.region}}`
- `{{debtor.registrationAddress.city}}`
- `{{debtor.registrationAddress.street}}`
- `{{debtor.registrationAddress.postalCode}}`
- `{{vehicle.primaryLabel}}`
- `{{creditor.sample1}}`
- `{{creditor.sample2}}`
- `{{creditor.sample3}}`
- `{{totalDebtFormatted}}`

## 3) Строки, которые запрещено оставлять

Ни в одном `word/*.xml` внутри DOCX не должно остаться:

- `Захаров`
- `ВЭББАНКИР`
- `ТУРБОЗАЙМ`
- `МИГКРЕДИТ`
- `MITSUBISHI RVR`
- `1 248 887,93`

## 4) Как проверить шаблоны после ручного редактирования

После ручной правки DOCX:

1. Запустить тест `testTemplatesDoNotContainRealSampleData` (снять `@Disabled`).
2. Выполнить `mvn clean test`.
3. Дополнительно (опционально) проверить шаблоны скриптом:

```bash
python - <<'PY'
import zipfile
targets = [
    "src/main/resources/templates/zayavlenie.docx",
    "src/main/resources/templates/prilozhenie_1.docx",
    "src/main/resources/templates/prilozhenie_2.docx",
]
forbidden = ["Захаров","ВЭББАНКИР","ТУРБОЗАЙМ","МИГКРЕДИТ","MITSUBISHI RVR","1 248 887,93"]
for path in targets:
    z = zipfile.ZipFile(path)
    xml = "\\n".join(
        z.read(name).decode("utf-8", "ignore")
        for name in z.namelist()
        if name.startswith("word/") and name.endswith(".xml")
    )
    for marker in forbidden:
        if marker in xml:
            raise SystemExit(f"{path}: found forbidden marker: {marker}")
print("OK: no forbidden markers in templates")
PY
```
