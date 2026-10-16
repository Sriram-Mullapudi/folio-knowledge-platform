import type { ReactNode } from "react";

/** A deliberately small Markdown subset. Model output is always rendered as text, never HTML. */
export function AnswerText({text,citation}:{text:string;citation:(number:number)=>ReactNode}) {
 const inline=(line:string)=>line.split(/(\[\d+\]|\*\*[^*]+\*\*)/g).map((part,i)=>{
  if(/^\[\d+\]$/.test(part))return <span key={i}>{citation(Number(part.slice(1,-1)))??part}</span>;
  return part.startsWith("**")&&part.endsWith("**")?<strong key={i}>{part.slice(2,-2)}</strong>:<span key={i}>{part}</span>;
 });
 return <>{text.split(/\n\s*\n/).map((block,i)=>{
  const lines=block.split("\n");
  if(lines.every(line=>/^[-*] /.test(line)))return <ul key={i}>{lines.map((line,j)=><li key={j}>{inline(line.slice(2))}</li>)}</ul>;
  if(lines.every(line=>/^\d+\. /.test(line)))return <ol key={i}>{lines.map((line,j)=><li key={j}>{inline(line.replace(/^\d+\. /,""))}</li>)}</ol>;
  return <div className="answer-paragraph" key={i}>{lines.map((line,j)=>/^#{1,6} /.test(line)?<h3 key={j}>{inline(line.replace(/^#{1,6} /,""))}</h3>:<div key={j}>{inline(line)||" "}</div>)}</div>;
 })}</>;
}
