$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $PSScriptRoot "fsrs-bridge"
$outputDir = Join-Path $repoRoot "app/src/main/jniLibs"
if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { throw "Install Rust/Cargo first." }
if (-not (Get-Command cargo-ndk -ErrorAction SilentlyContinue)) { throw "Install cargo-ndk: cargo install cargo-ndk" }
rustup target add aarch64-linux-android x86_64-linux-android
Push-Location $bridgeDir
try {
    cargo ndk -t arm64-v8a -t x86_64 -o $outputDir build --release
    if ($LASTEXITCODE -ne 0) { throw "Rust Android build failed: $LASTEXITCODE" }
} finally { Pop-Location }
Write-Host "Native libraries generated in $outputDir"
Write-Host "Next: .\gradlew.bat :app:assembleDebug"
