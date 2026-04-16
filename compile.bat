@echo off
setlocal enabledelayedexpansion
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot
set JAVAC=%JAVA_HOME%\bin\javac.exe

pushd %~dp0

if not exist "out" mkdir "out"

set FILES=
for %%f in (src\models\*.java src\streams\*.java src\testes\*.java src\network\*.java) do (
    set FILES=!FILES! "%%f"
)

"%JAVAC%" -encoding UTF-8 -d "out" !FILES!

if %ERRORLEVEL% == 0 (
    echo Compilado com sucesso. Classes em: out\
) else (
    echo Erro na compilacao.
)

popd
