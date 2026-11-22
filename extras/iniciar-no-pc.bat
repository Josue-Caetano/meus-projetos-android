@echo off
REM Roda o painel no PC (Windows) para abrir os projetos pelo celular via Wi-Fi.
REM Troque o caminho abaixo pela pasta onde ficam seus projetos.
set PROJETOS=%USERPROFILE%\projetos
python "%~dp0servidor.py" -d "%PROJETOS%" -p 8080
pause
