@echo off
title �Ƶ�Ԥ��ϵͳ - ��ʾ������
cd /d "%~dp0"

echo ============================================
echo    �Ƶ�Ԥ��ϵͳ - һ������
echo ============================================

echo [1/4] ��� Docker ���� ...
docker info >nul 2>&1
if not errorlevel 1 goto dockerok
start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
set /a try=0
:waitdocker
docker info >nul 2>&1
if not errorlevel 1 goto dockerok
set /a try+=1
if %try% geq 36 echo     �ȴ���ʱ�����ֶ��� Docker Desktop �����ԡ�& pause & exit /b 1
echo     �ȴ� Docker ������� ...
ping -n 6 127.0.0.1 >nul
goto waitdocker
:dockerok
echo     Docker �����Ѿ���

echo [2/4] ������������ nginx / app / mysql / redis ...
docker compose up -d

echo [3/4] �ȴ�����վ����� ...
set /a web=0
:waitweb
curl -s -m 5 -o nul http://localhost/
if not errorlevel 1 goto webok
set /a web+=1
if %web% geq 30 echo     �ȴ���ʱ���Ų����docker logs hotel-app --since 5m & pause & exit /b 1
ping -n 6 127.0.0.1 >nul
goto waitweb
:webok
echo     ����վ���Ѿ���

echo [4/4] ������������ trycloudflare ...
set "CFLARE=C:\Program Files (x86)\cloudflared\cloudflared.exe"
set "TLOG=%TEMP%\cloudflared-demo.err.log"
tasklist /FI "IMAGENAME eq cloudflared.exe" 2>nul | find /I "cloudflared.exe" >nul
if errorlevel 1 goto tunnelstart
if exist "%TLOG%" goto tunnelread
taskkill /IM cloudflared.exe /F >nul 2>&1
echo     ����������ȱ����־���������� ...
:tunnelstart
if exist "%TLOG%" del "%TLOG%"
start "cloudflared" /min cmd /c ""%CFLARE%" tunnel --url http://localhost:80 --no-autoupdate >"%TLOG%" 2>&1"
ping -n 11 127.0.0.1 >nul

:tunnelread
set "url="
if exist "%TLOG%" for /f "usebackq tokens=2 delims=|" %%a in (`findstr /C:".trycloudflare.com" "%TLOG%"`) do set "url=%%a"
if defined url set "url=%url: =%"

:tunnelloop
if defined url goto tunnelok
set /a urltry+=1
if %urltry% geq 6 goto tunneldone
echo     �ȴ�������ַ���� ...
ping -n 7 127.0.0.1 >nul
if exist "%TLOG%" for /f "usebackq tokens=2 delims=|" %%a in (`findstr /C:".trycloudflare.com" "%TLOG%"`) do set "url=%%a"
if defined url set "url=%url: =%"
goto tunnelloop

:tunnelok
echo     ��������

echo     Checking tunnel URL (DNS may take a moment) ...
set /a chk=0
:checkloop
curl -s -m 10 -o nul "%url%"
if not errorlevel 1 goto checkok
set /a chk+=1
if %chk% geq 12 goto tunneldone
echo     Waiting for tunnel URL to become reachable (try %chk%) ...
ping -n 6 127.0.0.1 >nul
goto checkloop
:checkok
echo     Tunnel URL is reachable

:tunneldone
echo.
echo ============================================
echo    ���ط��ʣ�  http://localhost
if not defined url goto nourl
echo    �������ʣ�  %url%
goto showdone
:nourl
echo    �������ʣ�  ��δ������ַ���Ժ�鿴��־ %TLOG%
:showdone
echo    ��ʾ�˺ţ�  admin / admin123
echo                13911112222 / abc123456
echo    ֹͣ��ʾ��  ���� stop-demo.bat
echo ============================================

if not defined url pause
if defined url start "" "%url%"
exit /b 0
