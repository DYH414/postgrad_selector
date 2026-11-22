import os
import re
from pathlib import Path

import bcrypt


accounts = Path(os.environ["POSTGRAD_TEST_ACCOUNTS"]).read_text(encoding="utf-8")
row = next((line for line in accounts.splitlines() if line.startswith("| Admin |")), "")
cells = [cell.strip() for cell in row.split("|")]
if len(cells) < 5:
    raise SystemExit("Expected an Admin row in POSTGRAD_TEST_ACCOUNTS")
account, password = cells[2:4]
if not re.fullmatch(r"[A-Za-z0-9_.-]{2,30}", account) or not 5 <= len(password.encode()) <= 72:
    raise SystemExit("Invalid disposable admin username or password length")
password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()

print(f"""
INSERT INTO sys_user (user_name, nick_name, password, pwd_update_date, create_time)
VALUES ('{account}', 'Verify school viewer', '{password_hash}', NOW(), NOW());
SET @viewer_id = LAST_INSERT_ID();
INSERT INTO sys_role (role_name, role_key, role_sort, status, create_time)
VALUES ('Verify school viewer', 'verify_school_viewer', 1, '0', NOW());
SET @viewer_role = LAST_INSERT_ID();
INSERT INTO sys_user_role (user_id, role_id) VALUES (@viewer_id, @viewer_role);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @viewer_role, menu_id
FROM sys_menu
WHERE menu_id IN (2000, 2130)
   OR perms IN ('postgrad:workspace:view', 'postgrad:school:list', 'postgrad:school:query');
UPDATE sys_config SET config_value = 'false' WHERE config_key = 'sys.account.captchaEnabled';
""")
