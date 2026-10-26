#!/usr/bin/env bash
set -euo pipefail
package_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mode="${1:-upload}"
if [[ "$mode" != upload && "$mode" != --check && "$mode" != --publish ]]; then
  echo '用法：bash UPLOAD.sh [--check | --publish]' >&2
  exit 2
fi
required=(python3)
if [[ "$mode" != --check ]]; then required+=(git gh); fi
for cmd in "${required[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    if command -v pkg >/dev/null 2>&1; then
      pkg install -y git python gh
      break
    fi
    echo '需要 git、Python 3、GitHub CLI gh。' >&2
    exit 1
  fi
done
python3 "$package_root/verify_package.py" "$package_root"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$package_root/source/android-project/tools/tests" -p 'test_*.py'
if [[ "$mode" == --check ]]; then
  echo '更新包與回歸檢查通過；--check 沒有登入、推送或啟動遠端建置。'
  exit 0
fi
if ! gh auth status --hostname github.com >/dev/null 2>&1; then
  gh auth login --hostname github.com --git-protocol https --web
fi
gh auth setup-git --hostname github.com
repo='luc700428-hash/thinn-kh-.'
task_tmp="${TMPDIR:-${HOME}/.cache}"
mkdir -p "$task_tmp"
checkout="$(mktemp -d "$task_tmp/thinn-v25-upload-XXXXXXXX")"
echo "使用獨立工作目錄：$checkout"
git clone --depth 1 --branch main "https://github.com/$repo.git" "$checkout"
# rev-parse supports both regular .git directories and linked worktrees.
git -C "$checkout" rev-parse --is-inside-work-tree >/dev/null
python3 "$package_root/apply_update.py" "$package_root" "$checkout"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s "$checkout/android-project/tools/tests" -p 'test_*.py'
if ! git -C "$checkout" diff --cached --quiet; then
  if [[ -z "$(git -C "$checkout" config user.name || true)" ]]; then
    git -C "$checkout" config user.name "$(gh api user --jq .login)"
  fi
  if [[ -z "$(git -C "$checkout" config user.email || true)" ]]; then
    account_login="$(gh api user --jq .login)"
    account_id="$(gh api user --jq .id)"
    git -C "$checkout" config user.email "$account_id+$account_login@users.noreply.github.com"
  fi
  git -C "$checkout" commit -m 'feat(v25): unify iOS controls and upgrade weather wake, widgets and island' -m 'Update-Package-ID: thinn-khi-v25-ios-upgrade-20261027'
  # Normal push only: another main update cannot be silently overwritten.
  if ! git -C "$checkout" push origin HEAD:main; then
    echo "尚未啟動建置。程式保留於 $checkout；若 GitHub 提示 workflow 權限，請執行 gh auth refresh -h github.com -s workflow 後重跑本腳本。" >&2
    exit 1
  fi
fi
head_sha="$(git -C "$checkout" rev-parse HEAD)"
existing_url="$(gh run list --repo "$repo" --workflow build-apk.yml --commit "$head_sha" --limit 1 --json url --jq '.[0].url // empty')"
if [[ "$mode" == --publish ]]; then
  gh workflow run build-apk.yml --repo "$repo" --ref main -f publish_release=true -f full_milestone=false
  echo '已啟動一般簽章 OTA 發布；須等待 GitHub Actions 簽章、版本與檔案驗證通過。'
elif [[ -z "$existing_url" ]]; then
  gh workflow run build-apk.yml --repo "$repo" --ref main -f publish_release=false -f full_milestone=false
  echo '上傳完成，已啟動候選 APK 建置。'
else
  echo "此版本已有建置：$existing_url"
fi
printf '建置狀態：https://github.com/%s/actions/workflows/build-apk.yml\n' "$repo"
if [[ "$mode" != --publish ]]; then
  echo '要發布正式 OTA，執行 bash UPLOAD.sh --publish；沿用 GitHub 既有簽章，未宣告全部功能通過真機驗收。'
fi
