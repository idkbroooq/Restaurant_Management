@echo off
@REM Local Maven wrapper invoking installed Maven
set SCRIPT_DIR=%~dp0
"%SCRIPT_DIR%.tools\apache-maven-3.9.6\bin\mvn.cmd" %*
