"""After history_program_management.sql, test real blocking transactions in the disposable DB.
Only localhost:55440 / sipatokkong_flow_test is used. Never connects to Supabase.
"""
import os
import subprocess

PSQL = os.environ.get('PSQL', '/opt/homebrew/opt/postgresql@18/bin/psql')
CMD = [PSQL, '-X', '-h', '127.0.0.1', '-p', '55440', '-d', 'sipatokkong_flow_test', '-v', 'ON_ERROR_STOP=1', '-At']
PARENT = '00000000-0000-0000-0000-000000000001'
AUTHOR = '00000000-0000-0000-0000-000000000002'
def as_user(user, query):
    return f"set role authenticated; select set_config('request.jwt.claim.sub','{user}',false); {query}"
def competing(first, second, expected):
    p = subprocess.Popen(CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p.stdin.write('begin; '+first+" select 'LOCKED'; select pg_sleep(1); commit;\n")
    p.stdin.close()
    while True:
        line = p.stdout.readline()
        if 'LOCKED' in line: break
        if not line: raise AssertionError(p.stderr.read())
    r = subprocess.run(CMD, input=second, text=True, capture_output=True)
    p.wait(timeout=10)
    assert p.returncode == 0, p.stderr.read()
    assert r.returncode != 0 and expected in r.stderr, r.stdout+r.stderr

for suffix, first_cancel in [('070',True),('071',False)]:
    task = f'50000000-0000-0000-0000-000000000{suffix}'
    cancel = as_user(AUTHOR,f"select public.cancel_one_off('{task}',0);")
    submit = as_user(PARENT,f"insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date) values('{task}','{PARENT}','2025-01-01');")
    competing(cancel if first_cancel else submit, submit if first_cancel else cancel,
              'dibatalkan' if first_cancel else 'memiliki setoran')
print('Both concurrent cancellation/submission orderings passed')

for suffix, first_edit in [('073',True),('074',False)]:
    task = f'50000000-0000-0000-0000-000000000{suffix}'
    edit = as_user(AUTHOR,f"select public.edit_program('{task}',0,gen_random_uuid(),(select to_jsonb(t) || '{{\"deadline\":\"2025-02-02\"}}'::jsonb from public.tugas t where id='{task}'));")
    submit = as_user(PARENT,f"insert into public.tugas_penyelesaian(tugas_id,orangtua_id,occurrence_date) values('{task}','{PARENT}','2025-01-01');")
    competing(edit if first_edit else submit, submit if first_edit else edit, 'berubah')
print('Both concurrent date-edit/submission orderings passed')
