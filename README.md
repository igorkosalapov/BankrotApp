# BankrotApp

## Инструменты подготовки и аудита DOCX-шаблонов

В проекте есть два CLI-инструмента для стабилизации шаблонов и проверки на legacy-данные.

### 1) Что делает инструмент подготовки DOCX

Класс: `com.bankrotapp.template.TemplateCleanupTool`.

Инструмент:
- открывает/пересоздаёт шаблоны в `src/main/resources/templates`;
- заменяет/удаляет старые реальные данные;
- создаёт предсказуемые DOCX-шаблоны с placeholder-полями.

Читает/использует путь к папке шаблонов из аргумента командной строки или по умолчанию:
- `src/main/resources/templates`.

Изменяет/создаёт файлы:
- `src/main/resources/templates/zayavlenie.docx`
- `src/main/resources/templates/prilozhenie_1.docx`
- `src/main/resources/templates/prilozhenie_2.docx`

### 2) Что делает инструмент аудита DOCX

Класс: `com.bankrotapp.template.TemplateAuditTool`.

Инструмент:
- открывает каждый DOCX как ZIP;
- читает `word/*.xml`;
- проверяет отсутствие запрещённых строк;
- завершает выполнение с ошибкой, если обнаружен запрещённый маркер.

Проверяемые маркеры:
- `Захаров`
- `ВЭББАНКИР`
- `ТУРБОЗАЙМ`
- `МИГКРЕДИТ`
- `MITSUBISHI RVR`
- `1 248 887,93`

## Где лежат шаблоны

- `src/main/resources/templates/zayavlenie.docx`
- `src/main/resources/templates/prilozhenie_1.docx`
- `src/main/resources/templates/prilozhenie_2.docx`

## Как запускать подготовку шаблонов

### Linux/macOS

```bash
./scripts/template-cleanup.sh
```

### Windows

```bat
scripts\template-cleanup.cmd
```

### Альтернатива (Maven)

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateCleanupTool
```

## Как запускать аудит шаблонов

### Linux/macOS

```bash
./scripts/template-audit.sh
```

### Windows

```bat
scripts\template-audit.cmd
```

### Альтернатива (Maven)

```bash
mvn -q -DskipTests exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool
```

## Как запускать тесты

```bash
mvn test
```

## Ручная проверка через приложение

1. Открыть форму.
2. Заполнить данные клиента.
3. Открыть preview.
4. Нажать кнопку **«Сформировать ZIP с DOCX-документами»**.
5. Открыть 3 сгенерированных DOCX:
   - заявление;
   - приложение №1;
   - приложение №2.
6. Убедиться, что в документах отсутствуют старые данные Захарова и старые кредиторы/транспортные артефакты.
