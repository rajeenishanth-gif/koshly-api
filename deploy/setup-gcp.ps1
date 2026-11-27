# One-time Google Cloud setup for the Koshly API. Safe to run again: steps
# already done are skipped. Takes about 10-15 minutes (the database is slow to create).
#
# Run from the koshly-api folder, in PowerShell:
#   powershell -ExecutionPolicy Bypass -File deploy\setup-gcp.ps1
#
# Before running: billing is enabled on the project, a budget alert is set (see
# the guide, Step 29), and you're signed in: gcloud auth login

. "$PSScriptRoot\config.ps1"

Invoke-Step "Use project $ProjectId" { gcloud config set project $ProjectId }

# 1. Turn on the Google Cloud services the API needs.
Invoke-Step "Enable APIs (Cloud Run, Artifact Registry, Cloud SQL, Secret Manager, IAM credentials)" {
    gcloud services enable run.googleapis.com artifactregistry.googleapis.com sqladmin.googleapis.com `
        secretmanager.googleapis.com iamcredentials.googleapis.com
}

# 2. A private place for the API's container images.
if (-not (Test-Exists { gcloud artifacts repositories describe $Repo --location $Region })) {
    Invoke-Step "Create Artifact Registry repository '$Repo'" {
        gcloud artifacts repositories create $Repo --repository-format=docker --location=$Region `
            --description="Koshly container images"
    }
} else { Write-Host "Repository '$Repo' already exists" -ForegroundColor DarkGray }

# 3. The API's own identity, with only the permissions it needs.
if (-not (Test-Exists { gcloud iam service-accounts describe $RuntimeSaEmail })) {
    Invoke-Step "Create service account '$RuntimeSa'" {
        gcloud iam service-accounts create $RuntimeSa --display-name="Koshly API (Cloud Run)"
    }
} else { Write-Host "Service account '$RuntimeSa' already exists" -ForegroundColor DarkGray }

$roles = @(
    "roles/cloudsql.client",       # connect to Cloud SQL through the connector
    "roles/firebaseauth.admin",    # Firebase Admin: users, phone numbers, revoked sessions
    "roles/datastore.user"         # Firestore: delete a profile when an account is deleted
)
foreach ($role in $roles) {
    Invoke-Step "Grant $role" {
        gcloud projects add-iam-policy-binding $ProjectId --member="serviceAccount:$RuntimeSaEmail" `
            --role=$role --condition=None --quiet *> $null
    }
}
# Signing Firebase custom tokens ("Add a country") uses the account's own key, held by Google.
Invoke-Step "Let the API sign tokens as itself" {
    gcloud iam service-accounts add-iam-policy-binding $RuntimeSaEmail --member="serviceAccount:$RuntimeSaEmail" `
        --role="roles/iam.serviceAccountTokenCreator" --quiet *> $null
}

# 4. The database: Postgres 17, smallest shared-core size, daily backups, protected from deletion.
if (-not (Test-Exists { gcloud sql instances describe $DbInstance })) {
    Invoke-Step "Create Cloud SQL instance '$DbInstance' (takes 5-10 minutes)" {
        gcloud sql instances create $DbInstance --database-version=POSTGRES_17 --edition=ENTERPRISE `
            --tier=db-f1-micro --region=$Region --availability-type=zonal `
            --storage-type=SSD --storage-size=10GB --storage-auto-increase `
            --backup-start-time=08:00 --deletion-protection
    }
} else { Write-Host "Cloud SQL instance '$DbInstance' already exists" -ForegroundColor DarkGray }

if (-not (Test-Exists { gcloud sql databases describe $DbName --instance $DbInstance })) {
    Invoke-Step "Create database '$DbName'" { gcloud sql databases create $DbName --instance=$DbInstance }
} else { Write-Host "Database '$DbName' already exists" -ForegroundColor DarkGray }

# 5. The database user, with a random password stored only in Secret Manager.
if (-not (Test-Exists { gcloud secrets describe $DbSecret })) {
    # 32 random letters and digits; never printed or saved to disk except a temp file deleted at once.
    $chars = [char[]]"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

    Invoke-Step "Create database user '$DbUser'" {
        gcloud sql users create $DbUser --instance=$DbInstance --password=$password
    }
    $tmp = New-TemporaryFile
    try {
        [System.IO.File]::WriteAllText($tmp.FullName, $password)   # no trailing newline
        Invoke-Step "Store the password in Secret Manager as '$DbSecret'" {
            gcloud secrets create $DbSecret --replication-policy=automatic --data-file=$($tmp.FullName)
        }
    } finally {
        Remove-Item $tmp.FullName -Force
        $password = $null
    }
} else { Write-Host "Secret '$DbSecret' already exists (database user was created with it)" -ForegroundColor DarkGray }

Invoke-Step "Let the API read the password secret (and nothing else)" {
    gcloud secrets add-iam-policy-binding $DbSecret --member="serviceAccount:$RuntimeSaEmail" `
        --role="roles/secretmanager.secretAccessor" --quiet *> $null
}

# 6. Let Docker on this PC push images to Artifact Registry with your gcloud sign-in.
Invoke-Step "Connect Docker to Artifact Registry" { gcloud auth configure-docker "$Region-docker.pkg.dev" --quiet }

Write-Host ""
Write-Host "Setup done. Next: powershell -ExecutionPolicy Bypass -File deploy\deploy.ps1" -ForegroundColor Green
