@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"
call mvn -q -DskipTests -f "%REPO_ROOT%\pom.xml" exec:java -Dexec.mainClass=com.bankrotapp.template.TemplateAuditTool %*
endlocal
