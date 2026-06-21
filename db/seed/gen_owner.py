#!/usr/bin/env python
"""
점주 계정 INSERT SQL 생성기.

비밀번호를 BCrypt 해시로 바꿔서, users 테이블에 넣을 INSERT문을 출력한다.
(앱에서 보내는 비번은 평문이지만, DB에는 반드시 해시로 저장해야 하므로 이 도구로 변환한다.)

사용법:
    pip install bcrypt          # 최초 1회만
    python db/seed/gen_owner.py <loginId> <password> <nickname>

예:
    python db/seed/gen_owner.py owner02 cafe5678 "강남점장"

출력된 INSERT문을 DB에 실행하면 끝.
"""
import sys
import bcrypt

# Windows 콘솔/리다이렉트에서 한글이 깨지지 않도록 UTF-8 강제
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

if len(sys.argv) != 4:
    print("사용법: python gen_owner.py <loginId> <password> <nickname>")
    sys.exit(1)

login_id, password, nickname = sys.argv[1], sys.argv[2], sys.argv[3]
hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()

print(f"""-- loginId={login_id} / password={password} (평문은 참고용, DB엔 해시만 저장됨)
INSERT INTO users (nickname, login_id, password, role, created_at, updated_at)
VALUES ('{nickname}', '{login_id}', '{hashed}', 'OWNER', NOW(), NOW());""")
