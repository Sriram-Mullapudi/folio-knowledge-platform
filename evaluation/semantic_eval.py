"""Live local semantic retrieval check using only fictional documents."""
import json, secrets, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen

BASE = 'http://127.0.0.1:8081'
token = None
def call(path, data=None, kind='application/json'):
    headers = {'Content-Type': kind}
    if token: headers['Authorization'] = 'Bearer ' + token
    if isinstance(data, dict): data = json.dumps(data).encode()
    return urlopen(Request(BASE + path, data=data, headers=headers), timeout=180)
def api(path, data=None):
    with call(path, data) as response: return json.load(response)

token = api('/api/v1/auth/register', {'email': 'semantic-'+uuid.uuid4().hex+'@example.test', 'password': secrets.token_urlsafe(24)})['accessToken']
fixtures = [
    ('leave.txt', 'Vacation allowance: Employees receive twenty paid days annually.', 'How much time off can I take?'),
    ('remote.txt', 'Remote working: Staff may telecommute three weekdays weekly.', 'Can I do my job away from the office?'),
    ('expenses.txt', 'Expense reimbursement: Submit receipts within thirty days.', 'When must I turn in proof of purchases?'),
]
ids = {}
for name, content, _ in fixtures:
    boundary = 'fixture'+uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: text/plain\r\n\r\n{content}\r\n--{boundary}--\r\n').encode()
    with call('/api/v1/documents', body, 'multipart/form-data; boundary='+boundary) as response:
        ids[name] = json.load(response)['id']

results = []
for attempt in range(120):
    results = []
    for name, _, query in fixtures:
        hits = api('/api/v1/search', {'query': query, 'k': 3})
        results.append({'query': query, 'expectedDocument': name,
                        'matched': bool(hits and hits[0]['documentId'] == ids[name]),
                        'hits': hits})
    if all(r['matched'] for r in results): break
    time.sleep(2)
else: raise AssertionError(json.dumps(results))

owner_token = token
with call('/api/v1/chat', {'question': fixtures[0][2], 'documentId': ids['leave.txt']}) as response:
    stream = response.read().decode()
answer = ''
for line in stream.splitlines():
    if line.startswith('data:'):
        payload = json.loads(line[5:])
        if isinstance(payload, dict): answer += payload.get('delta', '')
assert 'event:done' in stream and 'event:error' not in stream
assert ('twenty' in answer.lower() or '20' in answer) and '[1]' in answer, answer
token = None
token = api('/api/v1/auth/register', {'email': 'semantic-other-'+uuid.uuid4().hex+'@example.test', 'password': secrets.token_urlsafe(24)})['accessToken']
assert api('/api/v1/search', {'query': fixtures[0][2], 'documentId': ids['leave.txt'], 'k': 5}) == []
Path('evaluation/semantic-results.json').write_text(json.dumps({
    'passed': True, 'model': 'nomic-embed-text:v1.5', 'checks': results,
    'crossOwnerExcluded': True, 'citedParaphraseAnswer': answer,
    'limitations': 'Three short fictional paraphrases and one cited answer, not a general answer-quality benchmark.'
}, indent=2)+'\n', encoding='utf-8')
print('Three live paraphrase rankings, a cited answer, and cross-owner isolation passed.')
