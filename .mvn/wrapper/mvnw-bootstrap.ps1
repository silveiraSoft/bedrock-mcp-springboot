param([Parameter(Mandatory = $true)][string]$BaseDir)
# Downloads (once) the Maven distribution declared in maven-wrapper.properties and prints its home dir.
$ErrorActionPreference = 'Stop'
$props = Join-Path $BaseDir '.mvn\wrapper\maven-wrapper.properties'
$line = Get-Content $props | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
$url = $line.Split('=', 2)[1].Trim()
$name = ([IO.Path]::GetFileName($url)) -replace '-bin\.zip$', ''
$userHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $env:USERPROFILE '.m2' }
$dist = Join-Path $userHome ("wrapper\dists\" + $name)
if (-not (Test-Path (Join-Path $dist 'bin\mvn.cmd'))) {
    [Console]::Error.WriteLine("Downloading $url")
    $tmp = "$dist.tmp"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $zip = Join-Path $tmp 'maven.zip'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Split-Path $dist) | Out-Null
    Move-Item (Join-Path $tmp $name) $dist
    Remove-Item $tmp -Recurse -Force
}
Write-Output $dist
