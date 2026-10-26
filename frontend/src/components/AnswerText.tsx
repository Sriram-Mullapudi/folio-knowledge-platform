import type { ReactNode } from "react";

function cells(line: string) {
 const row = line.trim().replace(/^\|/, "").replace(/(?<!\\)\|$/, "");
 return row.split(/(?<!\\)\|/).map(cell => cell.trim().replace(/\\\|/g, "|"));
}

/** A small Markdown subset. Model output stays text, never executable HTML. */
export function AnswerText({text,citation}:{text:string;citation:(number:number)=>ReactNode}) {
 const inline=(line:string):ReactNode[]=>line.split(/(\[\d+\]|\*\*[^*]+\*\*)/g).map((part,i)=>{
  if(/^\[\d+\]$/.test(part))return <span key={i}>{citation(Number(part.slice(1,-1)))??part}</span>;
  return part.startsWith("**")&&part.endsWith("**")?<strong key={i}>{inline(part.slice(2,-2))}</strong>:<span key={i}>{part}</span>;
 });
 const lines=text.replace(/\r\n/g,"\n").split("\n");
 const blocks:ReactNode[]=[];
 for(let i=0;i<lines.length;) {
  if(!lines[i].trim()){i++;continue;}
  const start=i,headers=cells(lines[i]),separator=cells(lines[i+1]??"");
  if(lines[i].includes("|")&&headers.length>1&&separator.length===headers.length&&separator.every(cell=>/^:?-{3,}:?$/.test(cell))) {
   i+=2;
   const rows:string[][]=[];
   while(i<lines.length&&lines[i].includes("|")&&cells(lines[i]).length===headers.length)rows.push(cells(lines[i++]));
   blocks.push(<div className="answer-table-scroll" role="region" aria-label="Answer comparison table" tabIndex={0} key={start}><table><thead><tr>{headers.map((cell,j)=><th scope="col" key={j}>{inline(cell)}</th>)}</tr></thead><tbody>{rows.map((row,j)=><tr key={j}>{row.map((cell,k)=><td key={k}>{inline(cell)}</td>)}</tr>)}</tbody></table></div>);
   continue;
  }
  const ordered=/^\d+\. /.test(lines[i]),unordered=/^[-*] /.test(lines[i]);
  if(ordered||unordered) {
   const items:ReactNode[]=[],pattern=ordered?/^\d+\. /:/^[-*] /;
   const first=ordered?Number.parseInt(lines[i],10):1;
   while(i<lines.length&&pattern.test(lines[i])){items.push(<li key={i}>{inline(lines[i].replace(pattern,""))}</li>);i++;}
   blocks.push(ordered?<ol start={first} key={start}>{items}</ol>:<ul key={start}>{items}</ul>);
   continue;
  }
  blocks.push(/^#{1,6} /.test(lines[i])?<h3 key={i}>{inline(lines[i].replace(/^#{1,6} /,""))}</h3>:<div className="answer-paragraph" key={i}>{inline(lines[i])}</div>);
  i++;
 }
 return <>{blocks}</>;
}
