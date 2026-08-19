param(
    [string]$NdkPath = "$env:LOCALAPPDATA\Android\Sdk\ndk\29.0.14206865"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$crate = Join-Path $projectRoot "native\jwsk-core"
$jniLibs = Join-Path $projectRoot "manager\src\main\jniLibs"

if (-not (Test-Path -LiteralPath $NdkPath)) {
    throw "Android NDK not found: $NdkPath"
}

$cargoRoot = Join-Path $projectRoot ".tooling\cargo"
$env:RUSTUP_HOME = Join-Path $projectRoot ".tooling\rustup"
$env:CARGO_HOME = $cargoRoot
$env:PATH = "$(Join-Path $cargoRoot 'bin');$env:PATH"
$toolchain = Join-Path $NdkPath "toolchains\llvm\prebuilt\windows-x86_64\bin"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = Join-Path $toolchain "aarch64-linux-android31-clang.cmd"
$env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = Join-Path $toolchain "x86_64-linux-android31-clang.cmd"
Push-Location $crate
try {
    cargo build --target aarch64-linux-android --release
    cargo build --target x86_64-linux-android --release
    New-Item -ItemType Directory -Force (Join-Path $jniLibs "arm64-v8a"), (Join-Path $jniLibs "x86_64") | Out-Null
    Copy-Item -LiteralPath (Join-Path $crate "target\aarch64-linux-android\release\libjwsk_core.so") -Destination (Join-Path $jniLibs "arm64-v8a\libjwsk_core.so") -Force
    Copy-Item -LiteralPath (Join-Path $crate "target\x86_64-linux-android\release\libjwsk_core.so") -Destination (Join-Path $jniLibs "x86_64\libjwsk_core.so") -Force
} finally {
    Pop-Location
}
