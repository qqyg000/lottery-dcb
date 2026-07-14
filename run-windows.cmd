@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "CONSOLE_LOG_CHARSET=UTF-8"
if not exist "lottery-dcb.jar" (
  echo 请先在项目根目录执行 mvn clean package
  exit /b 1
)
java -jar lottery-dcb.jar
endlocal
