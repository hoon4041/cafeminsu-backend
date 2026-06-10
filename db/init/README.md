# DB 초기화 스크립트

이전에 만드신 `카페민수_DDL.sql` 파일을 이 디렉터리에 `01_schema.sql`로 복사하면
`docker compose up` 시 MySQL 컨테이너 첫 기동에서 자동으로 실행됩니다.

```bash
cp ~/Downloads/카페민수_DDL.sql ./db/init/01_schema.sql
```

이미 컨테이너가 한 번 떠 있던 상태라면 자동 실행이 안 됩니다.
- 볼륨 초기화: `docker compose down -v && docker compose up -d`
- 또는 수동 실행: `docker exec -i cafeminsu-mysql mysql -ucafeminsu -pcafeminsu cafeminsu &lt; db/init/01_schema.sql`
