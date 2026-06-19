# Task 4 Report: Priority-Weighted Sorting

## Changes Made

### 1. CandidatePoolServiceImpl.java
- Added `normalizePriority()` helper method — normalizes `schoolTierPreference` to one of three standard priorities: `school_tier_priority`, `developed_region_priority`, `safe_admission_priority`.
- Added `DEVELOPED_CITIES` set (25 cities including 北京,上海,深圳,广州,杭州,南京,武汉,成都,苏州,天津,重庆,西安,长沙,青岛,厦门,宁波,无锡,合肥,郑州,济南,福州,东莞,佛山,珠海).
- Updated `compositeScore()` — added priority-weighted boost after base score:
  - `school_tier_priority`: 985 +14, 211/双一流 +10
  - `safe_admission_priority`: gap bonus up to +16, quota risk normal +8, data completeness A +6
  - `developed_region_priority`: developed city +10
- Removed the old hardcoded tier-priority checks (`"tier_priority"`, `"must_211_or_better"`, `"prefer_211_or_better"`).

### 2. CandidateWorkspaceServiceImpl.java
- Added `normalizePriority()` helper method.
- Removed `regionStrategy` parameter entirely from `buildTier()` and `policyScore()` — no longer derived or passed.
- Updated `policyScore()` with same priority-weighted boost logic as `compositeScore()`.
- Simplified `buildWorkspace()` entry (removed the now-unnecessary `regionStrategy` derivation).
- Updated `buildTier` call sites to pass only 5 parameters (removed `regionStrategy`).

### 3. Verification
- `mvn compile -pl ruoyi-postgrad,ruoyi-admin -am -DskipTests -q` — **SUCCESS**
- `mvn -pl ruoyi-postgrad -DskipTests=false test` — **39/39 tests pass, 0 failures, 0 errors**

## Architecture Notes
- `ICandidateWorkspaceService` interface already had the simplified single-param `buildWorkspace(CandidateUniverseVO, String schoolTierPref)` signature from Task 3 — no interface change needed.
- `DraftServiceImpl` already called `workspaceService.buildWorkspace(universe, schoolTierPref)` without `regionStrategy` — no call-site change needed.
- `normalizePriority()` in both services matches the implementation already present in `DraftServiceImpl`.

## Fixes Applied (2026-06-19)

### Fix 1 — CandidateWorkspaceServiceImpl school_tier_priority base score replacement
- Changed `school_tier_priority` block: now subtracts the old base score then adds the priority-adjusted score (985=34, 211/双一流=26, other=6), effectively replacing rather than stacking.

### Fix 2 — Developed region bonus +10 -> +14
- `CandidatePoolServiceImpl.compositeScore()` line 295: `score += 10` changed to `score += 14`
- `CandidateWorkspaceServiceImpl.policyScore()` line 179: `score += 10` changed to `score += 14`

### Fix 3 — Unify DEVELOPED_CITIES sets
- Both files now use the same 24-city set: removed `大连`, added `珠海` (already present in PoolService), matching the unified spec order.
- CandidatePoolServiceImpl: `"大连"` removed from set
- CandidateWorkspaceServiceImpl: `"大连"` removed, `"珠海"` added, order re-sorted to match PoolService

### Verification
- `mvn compile -pl ruoyi-postgrad,ruoyi-admin -am -DskipTests -q` — **SUCCESS**
- `mvn test -pl ruoyi-postgrad -DskipTests=false` — **39/39 tests pass, 0 failures, 0 errors**
- Commit: `fix(sort): correct priority scores to match spec`

### Fix 4 — CandidatePoolServiceImpl compositeScore school_tier_priority replacement scoring
- Changed `school_tier_priority` block in `compositeScore()`: replaced additive scoring (985 +14, 211 +10) with replacement scoring, matching the `CandidateWorkspaceServiceImpl` approach: subtract base (985=25, 211/双一流=18, other=10) then add adjusted (985=34, 211/双一流=26, other=6). Final scores: 985=34, 211/双一流=26, other=6 (was 985=39, 211=28, other=10).
- Compilation: `mvn compile -pl ruoyi-postgrad,ruoyi-admin -am -DskipTests -q` — **SUCCESS**
- Commit: `fix(sort): correct school tier replacement scoring in compositeScore`
