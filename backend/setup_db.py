import psycopg2
from psycopg2 import sql
from decouple import config

def setup_kavach_db():
    """
    Automated DB Provisioning Script.
    Run this as 'postgres' superuser to set up the dedicated app user and database.
    """
    # Config from .env
    dbname   = config('DB_NAME', default='kavach_db')
    user     = config('DB_USER', default='kavach_user')
    password = config('DB_PASSWORD', default='strong_password')
    host     = config('DB_HOST', default='127.0.0.1')
    
    print(f"--- Provisioning Database: {dbname} for User: {user} ---")
    
    try:
        # 1. Connect to default postgres DB to create the target DB
        conn = psycopg2.connect(
            dbname='postgres',
            user='postgres',
            password=input("Enter 'postgres' superuser password: "),
            host=host
        )
        conn.autocommit = True
        cur = conn.cursor()

        # 2. Create User if not exists
        print(f"Creating user '{user}'...")
        cur.execute(f"DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_catalog.pg_user WHERE usename = '{user}') THEN CREATE USER {user} WITH PASSWORD '{password}'; END IF; END $$;")

        # 3. Create Database if not exists
        print(f"Creating database '{dbname}'...")
        cur.execute(f"SELECT 1 FROM pg_database WHERE datname = '{dbname}'")
        if not cur.fetchone():
            cur.execute(sql.SQL("CREATE DATABASE {} OWNER {}").format(
                sql.Identifier(dbname),
                sql.Identifier(user)
            ))
        
        # 4. Grant privileges
        print(f"Granting privileges on '{dbname}' to '{user}'...")
        cur.execute(sql.SQL("GRANT ALL PRIVILEGES ON DATABASE {} TO {}").format(
            sql.Identifier(dbname),
            sql.Identifier(user)
        ))
        
        cur.close()
        conn.close()
        print("SUCCESS: Database and User are ready for KAVACH operations.")
        
    except Exception as e:
        print(f"FAILURE: {e}")

if __name__ == "__main__":
    setup_kavach_db()
