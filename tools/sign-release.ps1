# XiaomiBrowserTuner —— release 签名（口令走 DPAPI 密文，不进命令行 / 进程列表 / 仓库）
#
# 前置：先跑一次 tools/store-ks-pass.ps1 把 keystore 口令用 DPAPI 存好
# 用法：powershell -NoProfile -File tools/sign-release.ps1
#       powershell -NoProfile -File tools/sign-release.ps1 -Keystore "D:\other\TEST"
param(
    # keystore 路径**不进仓库**（含本机目录结构）。二选一传进来：
    #   set HB_KEYSTORE=<keystore 路径>        然后 powershell -File tools/sign-release.ps1
    #   powershell -File tools/sign-release.ps1 -Keystore "<keystore 路径>"
    [string]$Keystore = $env:HB_KEYSTORE,
    [string]$Alias    = "key0"
)

$ErrorActionPreference = "Stop"

$repo       = Split-Path -Parent $PSScriptRoot
$apk        = Join-Path $repo "app\build\outputs\apk\release\app-release-unsigned.apk"
$out        = Join-Path $repo "app\release\app-release.apk"
$arch       = Join-Path $repo "app\release\archive"
$secret     = Join-Path $env:USERPROFILE ".dsh\secrets\xiaomibrowser-ks.dpapi"
# build-tools 从 ANDROID_HOME / ANDROID_SDK_ROOT 派生，取主版本号最大的那个 —— 不写死本机路径
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $null }
$buildTools = if ($sdkRoot) {
    $bt = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [int]($_.Name.Split('.')[0]) } -Descending | Select-Object -First 1
    if ($bt) { $bt.FullName } else { $null }
} else { $null }

if (-not (Test-Path $apk))    { throw "未找到待签包：$apk（先跑 gradlew assembleRelease）" }
if (-not $buildTools)         { throw "找不到 build-tools：先设置 ANDROID_HOME 环境变量" }
if (-not $Keystore)           { throw "未指定 keystore：设置 HB_KEYSTORE 环境变量，或用 -Keystore 传路径" }
if (-not (Test-Path $secret)) { throw "未找到口令密文：$secret（先跑 tools/store-ks-pass.ps1）" }
if (-not (Test-Path $Keystore)) { throw "未找到 keystore：$Keystore" }

# 旧产物归档，保留回退（不覆盖、不删除）
if (Test-Path $out) {
    New-Item -ItemType Directory -Force -Path $arch | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Move-Item -Force $out (Join-Path $arch ("app-release-{0}.apk" -f $stamp))
    Write-Host "[archive] 旧产物已归档到 app\release\archive\app-release-$stamp.apk"
}

# DPAPI 解密 → 临时文件 → 签完即删（口令全程不进 argv）
# -Raw 会把写文件时补的行尾换行一起读进来，而 DPAPI 串不允许带换行 —— 先 Trim
$sec   = (Get-Content -Path $secret -Raw).Trim() | ConvertTo-SecureString
$bstr  = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

$tmp = Join-Path $env:TEMP ("hb-ks-{0}.pass" -f ([guid]::NewGuid().ToString("N")))
try {
    # apksigner 的 PasswordRetriever 是**按行顺序取密码**的：同一个文件被 `--ks-pass` 和
    # `--key-pass` 共用时，第一行当 keystore 口令、第二行当 key 口令。只写一行会报
    # "end of file reached"（第一次踩的就是这个）。这里两行写同一个值。
    $lines = $plain + [Environment]::NewLine + $plain
    Set-Content -Path $tmp -Value $lines -Encoding ASCII
    # 只给当前用户读，去掉继承
    & icacls $tmp /inheritance:r /grant:r "$($env:USERNAME):(R)" 2>&1 | Out-Null

    & (Join-Path $buildTools "apksigner.bat") sign `
        --ks $Keystore --ks-key-alias $Alias `
        --ks-pass ("file:" + $tmp) --key-pass ("file:" + $tmp) `
        --out $out $apk
    if ($LASTEXITCODE -ne 0) { throw "apksigner 失败（exit $LASTEXITCODE）" }
}
finally {
    # 双保险：先清空内容再删。apksigner 进程若还握着句柄，删除会失败，
    # 但内容已经被覆盖成空串 —— 不留下可读口令。
    if (Test-Path $tmp) {
        try { Set-Content -Path $tmp -Value "" -Encoding ASCII -Force } catch { }
        try { Remove-Item -Force $tmp } catch { }
        if (Test-Path $tmp) { Write-Warning "临时口令文件未能删除（内容已清空）：$tmp" }
    }
    $plain = $null
    $sec   = $null
}

Write-Host ""
Write-Host "=== 核对 ==="
& (Join-Path $buildTools "aapt2.exe") dump badging $out | Select-Object -First 1
& (Join-Path $buildTools "apksigner.bat") verify --print-certs $out | Select-Object -First 6
Get-Item $out | Select-Object Length, LastWriteTime | Format-List
