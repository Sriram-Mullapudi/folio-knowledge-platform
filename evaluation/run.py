"""Local retrieval evaluation. Creates one synthetic account and six documents per run.
No model calls are made directly; server configuration controls embedding calls.
Use an isolated local stack with embeddings disabled for a free keyword baseline.
"""
import argparse, json, math, secrets, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlparse

parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8080')
parser.add_argument('--output',default='evaluation/results.json')
a=parser.parse_args()
if urlparse(a.base_url).hostname not in ('127.0.0.1','localhost'):
    raise SystemExit('Use an isolated local test instance.')
corpus=json.loads(Path(__file__).with_name('corpus.json').read_text())
token=None

def request(path, data=None, content_type='application/json'):
    headers={'Content-Type':content_type}
    if token: headers['Authorization']='Bearer '+token
    if isinstance(data,dict): data=json.dumps(data).encode()
    with urlopen(Request(a.base_url+path,data=data,headers=headers),timeout=65) as r:
        return json.load(r)

auth=request('/api/v1/auth/register',{'email':'eval-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24)})
token=auth['accessToken']
ids={}
for doc in corpus['documents']:
    boundary='eval'+uuid.uuid4().hex
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{doc["name"]}"\r\nContent-Type: text/plain\r\n\r\n{doc["text"]}\r\n--{boundary}--\r\n').encode()
    uploaded=request('/api/v1/documents',body,'multipart/form-data; boundary='+boundary)
    ids[uploaded['id']]=doc['name']
deadline=time.monotonic()+120
while True:
    docs=request('/api/v1/documents?size=100')['content']
    if any(d['status']=='FAILED' for d in docs): raise SystemExit('Document processing failed')
    if len(docs)==len(ids) and all(d['status']=='COMPLETED' for d in docs): break
    if time.monotonic()>deadline: raise SystemExit('Processing timed out')
    time.sleep(1)
summary=request('/api/v1/workspace/summary')
rows=[]
for q in corpus['queries']:
    start=time.perf_counter()
    hits=request('/api/v1/search',{'query':q['query'],'k':5})
    elapsed=1000*(time.perf_counter()-start)
    retrieved=list(dict.fromkeys(ids[h['documentId']] for h in hits))
    expected=set(q['relevant'])
    recall=len(expected.intersection(retrieved))/len(expected) if expected else None
    rows.append({**q,'retrieved':retrieved,'recall_at_5':recall,'latency_ms':round(elapsed,2),'empty_result_correct':not retrieved if not expected else None})
latencies=sorted(r['latency_ms'] for r in rows)
recalls=[r['recall_at_5'] for r in rows if r['recall_at_5'] is not None]
result={'mode':summary['searchMode'],'documents':summary['documents'],'passages':summary['passages'],
 'queries':len(rows),'mean_document_recall_at_5':sum(recalls)/len(recalls),
 'p50_ms':latencies[math.ceil(.50*len(latencies))-1], 'p95_ms':latencies[math.ceil(.95*len(latencies))-1],
 'limitations':'Small synthetic corpus; document-level recall over top five chunks. Single sequential run, no load test. Does not measure answer factuality or citation support.', 'results':rows}
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
Path(a.output).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='results'},indent=2))
