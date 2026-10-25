# Builds the image as <artifactId>:<version> read from pom.xml  ->  bedrock-mcp-springboot:0.1.0
# Usage (PowerShell):  .\scripts\docker-build.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
[xml]$pom = Get-Content 'pom.xml'
$name = $pom.project.artifactId
$version = $pom.project.version
$image = "${name}:${version}"
docker build --build-arg APP_VERSION=$version -t $image -t "${name}:latest" .
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }
Write-Host "Built $image"
