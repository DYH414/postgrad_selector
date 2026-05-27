# Restore Screening Range Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the current "筛选范围" feature so the UI sends `scoreRange`, backend filters by admitted average score, and results explain the range clearly.

**Architecture:** Keep backend API names and `scoreRange` unchanged. The service treats `scoreRange=N` as `estimatedScore >= avgAdmittedScore - N`, returns one flat `matches` group, and sorts by closeness to the boundary. The frontend presents this as "筛选范围 / 均分+N".

**Tech Stack:** Spring Boot/RuoYi Java service, MyBatis XML mapper, Vue 2 + Element UI frontend, Maven tests, Vue production build.

---

### Task 1: Backend TDD Restore

**Files:**
- Modify: `ruoyi-postgrad/pom.xml`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/ProgramRecommendationServiceImplTest.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/ProgramRecommendationServiceImpl.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java`

- [ ] Add JUnit 5 and Mockito test dependencies to `ruoyi-postgrad/pom.xml`.
- [ ] Write tests proving average-score range inclusion, exclusion, missing-average exclusion, and risk sorting.
- [ ] Run `mvn -pl ruoyi-postgrad -Dtest=ProgramRecommendationServiceImplTest test` and verify the tests fail before production code.
- [ ] Restore service and mapper support for `scoreRange`.
- [ ] Re-run the Maven test command and verify all tests pass.

### Task 2: Frontend Restore

**Files:**
- Modify: `ruoyi-ui/src/views/postgrad/app/recommend.vue`
- Modify: `ruoyi-ui/src/views/postgrad/app/results.vue`

- [ ] Restore the "筛选范围" row with `均分+5`, `均分+10`, `均分+15`, `均分+20`, and `不限`.
- [ ] Send `scoreRange` in the generate request.
- [ ] Show `筛选范围 均分+N` in result chips.
- [ ] Remove fake fallback recommendation data from the results page.
- [ ] Ensure empty result state does not display demo schools.
- [ ] Run `npm run build:prod` in `ruoyi-ui`.

### Task 3: Browser Verification

**Files:**
- Verify only.

- [ ] Open `/app/recommend`.
- [ ] Confirm the page shows "筛选范围" and the `均分+N` buttons.
- [ ] Confirm the help tooltip exists.
- [ ] Open `/app/results?id=83`.
- [ ] Confirm result page displays the screening range chip when available.
