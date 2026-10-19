"""Live local-only answer evaluation with fictional fixtures; no third-party packages.
Checks are explicit heuristics, not an LLM judge or proof of factual correctness.
"""
import argparse,json,re,secrets,time,uuid
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.parse import urlparse
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8081')
parser.add_argument('--output',default='evaluation/section-results.json')
a=parser.parse_args()
if urlparse(a.base_url).hostname not in ('127.0.0.1','localhost'):raise SystemExit('Local evaluation only')
corpus=json.loads(Path(__file__).with_name('section-corpus.json').read_text(encoding='utf-8'))
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
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{doc["name"]}"\r\nContent-Type: {('text/markdown' if doc['name'].endswith('.md') else 'text/plain')}\r\n\r\n{doc["text"]}\r\n--{boundary}--\r\n').encode()
 with call('/api/v1/documents',body,'multipart/form-data; boundary='+boundary) as response:ids[doc['name']]=json.load(response)['id']
for attempt in range(120):
 docs=api('/api/v1/documents?size=100')['content']
 if any(d['status']=='FAILED' for d in docs):raise RuntimeError('Fixture processing failed')
 if len(docs)==len(ids) and all(d['status']=='COMPLETED' for d in docs):break
 time.sleep(1)
else:raise RuntimeError('Fixture preparation timed out')
summary=api('/api/v1/workspace/summary')



resume=ids['section-resume.txt']; policy=ids['section-policy.md']
chunks=api('/api/v1/documents/'+resume+'/chunks?size=100')['content']
assert len(chunks)>3
assert all(not ('Cedar Health' in c['content'] and ('CopperLedger' in c['content'] or 'Terraform' in c['content'])) for c in chunks)
late=[c for c in chunks if 'CobaltQueue' in c['content']]
assert late and all(c['content'].startswith('Cedar Health') for c in late)
hits=api('/api/v1/search',{'query':'Cedar Health CobaltQueue','documentId':resume,'k':5})
assert any('CobaltQueue' in h['content'] and 'Cedar Health' in h['content'] for h in hits)
mdhits=api('/api/v1/search',{'query':'Atlas retention 63','documentId':policy,'k':5})
assert any('# Atlas' in h['content'] and '63' in h['content'] for h in mdhits)
assert all('Boreal' not in h['content'] for h in mdhits)
original=[c['content'] for c in chunks]
api('/api/v1/documents/'+resume+'/reprocess',{})
for _ in range(120):
 docs=api('/api/v1/documents?size=100')['content']
 status=next(d['status'] for d in docs if d['id']==resume)
 if status=='COMPLETED':break
 if status=='FAILED':raise RuntimeError('Reprocessing failed')
 time.sleep(1)
else:raise RuntimeError('Reprocessing timed out')
assert original==[c['content'] for c in api('/api/v1/documents/'+resume+'/chunks?size=100')['content']]
owner_token=token;token=None
token=api('/api/v1/auth/register',{'email':'sections-other-'+uuid.uuid4().hex+'@example.test','password':secrets.token_urlsafe(24)})['accessToken']
assert api('/api/v1/search',{'query':'Cedar Health CobaltQueue','documentId':resume,'k':5})==[]
token=owner_token
Path(a.output).write_text(json.dumps({'passed':True,'checks':['long-role continuation retains heading','no cross-role/skills overlap','named continuation retrieved','Markdown hierarchy retrieved','reprocessing idempotent','cross-owner search excluded'],'resumeChunkCount':len(chunks),'resumeHits':hits,'policyHits':mdhits,'limitations':'Two fictional text/Markdown documents. No OCR, PDF layout or general heading-recognition accuracy claim.'},indent=2)+'\n',encoding='utf-8')
print('All six live section-processing checks passed.',flush=True)
