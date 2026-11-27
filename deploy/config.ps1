# Shared settings for setup-gcp.ps1 and deploy.ps1. Change them here only.
# Nothing in this file is secret: the database password lives in Secret Manager.

$ProjectId  = "koshly-dev"        # the Firebase / Google Cloud project
$Region     = "us-central1"       # Iowa: low cost, Cloud Run's free tier, about 30 ms from Texas
$Repo       = "koshly"            # Artifact Registry repository for container images
$Service    = "koshly-api"        # Cloud Run service name
$DbInstance = "koshly-db"         # Cloud SQL instance
$DbName     = "koshly"            # database inside it
$DbUser     = "koshly"            # database user the API signs in as
$DbSecret   = "koshly-db-password" # Secret Manager secret holding that user's password
$RuntimeSa  = "koshly-api"        # service account the API runs as

# Worked out from the above
$RuntimeSaEmail = "$RuntimeSa@$ProjectId.iam.gserviceaccount.com"
$Image          = "$Region-docker.pkg.dev/$ProjectId/$Repo/$Service"
$SqlConnection  = "${ProjectId}:${Region}:${DbInstance}"

# Runs a gcloud/docker command, shows it, and stops the script if it fails.
function Invoke-Step {
    param([string]$Title, [scriptblock]$Command)
    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Step failed: $Title (exit code $LASTEXITCODE)" }
}

# True if a gcloud "describe" finds the thing (used to skip steps already done).
function Test-Exists {
    param([scriptblock]$Describe)
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $Describe *> $null
    $found = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $old
    return $found
}
