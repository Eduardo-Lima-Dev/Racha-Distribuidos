@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot
set JAVA=%JAVA_HOME%\bin\java.exe

pushd %~dp0

echo === Cliente TCP ===
"%JAVA%" -cp "out" network.Cliente

popd
