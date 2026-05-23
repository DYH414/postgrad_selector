@echo off
chcp 65001 >nul
title 考研择校 - 用户端
echo ========================================
echo   考研择校决策平台 - 用户端入口
echo ========================================
echo.

set PORT=8081
set URL=http://localhost:%PORT%/app

echo 正在检查前端服务是否启动...

:: 检查端口是否可用
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:%PORT%' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop; exit 0 } catch { exit 1 }" >nul 2>&1
if %errorlevel% neq 0 (
    echo [提示] 前端服务未启动，正在启动...
    echo.
    start "" cmd /c cd /d %~dp0.. ^&^& npm run dev
    :: 等待服务启动
    echo 等待前端服务启动中，请稍候...
    :wait_loop
    timeout /t 2 /nobreak >nul
    powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:%PORT%' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop; exit 0 } catch { exit 1 }" >nul 2>&1
    if %errorlevel% neq 0 goto wait_loop
    echo [OK] 前端服务已启动！
)

echo.
echo 正在打开用户端: %URL%
start "" "%URL%"
echo.
echo 已打开浏览器，请开始使用。
echo 按任意键关闭此窗口...
pause >nul
