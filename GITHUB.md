# Putting this on GitHub

A clean public repo is the thing you actually link to PvPGames.gg. Here's the whole process.

## 1. Create the repo

On <https://github.com/new>: name it something like `pvpgames-demo-core`, **Public**, and do
**not** add a README/.gitignore/license (this project already has them).

## 2. Push from the project folder

From a terminal in the project folder (the one with `build.gradle.kts`):

```bash
git init
git add .
git commit -m "PvPGames Demo Core: 1v1 duels framework (lobby, queue, match engine, stats, ELO)"
git branch -M main
git remote add origin https://github.com/OWNER/REPO.git   # <-- your repo URL
git push -u origin main
```

> Replace `OWNER/REPO`. If you use SSH, use the `git@github.com:OWNER/REPO.git` form instead.

## 3. Turn on the build badge

The repo includes `.github/workflows/build.yml`, which builds the jar on every push. After your
first push:

1. Open the **Actions** tab — you should see a "Build" run go green.
2. In `README.md`, replace `OWNER/REPO` in the badge URL with your real path. Commit + push.
3. The badge now shows **build passing**, and each Actions run has the compiled jar attached as a
   downloadable artifact.

## 4. Good first-impression touches

- **About section** (right side of the repo page): add a one-line description and the topics
  `minecraft`, `paper`, `pvp`, `java`, `gradle`.
- **Pin the repo** on your GitHub profile.
- Make sure the **first commit message** and the README read well — that's what a reviewer sees
  first.
- Consider a couple of follow-up commits (e.g. "Add CTF skeleton", "Add screenshots") so the
  history shows iterative work rather than one dump.

## 5. What NOT to commit

Already handled by `.gitignore`, but double-check you never push:

- a real database password in `config.yml` (only the server copy should hold credentials),
- the `build/` output or `.gradle/` cache,
- any local `*.db` SQLite file.

## Commit message ideas for future work

```
feat(ctf): add Capture The Flag mode implementing the Game interface
feat(api): expose read-only stats endpoint for the website
feat(cosmetics): kill effects + lobby trails driven off PlayerProfile
docs: add architecture diagram and screenshots
test: add unit tests for Elo math and matchmaking bands
```
