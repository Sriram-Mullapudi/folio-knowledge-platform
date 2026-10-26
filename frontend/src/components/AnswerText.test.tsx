import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { AnswerText } from './AnswerText';
afterEach(cleanup);
const noCitation=()=>null;
test('comparison cells preserve clickable citations, including bold citations',()=>{
 const open=vi.fn();
 render(<AnswerText text={'Policy comparison\n| Policy | Allowance |\n| --- | --- |\n| Meals | **$40 [1]** |'} citation={n=><button onClick={()=>open(n)}>Source {n}</button>}/>);
 expect(screen.getAllByRole('columnheader')).toHaveLength(2);
 expect(screen.getAllByRole('cell')[1].textContent).toBe('$40 Source 1');
 fireEvent.click(screen.getByRole('button',{name:'Source 1'}));
 expect(open).toHaveBeenCalledWith(1);
});
test('incomplete streamed rows remain visible until the row is complete',()=>{
 const {rerender}=render(<AnswerText text={'| Policy | Limit |\n| --- | --- |\n| Meals'} citation={noCitation}/>);
 expect(screen.getByText('| Meals')).toBeTruthy();
 rerender(<AnswerText text={'| Policy | Limit |\n| --- | --- |\n| Meals | $40 [9] |'} citation={noCitation}/>);
 expect(screen.getAllByRole('cell')[1].textContent).toBe('$40 [9]');
});
test('escaped pipes stay in cells and HTML remains inert text',()=>{
 const {container}=render(<AnswerText text={'Name | Value\r\n--- | ---\r\nA \\| B | <img src=x onerror=alert(1)>'} citation={noCitation}/>);
 expect(screen.getByRole('cell',{name:'A | B'})).toBeTruthy();
 expect(container.querySelector('img')).toBeNull();
 expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeTruthy();
});
test('lists following a heading do not require a blank line and retain numbering',()=>{
 render(<AnswerText text={'## Next steps\n3. Check policy\n4. Read sources\nDone.'} citation={noCitation}/>);
 expect(screen.getByRole('heading',{name:'Next steps'})).toBeTruthy();
 expect(screen.getByRole('list').getAttribute('start')).toBe('3');
 expect(screen.getAllByRole('listitem')).toHaveLength(2);
});
test('ordinary pipes and invalid table separators are not converted to tables',()=>{
 render(<AnswerText text={'A | B\n- | -\nplain | text'} citation={noCitation}/>);
 expect(screen.queryByRole('table')).toBeNull();
 expect(screen.getByText('plain | text')).toBeTruthy();
});
