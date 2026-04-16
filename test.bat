@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot
set JAVA=%JAVA_HOME%\bin\java.exe
set OUT=%~dp0out

echo === TesteStreamSaida ===
"%JAVA%" -cp "%OUT%" testes.TesteStreamSaida
echo.

echo === TesteStreamEntrada ===
"%JAVA%" -cp "%OUT%" testes.TesteStreamEntrada
echo.
