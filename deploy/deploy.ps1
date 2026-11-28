# Builds the API's container image, pushes it to Artifact Registry and deploys
# it to Cloud Run, applying the full list of settings below.
#
# CAUTION: --set-env-vars REPLACES all of the service's settings with this list, so
# anything changed in the Cloud Run console (CORS for the website, FIREBASE_TENANT_*...)
# is lost. For a normal new version use the Gradle tasks instead, which change only the
# image: .\gradlew.bat release  (see gradle/cloud.gradle). Use this script to set up a
# new environment, after updating the list.
#
# Run from the koshly-api folder, in PowerShell, with Docker Desktop running:
#   powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1

. "$PSScriptRoot\config.ps1"
Set-Location (Split-Path $PSScriptRoot -Parent)   # the koshly-api folder

# Tag each image with the git commit, so you can tell which code is running.
$tag = (git rev-parse --short HEAD 2>$null)
if (-not $tag) { $tag = Get-Date -Format "yyyyMMdd-HHmmss" }
if (git status --porcelain 2>$null) { $tag = "$tag-dirty" }   # uncommitted changes included

Invoke-Step "Build image $Image`:$tag" { docker build -t "$Image`:$tag" . }
Invoke-Step "Push image" { docker push "$Image`:$tag" }

# Anyone may call the API over the internet: it checks the Firebase sign-in on every
# request itself. --no-invoker-iam-check does that without granting "allUsers", which
# organization policies (like the one that blocked key files) often forbid.

# Settings for the deployed API. "^;^" makes ";" the separator, so values may contain commas.
$settings = @(
    "KOSHLY_ENVIRONMENT=staging",
    "KOSHLY_CODE_SENDER=disabled",        # until Twilio is set up (then: twilio + TWILIO_* settings)
    "FIREBASE_PROJECT_ID=$ProjectId",
    "KOSHLY_ENABLED_COUNTRIES=US",         # default only; admins switch countries in Firestore config/app
    "CORS_ALLOWED_ORIGINS=http://localhost:8081",
    "DB_URL=jdbc:postgresql:///$DbName`?cloudSqlInstance=$SqlConnection&socketFactory=com.google.cloud.sql.postgres.SocketFactory",
    "DB_USER=$DbUser"
) -join ";"

Invoke-Step "Deploy to Cloud Run ($Service in $Region)" {
    gcloud run deploy $Service `
        --image "$Image`:$tag" `
        --region $Region `
        --service-account $RuntimeSaEmail `
        --set-env-vars "^;^$settings" `
        --set-secrets "DB_PASSWORD=$DbSecret`:latest" `
        --no-invoker-iam-check `
        --memory 1Gi --cpu 1 --cpu-boost `
        --min-instances 0 --max-instances 2 --concurrency 40 --timeout 60
}

$url = gcloud run services describe $Service --region $Region --format "value(status.url)"
Write-Host ""
Write-Host "Deployed: $url" -ForegroundColor Green
Write-Host "Health:   $url/actuator/health"
try {
    $health = Invoke-RestMethod "$url/actuator/health" -TimeoutSec 60
    Write-Host "Status:   $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "The health check didn't answer yet. See the logs: gcloud run services logs read $Service --region $Region --limit 50" -ForegroundColor Yellow
}
