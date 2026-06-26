# Publishing orgdroid to F-Droid

## Prerequisites

- `v0.1.0` tag is live on GitHub ✓
- `fdroid-metadata.yml` at repo root is the ready-made metadata recipe ✓
- A GitLab account

## Steps

### 1. Fork fdroiddata

Go to https://gitlab.com/fdroid/fdroiddata and click **Fork**.

### 2. Clone your fork and create a branch

```sh
git clone git@gitlab.com:<your-gitlab-user>/fdroiddata.git
cd fdroiddata
git checkout -b add-dev.orgdroid
```

### 3. Add the metadata file

```sh
cp /path/to/orgdroid/fdroid-metadata.yml metadata/dev.orgdroid.yml
```

### 4. (Optional) Lint locally

```sh
nix-shell -p fdroidserver --run "fdroid lint dev.orgdroid"
nix-shell -p fdroidserver --run "fdroid build -v -l dev.orgdroid"
```

### 5. Commit and push

```sh
git add metadata/dev.orgdroid.yml
git commit -m "New app: dev.orgdroid"
git push origin add-dev.orgdroid
```

### 6. Open a merge request

Go to https://gitlab.com/fdroid/fdroiddata/-/merge_requests/new and open
an MR from `add-dev.orgdroid` against `master`. Title: `New app: orgdroid (dev.orgdroid)`.

Review typically takes a few weeks. Iterate on reviewer feedback by pushing
to the same branch.

## Future releases

For each new release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit and push
3. Tag `vX.Y.Z` (annotated) and push the tag

F-Droid's bot will detect the new tag via `UpdateCheckMode: Tags` and
open a PR in fdroiddata automatically.
