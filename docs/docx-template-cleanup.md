# DOCX template cleanup

Ручная правка Word-шаблонов больше не требуется.

## Основные команды

1. Пересоздать чистые шаблоны:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool
```

2. Проверить шаблоны на запрещённые legacy-строки:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool
```

## Что делает `TemplateCleanupTool`

- Программно генерирует заново:
  - `src/main/resources/templates/zayavlenie.docx`
  - `src/main/resources/templates/prilozhenie_1.docx`
  - `src/main/resources/templates/prilozhenie_2.docx`
- Использует placeholders вместо персональных данных.
- Сохраняет структуру таблиц приложений №1 и №2 так, чтобы они оставались совместимы с `DocumentGenerationService`.

## Что делает `TemplateAuditTool`

- Открывает каждый DOCX как zip.
- Читает `word/*.xml`.
- Проверяет отсутствие маркеров:
  - `Захаров`
  - `ВЭББАНКИР`
  - `ТУРБОЗАЙМ`
  - `МИГКРЕДИТ`
  - `MITSUBISHI RVR`
  - `1 248 887,93`

## Проверка генерации клиента Иванов Сергей Николаевич

```bash
mvn -Dtest=DocumentGenerationServiceTest#testAppendix1SignatureUsesCurrentDebtor,testAppendix2SignatureUsesCurrentDebtor test
```
