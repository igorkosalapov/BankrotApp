# BankrotApp

## Автоматическая очистка DOCX-шаблонов

В проект добавлены 2 CLI-инструмента, которые позволяют не редактировать DOCX вручную.

## Рекомендуемый запуск (работает из любой текущей папки)

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

> Скрипты сами вычисляют абсолютный путь до `pom.xml`, поэтому не зависят от текущей директории запуска.

## Альтернатива: запуск напрямую через Maven

Если вы запускаете команду из корня репозитория:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool
```

Если запускаете не из корня, укажите абсолютный путь до `pom.xml` через `-f`.

## Что делает очистка

- программно пересоздаёт чистые шаблоны без legacy-персональных данных;
- сохраняет файлы в `src/main/resources/templates/`:
  - `zayavlenie.docx`
  - `prilozhenie_1.docx`
  - `prilozhenie_2.docx`

## Что проверяет аудит

- открывает каждый DOCX как zip;
- читает `word/*.xml`;
- убеждается, что не осталось запрещённых маркеров:
  - `Захаров`
  - `ВЭББАНКИР`
  - `ТУРБОЗАЙМ`
  - `МИГКРЕДИТ`
  - `MITSUBISHI RVR`
  - `1 248 887,93`

## Проверка генерации для клиента «Иванов Сергей Николаевич»

```bash
mvn -Dtest=DocumentGenerationServiceTest#testAppendix1SignatureUsesCurrentDebtor,testAppendix2SignatureUsesCurrentDebtor test
```
