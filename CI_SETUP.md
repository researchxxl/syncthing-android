# GitHub Actions CI/CD Setup

## 🚀 Overview

This repository uses **GitHub Actions** for continuous integration and automated testing. Every push and pull request automatically runs tests to ensure code quality.

## 📋 CI Pipeline Jobs

### 1. Unit Tests (`unit-tests`)
- **Purpose**: Run all 125+ unit tests
- **Framework**: MockK + JUnit
- **Test Files**:
  - `WebDAVModelsTest.kt` - Data model validation
  - `WebDAVConfigManagerTest.kt` - Configuration & encryption
  - `ConflictResolverTest.kt` - Conflict resolution strategies
  - `WebDAVClientTest.kt` - WebDAV operations
  - `SyncEngineTest.kt` - Synchronization logic
  - `EInkUtilTest.kt` - E-Ink device detection

**Artifacts**:
- `test-results` - Raw test results (XML)
- `test-report` - HTML test report

### 2. Code Lint (`lint`)
- **Purpose**: Run Android lint checks
- **Checks**: Code quality, potential bugs, performance issues

**Artifacts**:
- `lint-results` - HTML lint report

### 3. Build Verification (`build`)
- **Purpose**: Verify compilation succeeds
- **Output**: Debug APK

**Artifacts**:
- `debug-apk` - Installable APK for testing

## 🔧 Technical Details

- **JDK**: 21 (Temurin distribution)
- **Gradle Cache**: Enabled for faster builds
- **OS**: Ubuntu Latest (GitHub Actions runners)
- **Timeout**: 6 hours per job

## 📊 Viewing Results

### 1. Check Workflow Status

Visit: **https://github.com/begend/syncthing-android/actions**

You'll see green ✅ (pass) or red ❌ (fail) badges for each commit.

### 2. View Test Details

1. Click on a workflow run
2. Click on "Unit Tests" job
3. Expand steps to see detailed logs
4. Download artifacts for detailed reports

### 3. Visual Test Reports

Thanks to `dorny/test-reporter`, you'll see a summary comment on PRs:

```
✅ Unit Tests - 125 passed, 0 failed
```

## 🎯 Triggering CI

### Automatic Triggers:
- Push to `main`, `master`, `feature/**`, `develop` branches
- Pull requests to `main`, `master`, `develop`

### Manual Trigger:
1. Go to **Actions** tab
2. Select **Tests** workflow
3. Click **Run workflow**
4. Choose branch and run

## 📦 Downloading Artifacts

### From GitHub UI:
1. Open workflow run page
2. Scroll to **Artifacts** section
3. Click artifact name to download

### Using GitHub CLI:
```bash
# List artifacts
gh run list --repo begend/syncthing-android

# Download specific artifact
gh run download <run-id> --name test-report --repo begend/syncthing-android
```

## 🔍 Troubleshooting

### Tests Failing?
1. Check test logs in "Unit Tests" job
2. Download `test-results` artifact
3. Review specific test failure messages
4. Fix code and push again

### Lint Errors?
1. Download `lint-results` artifact
2. Open HTML report in browser
3. Review specific lint issues
4. Fix issues or add suppressions if appropriate

### Build Failures?
1. Check "Build Verification" job logs
2. Look for compilation errors
3. Verify dependencies are correct
4. Check `IS_COPILOT=true` is set

## 🚦 Status Badge

Add to README.md:

```markdown
![CI Status](https://github.com/begend/syncthing-android/workflows/Tests/badge.svg)
```

## 💰 Cost

- **Public Repository**: FREE (unlimited minutes)
- **Private Repository**: 2,000 minutes/month free
- This repository: **Public** → No cost!

## 📈 Performance

Typical run times (GitHub-hosted runners):
- Unit Tests: ~5-10 minutes
- Lint: ~3-5 minutes
- Build: ~5-8 minutes
- **Total**: ~15-25 minutes (parallel jobs)

## 🔐 Security

- Uses official GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4`)
- No secrets required for running tests
- IS_COPILOT=true skips native builds (faster, no external dependencies)

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Testing Antipatterns](https://testing.googleblog.com/2015/04/just-say-no-to-more-end-to-end-tests.html)
- [JUnit Best Practices](https://junit.org/junit5/docs/current/user-guide/#writing-tests)

---

## ✅ Quick Start Checklist

- [x] CI configuration created
- [x] Pushed to GitHub
- [x] Tests run automatically on next push
- [ ] Check first CI run results
- [ ] Add status badge to README (optional)

---

**Created**: 2026-04-18
**Last Updated**: 2026-04-18
