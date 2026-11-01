import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ProductWorkspace } from './ProductWorkspace';
import { apiGet, apiPost } from '../api/client';
import { listDocuments } from '../api/documents';
import { streamAnswer } from '../api/chat';
vi.mock('../api/client',async importOriginal=>({...await importOriginal<typeof import('../api/client')>(),apiGet:vi.fn(),apiPost:vi.fn()}));
vi.mock('../api/documents',async importOriginal=>({...await importOriginal<typeof import('../api/documents')>(),listDocuments:vi.fn()}));
vi.mock('../api/chat',()=>({streamAnswer:vi.fn()}));
beforeEach(()=>{
 sessionStorage.clear();
 Element.prototype.scrollIntoView=vi.fn();
 vi.mocked(apiGet).mockImplementation(async path=>{
  if(path==='/api/v1/collections')return [{id:'team',name:'Team',count:2},{id:'other',name:'Other',count:2}] as never;
  return {documents:2,ready:2,processing:0,failed:0,answersEnabled:true,searchMode:'hybrid'} as never;
 });
 vi.mocked(listDocuments).mockResolvedValue({content:['a','b'].map(id=>({id,filename:`${id}.txt`,contentType:'text/plain',sizeBytes:30,status:'COMPLETED',chunkCount:1,uploadedAt:'2026-01-01',processedAt:null,processingError:null,uploadedBy:'demo',collectionId:'team'})),page:0,size:20,totalElements:2,totalPages:1});
});
afterEach(()=>{cleanup();vi.resetAllMocks();});
async function setup(){
 render(<ProductWorkspace user={{id:'demo',email:'demo@example.test',role:'USER',enabled:true,createdAt:'2026-01-01'}} onLogout={async()=>{}} signingOut={false}/>);
 await screen.findByRole('option',{name:'a.txt'});
 await waitFor(()=>expect((screen.getByLabelText('Answer mode') as HTMLSelectElement).value).toBe('RETRIEVAL'));
}
function select(label:string,value:string){fireEvent.change(screen.getByLabelText(label),{target:{value}});}
async function ask(question:string){
 fireEvent.change(screen.getByRole('textbox',{name:'Your question or search terms'}),{target:{value:question}});
 fireEvent.click(screen.getByRole('button',{name:'Send question'}));
 await screen.findByRole('button',{name:'Try again'});
}
test('retry retains original file, collection, and mode while keeping the new draft',async()=>{
 vi.mocked(streamAnswer).mockImplementation(async(_q,_o,h)=>{h.onError('Temporary failure');});
 await setup();select('Current collection','team');select('Search in documents','a');
 await ask('What is the allowance?');
 select('Current collection','other');select('Search in documents','b');select('Answer mode','SEARCH');
 const draft=screen.getByRole('textbox',{name:'Your question or search terms'});
 fireEvent.change(draft,{target:{value:'My next question'}});
 fireEvent.click(screen.getByRole('button',{name:'Try again'}));
 await waitFor(()=>expect(streamAnswer).toHaveBeenCalledTimes(2));
 expect(vi.mocked(streamAnswer).mock.calls[1][1]).toMatchObject({documentId:'a',collectionId:'team'});
 expect(apiPost).not.toHaveBeenCalled();
 expect((draft as HTMLTextAreaElement).value).toBe('My next question');
 expect((screen.getByLabelText('Search in documents') as HTMLSelectElement).value).toBe('b');
});
test('retrying a failed passage search does not switch to AI after the mode changes',async()=>{
 vi.mocked(apiPost).mockRejectedValue(new Error('Search unavailable'));
 await setup();select('Answer mode','SEARCH');select('Search in documents','a');
 fireEvent.change(screen.getByRole('textbox',{name:'Your question or search terms'}),{target:{value:'leave'}});
 fireEvent.click(screen.getByRole('button',{name:'Search documents'}));
 await screen.findByRole('button',{name:'Try again'});
 select('Answer mode','RETRIEVAL');select('Search in documents','b');
 fireEvent.click(screen.getByRole('button',{name:'Try again'}));
 await waitFor(()=>expect(apiPost).toHaveBeenCalledTimes(2));
 expect(vi.mocked(apiPost).mock.calls[1]).toEqual(['/api/v1/search',{query:'leave',documentId:'a',collectionId:null,k:6}]);
 expect(streamAnswer).not.toHaveBeenCalled();
});
