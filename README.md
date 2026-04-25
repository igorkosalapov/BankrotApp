# BankrotApp

## Автоматическая очистка DOCX-шаблонов

В проект добавлены 2 CLI-инструмента, которые позволяют не редактировать DOCX вручную.

### 1) Очистка/пересоздание шаблонов

Команда (одна строка):

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool
```

Что делает команда:
- программно пересоздаёт чистые шаблоны без legacy-персональных данных;
- сохраняет файлы в `src/main/resources/templates/`:
  - `zayavlenie.docx`
  - `prilozhenie_1.docx`
  - `prilozhenie_2.docx`

> При необходимости можно передать другой каталог первым аргументом:
>
> ```bash
> mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool -Dexec.args="/tmp/templates"
> ```

### 2) Аудит шаблонов

Команда:

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool
```

Что проверяет аудит:
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

Быстрая команда теста:

```bash
mvn -Dtest=DocumentGenerationServiceTest#testAppendix1SignatureUsesCurrentDebtor,testAppendix2SignatureUsesCurrentDebtor test
```

Опционально: проверить весь набор тестов сервиса генерации:

```bash
mvn -Dtest=DocumentGenerationServiceTest test
```
