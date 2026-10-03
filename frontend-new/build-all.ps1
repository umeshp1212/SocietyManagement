# Build script for separate Angular apps

Write-Host "=== Building Society Management Apps ===" -ForegroundColor Green

# Step 1: Install dependencies
Write-Host "Step 1: Installing dependencies..." -ForegroundColor Yellow
npm install

# Step 2: Build shared library
Write-Host "Step 2: Building shared library..." -ForegroundColor Yellow
npm run build:shared

# Step 3: Build admin portal
Write-Host "Step 3: Building admin portal..." -ForegroundColor Yellow
npm run build:admin

# Step 4: Build member portal PWA
Write-Host "Step 4: Building member portal PWA..." -ForegroundColor Yellow
npm run build:member

Write-Host "=== Build completed successfully! ===" -ForegroundColor Green
Write-Host ""
Write-Host "Output directories:" -ForegroundColor Cyan
Write-Host "- Admin Portal: dist/admin-portal" -ForegroundColor White
Write-Host "- Member Portal: dist/member-portal" -ForegroundColor White
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Test both applications" -ForegroundColor White
Write-Host "2. Update Docker configuration" -ForegroundColor White
Write-Host "3. Deploy to production" -ForegroundColor White