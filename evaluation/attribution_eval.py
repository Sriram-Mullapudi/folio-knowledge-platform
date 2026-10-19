"""Live local-only answer evaluation with fictional fixtures; no third-party packages.
Checks are explicit heuristics, not an LLM judge or proof of factual correctness.
"""
import argparse,json,re,secrets,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.parse import urlparse
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8081')
parser.add_argument('--output',default='evaluation/attribution-results.json')
a=parser.parse_args()
if urlparse(a.base_url).hostname not in ('127.0.0.1','localhost'):raise SystemExit('Local evaluation only')
corpus=json.loads(Path(__file__).with_name('attribution-corpus.json').read_text(encoding='utf-8'))
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

results=[]
def ask(question, history, scope):
 start=time.perf_counter(); answer=''; sources=[]; done=False; event=''
 with call('/api/v1/chat', {'question':question,'history':history,'documentId':scope}, accept='text/event-stream, application/json') as response:
  for raw in response:
   line=raw.decode().strip()
   if line.startswith('event:'):event=line[6:].strip()
   if not line.startswith('data:'):continue
   data=json.loads(line[5:].strip())
   if event=='sources':sources=data
   if event=='token':answer+=data['delta']
   if event=='done':done=True
   if event=='error':raise RuntimeError(data)
 if not done:raise RuntimeError('Stream ended early')
 row={'question':question,'answer':answer,'sources':sources,'seconds':round(time.perf_counter()-start,2)}
 results.append(row);print(json.dumps(row),flush=True)
 return row

scope=ids['fictional-resume.txt']
first=ask('What did the candidate build at Cedar Health?',[],scope)
history=[{'question':first['question'],'answer':first['answer'][:1000]}]
follow=ask('Which technologies did he use there?',history,scope)
switch=ask('Which technologies did he use at Birch Payments?',history,scope)
checks={
 'initial_grounded': 'scheduling' in first['answer'].lower() and bool(re.search(r'\[\d+\]',first['answer'])),
 'followup_required': all(x.lower() in follow['answer'].lower() for x in ['Java','Spring Boot','React','PostgreSQL']),
 'followup_no_other_role_or_general_skills': not re.search(r'\b(Rust|Kubernetes|Terraform|MongoDB|Python|Go|Redis|Kafka)\b',follow['answer'],re.I),
 'followup_cited':bool(re.search(r'\[\d+\]',follow['answer'])),
 'topic_switch':all(x.lower() in switch['answer'].lower() for x in ['Go','Redis','Kafka']) and not re.search(r'\b(Java|React|PostgreSQL|Rust|Kubernetes)\b',switch['answer'],re.I),
 'switch_cited':bool(re.search(r'\[\d+\]',switch['answer']))}
Path(a.output).write_text(json.dumps({'checks':checks,'passed':all(checks.values()),'limitations':'One fictional resume and three live answers. Phrase checks require manual review, including negative mentions; not general attribution accuracy.','results':results},indent=2)+'\n',encoding='utf-8')
print(json.dumps(checks),flush=True)
raise SystemExit(0 if all(checks.values()) else 1)
