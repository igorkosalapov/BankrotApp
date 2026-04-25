# DOCX template cleanup

Ручная правка Word-шаблонов больше не требуется.

## Основные команды

### Windows

```bat
scripts\template-cleanup.cmd
scripts\template-audit.cmd
```

### Linux / macOS

```bash
./scripts/template-cleanup.sh
./scripts/template-audit.sh
```

Скрипты вычисляют путь к `pom.xml` относительно своего расположения, поэтому их можно запускать из любой директории.

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
