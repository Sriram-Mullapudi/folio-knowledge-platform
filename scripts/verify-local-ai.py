import json, urllib.request, time
from pathlib import Path
BASE='http://127.0.0.1:8081'
def post(path,data,token=None,accept='application/json'):
    headers={'Content-Type':'application/json','Accept':accept}
    if token: headers['Authorization']='Bearer '+token
    return urllib.request.urlopen(urllib.request.Request(BASE+path,json.dumps(data).encode(),headers),timeout=180)
for attempt in range(20):
    try:
        urllib.request.urlopen(BASE+'/actuator/health',timeout=2).close()
        break
    except Exception:
        time.sleep(2)
with post('/api/v1/auth/login',{'email':'workspace-smoke-20261014@example.test','password':'Local-Smoke-Only-2026!'}) as r: token=json.load(r)['accessToken']
request=urllib.request.Request(BASE+'/api/v1/documents?page=0&size=20',headers={'Authorization':'Bearer '+token})
with urllib.request.urlopen(request) as r: docs=json.load(r)['content']
doc=next(d for d in docs if d['filename']=='Sample team handbook.txt')
results=[]
for question in ['How many vacation days do team members receive?', 'What is the company spacecraft launch date?']:
    started=time.monotonic()
    with post('/api/v1/chat',{'question':question,'documentId':doc['id']},token,'text/event-stream, application/json') as r: stream=r.read().decode()
    answer='';sources=[];done=False
    for frame in stream.replace('\r\n','\n').split('\n\n'):
        lines=frame.splitlines();event=next((v[6:].strip() for v in lines if v.startswith('event:')),'')
        payload=''.join(v[5:].strip() for v in lines if v.startswith('data:'))
        if not payload: continue
        data=json.loads(payload)
        if event=='token': answer+=data['delta']
        if event=='sources': sources=data
        if event=='done': done=True
        if event=='error': raise RuntimeError(data)
    assert done, 'Stream did not complete'
    results.append({'question':question,'answer':answer,'sourceCount':len(sources),'seconds':round(time.monotonic()-started,2)})
    print(json.dumps(results[-1]),flush=True)
assert '20' in results[0]['answer'] and '[1]' in results[0]['answer']
Path('artifacts/local-ai-smoke.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
started=time.monotonic()
with post('/api/v1/agent',{'question':'Summarize this handbook','documentId':doc['id'],'mode':'SUMMARIZATION'},token) as r: summary=json.load(r)
assert '[1]' in summary['answer'] and summary['sources']
results.append({'mode':'SUMMARIZATION','answer':summary['answer'],'sourceCount':len(summary['sources']),'seconds':round(time.monotonic()-started,2)})
print(json.dumps(results[-1]),flush=True)
Path('artifacts/local-ai-smoke.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
