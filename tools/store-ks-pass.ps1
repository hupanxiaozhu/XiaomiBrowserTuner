# 把 release keystore 的口令用 Windows DPAPI 加密存起来（只有当前 Windows 用户能解）
#
# 用法：powershell -NoProfile -File tools/store-ks-pass.ps1
# 口令是交互式输入的（Read-Host -AsSecureString），不经过命令行参数、不进历史。
#
# 密文落点：%USERPROFILE%\.dsh\secrets\xiaomibrowser-ks.dpapi
# 换机器 / 换 Windows 账户后解不开（这是 DPAPI 的设计，不是 bug）—— 那时重跑一次本脚本即可。
$ErrorActionPreference = "Stop"

$dir    = Join-Path $env:USERPROFILE ".dsh\secrets"
$target = Join-Path $dir "xiaomibrowser-ks.dpapi"

New-Item -ItemType Directory -Force -Path $dir | Out-Null

$sec = Read-Host -AsSecureString "输入 TEST keystore 口令（ks-pass 与 key-pass 同一个）"
if ($null -eq $sec -or $sec.Length -eq 0) { throw "口令为空，已取消" }

$sec | ConvertFrom-SecureString | Set-Content -Path $target -Encoding ASCII

# 收紧 ACL：只留当前用户
& icacls $target /inheritance:r /grant:r "$($env:USERNAME):(R)" 2>&1 | Out-Null

$item = Get-Item $target
Write-Host ""
Write-Host "[ok] 已保存：$($item.FullName)"
Write-Host "     大小：$($item.Length) 字节（DPAPI 密文，绑定当前用户）"
Write-Host "     下一步：powershell -NoProfile -File tools/sign-release.ps1"
