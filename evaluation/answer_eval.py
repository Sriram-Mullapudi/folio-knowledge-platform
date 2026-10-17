"""Live local-only answer evaluation with fictional fixtures; no third-party packages.
Checks are explicit heuristics, not an LLM judge or proof of factual correctness.
"""
import argparse,json,re,secrets,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.parse import urlparse
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8081')
parser.add_argument('--output',default='evaluation/answer-results.json')
a=parser.parse_args()
if urlparse(a.base_url).hostname not in ('127.0.0.1','localhost'):raise SystemExit('Local evaluation only')
corpus=json.loads(Path(__file__).with_name('answer-corpus.json').read_text(encoding='utf-8'))
token=None

def call(path,data=None,content_type='application/json',accept='application/json'):
 headers={'Content-Type':content_type,'Accept':accept}
 if token:headers['Authorization']='Bearer '+token
 if isinstance(data,dict):data=json.dumps(data).encode()
 return urlopen(Request(a.base_url+path,data=data,headers=headers),timeout=180)
def api(path,data=None):
 with call(path,data) as response:return json.load(response)
token=api('/api/v1/auth/register',{'email':'answer-eval-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24)})['accessToken']
ids={}
for doc in corpus['documents']:
 boundary='fixture'+uuid.uuid4().hex
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{doc["name"]}"\r\nContent-Type: text/plain\r\n\r\n{doc["text"]}\r\n--{boundary}--\r\n').encode()
 with call('/api/v1/documents',body,'multipart/form-data; boundary='+boundary) as response:ids[doc['name']]=json.load(response)['id']
for attempt in range(120):
 docs=api('/api/v1/documents?size=100')['content']
 if any(d['status']=='FAILED' for d in docs):raise RuntimeError('Fixture processing failed')
 if len(docs)==len(ids) and all(d['status']=='COMPLETED' for d in docs):break
 time.sleep(1)
else:raise RuntimeError('Fixture preparation timed out')
summary=api('/api/v1/workspace/summary')
if not summary['answersEnabled']:raise SystemExit('Enable local generation first')
rows=[]
def save():
 result={'fixture':'answer-corpus.json','mode':summary['searchMode'],'casesCompleted':len(rows),'checksPassed':sum(r['passed'] for r in rows),'limitations':'Small fictional dataset, one sequential run. Automated phrase, abstention and citation-reference checks require human review. Citation validity does not establish entailment. Timings include model warmup and are not load-test results.','results':rows}
 Path(a.output).parent.mkdir(parents=True,exist_ok=True)
 Path(a.output).write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
for case in corpus['cases']:
 start=time.perf_counter();answer='';sources=[];terminal=False;error=None;first=None;retrieved=None;event=''
 try:
  with call('/api/v1/chat',{'question':case['question'],'documentId':ids.get(case.get('scope'))},accept='text/event-stream, application/json') as response:
   for raw in response:
    line=raw.decode().strip()
    if line.startswith('event:'):event=line[6:].strip()
    if not line.startswith('data:'):continue
    payload=json.loads(line[5:].strip())
    if event=='sources':sources=payload;retrieved=time.perf_counter()-start
    if event=='token':
     if first is None:first=time.perf_counter()-start
     answer+=payload['delta']
    if event=='done':terminal=True
    if event=='error':error=payload.get('message');terminal=True
 except Exception as exc:error=str(exc)
 valid={s['number']:s for s in sources};refs=[int(n) for n in re.findall(r'\[(\d+)\]',answer)]
 cited={valid[n]['filename'] for n in refs if n in valid}
 checks={'completed':terminal and not error,'required_facts':all(re.search(p,answer,re.I) for p in case.get('must',[])),
 'forbidden_absent':not any(re.search(p,answer,re.I) for p in case.get('forbid',[])),
 'citation_numbers_valid':all(n in valid for n in refs),'expected_sources_cited':all(n in cited for n in case.get('cite',[])),
 'abstention':not case.get('abstain') or bool(re.search(r"not (?:specif|provid|mention|contain|found)|doesn.t (?:specif|provid|mention|contain)|don.t know|couldn.t find|no (?:information|supporting|relevant)|not available",answer,re.I))}
 row={'id':case['id'],'question':case['question'],'answer':answer,'sources':sources,'checks':checks,'passed':all(checks.values()),'error':error,'first_token_seconds':round(first,2) if first is not None else None,'retrieval_seconds':round(retrieved,2) if retrieved is not None else None,'total_seconds':round(time.perf_counter()-start,2)}
 rows.append(row);save();print(json.dumps({k:v for k,v in row.items() if k!='sources'},ensure_ascii=False),flush=True)
print(f"Completed: {sum(r['passed'] for r in rows)}/{len(rows)} cases passed automated checks.",flush=True)
raise SystemExit(0 if all(r['passed'] for r in rows) else 1)
