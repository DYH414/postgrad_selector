# Admin school workspace

Run the [verification skill](../SKILL.md) with the disposable admin UI on port 18081. Sign in as the school viewer and open **学校数据工作台** at `/postgrad/workspace`.

1. Check that **学校数** reads **1** and **学校列表** reads **1 所**. The workspace and catalog APIs should both return **验证大学** with ID 900001. The workspace should count two programs even though the fixture admissions have no source ID.
2. Check the text drawn in **数据完整度分布**. Expect the four completeness categories and no `undefined%` text.
3. Click **学校管理** beside **学校列表**. Expect `/postgrad/basic/school` without a year query. Check that the school table shows **验证大学**.

Read `admin-workspace.log` for HTTP and JSON codes, browser counts, painted chart text, and the navigation target. The evidence directory also has screenshots and accessibility snapshots of both admin pages.
