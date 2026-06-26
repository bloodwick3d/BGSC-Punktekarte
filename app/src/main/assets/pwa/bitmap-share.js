'use strict';

// v47: Browser port of the native BitmapGenerator.kt.
// It deliberately renders from the saved game data instead of taking a DOM screenshot,
// so all players, rounds and 18 holes are included independent of scroll position.

const BITMAP_CACHE={assetsPromise:null};
const BITMAP_RENDER_CACHE=new Map();
const BITMAP_DB_NAME='minigolf_bitmap_cache_v50';
const BITMAP_DB_STORE='generated';
const BITMAP_CACHE_VERSION='native-v50';

function bmLoadImage(src){
  return new Promise((resolve,reject)=>{
    const img=new Image(); img.decoding='async';
    img.onload=()=>resolve(img); img.onerror=()=>reject(new Error(`Asset konnte nicht geladen werden: ${src}`));
    img.src=src;
  });
}
async function bmLoadDrawable(src){
  if('createImageBitmap' in window){
    try{
      const response=await fetch(src,{cache:'force-cache'});
      if(!response.ok)throw new Error(String(response.status));
      return await createImageBitmap(await response.blob(),{premultiplyAlpha:'default',colorSpaceConversion:'default'});
    }catch(err){console.warn('ImageBitmap fallback',src,err)}
  }
  return bmLoadImage(src);
}
async function bmAssets(){
  BITMAP_CACHE.assetsPromise ||= Promise.all([
    bmLoadDrawable('assets/bg_minigolf.jpg'),
    bmLoadDrawable('assets/minigolf_logo.png')
  ]).then(([background,logo])=>({background,logo}));
  return BITMAP_CACHE.assetsPromise;
}
function bmDateParts(value){
  const d=new Date(value||Date.now()), pad=n=>String(n).padStart(2,'0');
  return {date:`${pad(d.getDate())}.${pad(d.getMonth()+1)}.${d.getFullYear()}`,time:`${pad(d.getHours())}:${pad(d.getMinutes())}`};
}
function bmPlayers(game){
  const raw=Array.isArray(game?.players)&&game.players.length?game.players:defaultState().players;
  return raw.map((p,i)=>({
    name:String(p?.name||`Spieler ${i+1}`),
    color:String(p?.color||PLAYER_COLORS[i%PLAYER_COLORS.length]),
    rounds:Array.isArray(p?.roundScores)&&p.roundScores.length
      ?p.roundScores.map(r=>Array.from({length:18},(_,h)=>r?.[h]??null))
      :[blankRound()]
  }));
}
function bmRgb(hex){
  let v=String(hex||'#000').replace('#',''); if(v.length===3)v=v.split('').map(x=>x+x).join('');
  const n=parseInt(v,16); return Number.isFinite(n)?{r:(n>>16)&255,g:(n>>8)&255,b:n&255}:{r:0,g:0,b:0};
}
function bmRgba(hex,a){const c=bmRgb(hex);return `rgba(${c.r},${c.g},${c.b},${a})`}
function bmCover(ctx,img,w,h){
  const iw=img.naturalWidth||img.width,ih=img.naturalHeight||img.height;
  const k=Math.max(w/iw,h/ih),sw=w/k,sh=h/k;
  ctx.drawImage(img,(iw-sw)/2,(ih-sh)/2,sw,sh,0,0,w,h);
}
function bmRoundedPath(ctx,x,y,w,h,{tl=0,tr=0,br=0,bl=0}={}){
  tl=Math.min(tl,w/2,h/2);tr=Math.min(tr,w/2,h/2);br=Math.min(br,w/2,h/2);bl=Math.min(bl,w/2,h/2);
  ctx.beginPath();ctx.moveTo(x+tl,y);ctx.lineTo(x+w-tr,y);tr?ctx.quadraticCurveTo(x+w,y,x+w,y+tr):ctx.lineTo(x+w,y);
  ctx.lineTo(x+w,y+h-br);br?ctx.quadraticCurveTo(x+w,y+h,x+w-br,y+h):ctx.lineTo(x+w,y+h);
  ctx.lineTo(x+bl,y+h);bl?ctx.quadraticCurveTo(x,y+h,x,y+h-bl):ctx.lineTo(x,y+h);
  ctx.lineTo(x,y+tl);tl?ctx.quadraticCurveTo(x,y,x+tl,y):ctx.lineTo(x,y);ctx.closePath();
}
function bmRoundFill(ctx,x,y,w,h,r,color){bmRoundedPath(ctx,x,y,w,h,r);ctx.fillStyle=color;ctx.fill()}
function bmText(ctx,text,x,y,{size=20,bold=false,color='#fff',align='center',baseline='middle',shadow=true}={}){
  ctx.save();ctx.font=`${bold?'700':'400'} ${size}px Calibri, Arial, sans-serif`;ctx.fillStyle=color;ctx.textAlign=align;ctx.textBaseline=baseline;
  if(shadow){ctx.shadowColor='rgba(0,0,0,.7)';ctx.shadowBlur=4;ctx.shadowOffsetX=2;ctx.shadowOffsetY=2}
  ctx.fillText(String(text),x,y);ctx.restore();
}
function bmIcon(ctx,name,x,y,size,color){
  const d=ICONS[name];if(!d)return;ctx.save();ctx.translate(x,y);ctx.scale(size/24,size/24);ctx.fillStyle=color;ctx.fill(new Path2D(d));ctx.restore();
}
function bmLine(ctx,x1,y1,x2,y2,width=2,color='#d3d3d3'){ctx.save();ctx.strokeStyle=color;ctx.lineWidth=width;ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();ctx.restore()}
function bmRoundSum(r){return (r||[]).reduce((a,b)=>a+(Number(b)||0),0)}
function bmTotal(p){return (p.rounds||[]).reduce((a,r)=>a+bmRoundSum(r),0)}
function bmPlayed(rounds){return (rounds||[]).flat().filter(v=>v!=null&&Number(v)>0).length}
function bmScoreColor(total,system,rounds,played=0){
  if(total===0)return '#000'; if(rounds<=0)return '#fff';
  const effective=played>0?(rounds*18)+(total-played):total, avg=effective/rounds, sys=String(system||'');
  if(sys.includes('Eternit'))return avg<20?'#2196F3':avg<25?'#4CAF50':avg<30?'#F44336':'#000';
  if(sys.includes('Beton'))return avg<25?'#2196F3':avg<30?'#4CAF50':avg<36?'#F44336':'#000';
  return avg<30?'#2196F3':avg<36?'#4CAF50':avg<40?'#F44336':'#000';
}
function bmBlob(canvas){return new Promise((res,rej)=>canvas.toBlob(b=>b?res(b):rej(new Error('PNG-Erzeugung fehlgeschlagen')),'image/png',1))}

function bmGameKey(game){
  const players=bmPlayers(game).map(p=>({name:p.name,color:p.color,rounds:p.rounds}));
  const source=JSON.stringify({v:BITMAP_CACHE_VERSION,id:game?.id||'',date:game?.date||'',system:game?.system||'',location:game?.location||'',hasStats:!!game?.hasStats,players});
  let hash=2166136261;
  for(let i=0;i<source.length;i++){hash^=source.charCodeAt(i);hash=Math.imul(hash,16777619)}
  return `${BITMAP_CACHE_VERSION}:${(hash>>>0).toString(36)}:${source.length}`;
}
function bmOpenDb(){
  return new Promise((resolve,reject)=>{
    if(!('indexedDB' in window)){resolve(null);return}
    const req=indexedDB.open(BITMAP_DB_NAME,1);
    req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains(BITMAP_DB_STORE))db.createObjectStore(BITMAP_DB_STORE,{keyPath:'id'})};
    req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);
  }).catch(()=>null);
}
async function bmDbRead(id){
  const db=await bmOpenDb();if(!db)return null;
  return new Promise(resolve=>{const tx=db.transaction(BITMAP_DB_STORE,'readonly'),req=tx.objectStore(BITMAP_DB_STORE).get(id);req.onsuccess=()=>resolve(req.result?.blob||null);req.onerror=()=>resolve(null);tx.oncomplete=()=>db.close()});
}
async function bmDbWrite(id,blob){
  const db=await bmOpenDb();if(!db)return;
  await new Promise(resolve=>{const tx=db.transaction(BITMAP_DB_STORE,'readwrite');tx.objectStore(BITMAP_DB_STORE).put({id,blob,created:Date.now()});tx.oncomplete=resolve;tx.onerror=resolve});db.close();
}
function bmYield(){return new Promise(resolve=>requestAnimationFrame(()=>setTimeout(resolve,0)))}
function bmPackageItem(blob,name,label){return {blob,name,label,url:URL.createObjectURL(blob)}}
async function ensureGameBitmapPackage(game,{persist=true}={}){
  const key=bmGameKey(game);
  if(BITMAP_RENDER_CACHE.has(key))return BITMAP_RENDER_CACHE.get(key);
  const promise=(async()=>{
    const scoreId=`${key}:score`,statsId=`${key}:stats`;
    let scoreBlob=await bmDbRead(scoreId),statsBlob=game?.hasStats?await bmDbRead(statsId):null;
    if(!scoreBlob){
      await bmAssets();await bmYield();
      scoreBlob=await bmBlob(await generateResultBitmapCanvas(game));
      if(persist)bmDbWrite(scoreId,scoreBlob);
    }
    if(game?.hasStats&&!statsBlob){
      await bmYield();
      statsBlob=await bmBlob(await generateTrackStatsBitmapCanvas(game));
      if(persist)bmDbWrite(statsId,statsBlob);
    }
    const items=[bmPackageItem(scoreBlob,bmFileName(game,'score_table'),'Ergebniskarte')];
    if(statsBlob)items.push(bmPackageItem(statsBlob,bmFileName(game,'track_stats'),'Bahnstatistik'));
    return {key,items,scoreBlob,statsBlob};
  })().catch(err=>{BITMAP_RENDER_CACHE.delete(key);throw err});
  BITMAP_RENDER_CACHE.set(key,promise);return promise;
}
function bmSpinnerMarkup(){return `<div class="bitmapLoading"><span class="bitmapSpinner"></span><span>Ergebnisbild wird erstellt …</span></div>`}
async function hydrateHistoryBitmapPreview(card,game){
  const host=card?.querySelector('.nativeBitmapHost');if(!host)return;
  host.innerHTML=bmSpinnerMarkup();
  try{
    const pkg=await ensureGameBitmapPackage(game);
    if(!host.isConnected)return;
    host.innerHTML=`<div class="nativeBitmapPreviewRow">${pkg.items.map((item,i)=>`<button type="button" class="nativeBitmapThumb" data-bitmap-index="${i}" aria-label="${item.label} öffnen"><img src="${item.url}" alt="${item.label}"><span class="nativeBitmapShade"></span><span class="nativeBitmapOverlayIcon">${typeof icon==='function'?icon(i===0?'zoom_in':'bar_chart'):''}</span></button>`).join('')}</div>`;
    host.querySelectorAll('[data-bitmap-index]').forEach(btn=>btn.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();openGameBitmapPreview(game,Number(btn.dataset.bitmapIndex)||0)}));
  }catch(err){console.error(err);if(host.isConnected)host.innerHTML='<div class="bitmapError">Ergebnisbild konnte nicht erstellt werden.</div>'}
}
async function bmSaveBlob(blob,name){
  try{
    if('showSaveFilePicker' in window){
      const handle=await showSaveFilePicker({suggestedName:name,types:[{description:'PNG-Bild',accept:{'image/png':['.png']}}]});
      const writable=await handle.createWritable();await writable.write(blob);await writable.close();toast('Bild gespeichert');return;
    }
  }catch(err){if(err?.name==='AbortError')return}
  bmDownload(blob,name);toast('Download gestartet');
}
function openGameBitmapPreview(game,initialIndex=0){
  ensureGameBitmapPackage(game).then(pkg=>{
    let index=Math.max(0,Math.min(initialIndex,pkg.items.length-1)),scale=1,tx=0,ty=0,startX=0,startY=0,baseX=0,baseY=0,pointerId=null,moved=false;
    const overlay=document.createElement('div');overlay.className='bitmapFullscreenOverlay';overlay.innerHTML=`<button class="bitmapTopButton save" aria-label="Bild speichern">${typeof icon==='function'?icon('save_alt'):''}</button><button class="bitmapTopButton close" aria-label="Schließen">${typeof icon==='function'?icon('close'):''}</button><div class="bitmapFullscreenStage"><img draggable="false" alt="Ergebnisvorschau"></div><div class="bitmapPagerDots"></div>`;
    const img=overlay.querySelector('img'),stage=overlay.querySelector('.bitmapFullscreenStage'),dots=overlay.querySelector('.bitmapPagerDots');
    const apply=()=>{img.style.transform=`translate3d(${tx}px,${ty}px,0) scale(${scale})`};
    const render=()=>{const item=pkg.items[index];img.src=item.url;img.alt=item.label;scale=1;tx=ty=0;apply();dots.innerHTML=pkg.items.length>1?pkg.items.map((_,i)=>`<i class="${i===index?'active':''}"></i>`).join(''):''};
    overlay.querySelector('.close').onclick=()=>overlay.remove();overlay.querySelector('.save').onclick=e=>{e.stopPropagation();const item=pkg.items[index];bmSaveBlob(item.blob,item.name)};
    overlay.addEventListener('click',e=>{if(e.target===overlay||e.target===stage)overlay.remove()});
    img.addEventListener('dblclick',e=>{e.preventDefault();scale=scale>1.1?1:2.5;if(scale===1)tx=ty=0;apply()});
    img.addEventListener('wheel',e=>{e.preventDefault();scale=Math.max(1,Math.min(5,scale+(e.deltaY<0?.35:-.35)));if(scale===1)tx=ty=0;apply()},{passive:false});
    img.addEventListener('pointerdown',e=>{if(e.button!=null&&e.pointerType==='mouse'&&e.button!==0)return;pointerId=e.pointerId;startX=e.clientX;startY=e.clientY;baseX=tx;baseY=ty;moved=false;try{img.setPointerCapture(pointerId)}catch(_){};e.preventDefault()},{passive:false});
    img.addEventListener('pointermove',e=>{if(e.pointerId!==pointerId)return;const dx=e.clientX-startX,dy=e.clientY-startY;if(Math.abs(dx)>4||Math.abs(dy)>4)moved=true;if(scale>1){tx=baseX+dx;ty=baseY+dy;apply()}e.preventDefault()},{passive:false});
    img.addEventListener('pointerup',e=>{if(e.pointerId!==pointerId)return;const dx=e.clientX-startX,dy=e.clientY-startY;pointerId=null;if(scale===1&&pkg.items.length>1&&Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)){index=(index+(dx<0?1:-1)+pkg.items.length)%pkg.items.length;render()}e.preventDefault()},{passive:false});
    document.body.appendChild(overlay);render();
  }).catch(err=>{console.error(err);toast('Vorschau konnte nicht geöffnet werden')});
}
function prewarmGameBitmap(game){
  const run=()=>ensureGameBitmapPackage(game).catch(()=>{});
  if('requestIdleCallback' in window)requestIdleCallback(run,{timeout:3500});else setTimeout(run,1400);
}

async function generateResultBitmapCanvas(game){
  const players=bmPlayers(game),rounds=Math.max(1,players[0]?.rounds?.length||1),hasStats=!!game?.hasStats,s=2;
  const sticky=35*s,pw=100*s,statsW=35*s,gap=2*s,side=10*s,bottom=10*s,headH=40*s,rowH=25*s;
  const footH=(rounds>1?50:35)*s,section=2*s,logoW=70*s,metaH=60*s;
  const extra=hasStats?(players.length+1)*statsW:0;
  const tableW=sticky+gap+players.length*(pw+gap)+extra+sticky,tableH=headH+section+18*rowH+section+footH;
  const tableL=logoW+side,tableR=tableL+tableW,W=tableR+side,H=metaH+tableH+bottom,tableB=metaH+tableH;
  const canvas=document.createElement('canvas');canvas.width=Math.round(W);canvas.height=Math.round(H);const ctx=canvas.getContext('2d',{alpha:false});
  const {background,logo}=await bmAssets();bmCover(ctx,background,W,H);
  const dt=bmDateParts(game?.date),iconSize=10*s,iconX=side+5*s;
  bmIcon(ctx,'calendar_month',iconX,20*s-iconSize,iconSize,'#d3d3d3');bmIcon(ctx,'clock',iconX,35*s-iconSize,iconSize,'#d3d3d3');
  bmText(ctx,dt.date,side+19*s,20*s,{size:8*s,color:'#d3d3d3',align:'left',baseline:'alphabetic'});
  bmText(ctx,dt.time,side+19*s,35*s,{size:8*s,color:'#d3d3d3',align:'left',baseline:'alphabetic'});
  const system=String(game?.system||SYSTEMS[0]).replace(/\n/g,' '),location=String(game?.location||'').trim();
  bmText(ctx,system,tableR,25*s,{size:10*s,bold:true,align:'right',baseline:'alphabetic'});if(location)bmText(ctx,location,tableR,40*s,{size:10*s,bold:true,align:'right',baseline:'alphabetic'});
  const logoCX=side+logoW/2,tableCY=metaH+headH+12.2*rowH;ctx.save();ctx.translate(logoCX,tableCY);ctx.rotate(-Math.PI/2);bmText(ctx,'MiniGolf Punktekarte',0,0,{size:21*s,bold:true});ctx.restore();
  const logoSize=logoW*.8;ctx.drawImage(logo,side+(logoW-logoSize)/2,tableB-logoSize-7*s,logoSize,logoSize);
  let x=tableL,y;const radius=15*s;
  bmRoundFill(ctx,x,metaH,sticky,headH,{tl:radius},'rgba(0,0,0,.4)');y=metaH+headH+section;
  for(let i=1;i<=18;i++){ctx.fillStyle=i%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,sticky,rowH);bmText(ctx,i,x+sticky/2,y+rowH/2,{size:12*s,bold:true});y+=rowH}
  bmRoundFill(ctx,x,y+section,sticky,tableB-y-section,{bl:radius},'rgba(0,0,0,.4)');x+=sticky+gap;
  const allHoleSum=Array(18).fill(0),allHoleCount=Array(18).fill(0);let allPoints=0;
  for(const p of players){
    ctx.fillStyle=p.color;ctx.fillRect(x,metaH,pw,headH);const name=p.name.length>12?p.name.slice(0,10)+'..':p.name;bmText(ctx,name,x+pw/2,metaH+headH/2,{size:14*s,bold:true});
    y=metaH+headH+section;const rw=pw/rounds,pAvg=Array(18).fill(0);
    for(let h=0;h<18;h++){
      ctx.fillStyle='#fff';ctx.fillRect(x,y,pw,rowH);ctx.fillStyle=bmRgba(p.color,(h+1)%2===0?51/255:25/255);ctx.fillRect(x,y,pw,rowH);
      let hs=0,hc=0;
      for(let r=0;r<rounds;r++){
        const v=p.rounds?.[r]?.[h];if(v!=null){bmText(ctx,v,x+r*rw+rw/2,y+rowH/2,{size:12*s,color:'#000',shadow:false});hs+=Number(v)||0;hc++;allHoleSum[h]+=Number(v)||0;allHoleCount[h]++}
        if(rounds>1&&r<rounds-1)bmLine(ctx,x+(r+1)*rw,y,x+(r+1)*rw,y+rowH,1*s);
      }
      if(hc)pAvg[h]=hs/hc;y+=rowH;
    }
    y+=section;ctx.fillStyle='#fff';ctx.fillRect(x,y,pw,tableB-y);ctx.fillStyle=bmRgba(p.color,25/255);ctx.fillRect(x,y,pw,tableB-y);
    const total=bmTotal(p);
    if(rounds===1){bmText(ctx,total,x+pw/2,y+(tableB-y)/2,{size:16*s,bold:true,color:bmScoreColor(total,system,1,bmPlayed(p.rounds))})}
    else{
      const half=(tableB-y)/2;
      for(let r=0;r<rounds;r++){const sum=bmRoundSum(p.rounds[r]),played=(p.rounds[r]||[]).filter(v=>v!=null&&Number(v)>0).length;bmText(ctx,sum,x+r*rw+rw/2,y+half/2,{size:11*s,bold:true,color:bmScoreColor(sum,system,1,played)});if(r<rounds-1)bmLine(ctx,x+(r+1)*rw,y,x+(r+1)*rw,y+half,1*s)}
      bmLine(ctx,x,y+half,x+pw,y+half,1*s);bmText(ctx,total,x+pw/2,y+half+half/2,{size:14*s,bold:true,color:bmScoreColor(total,system,rounds,bmPlayed(p.rounds))});
    }
    allPoints+=total;x+=pw;
    if(hasStats){
      ctx.fillStyle='rgba(0,0,0,.4)';ctx.fillRect(x,metaH,statsW,headH);bmText(ctx,'Ø',x+statsW/2,metaH+headH/2,{size:10*s,bold:true});y=metaH+headH+section;
      for(let h=0;h<18;h++){ctx.fillStyle=(h+1)%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,statsW,rowH);if(pAvg[h]>0)bmText(ctx,pAvg[h].toFixed(1),x+statsW/2,y+rowH/2,{size:10*s});y+=rowH}
      y+=section;ctx.fillStyle='rgba(0,0,0,.4)';ctx.fillRect(x,y,statsW,tableB-y);bmText(ctx,(total/rounds).toFixed(1),x+statsW/2,y+(tableB-y)/2,{size:10*s,bold:true});x+=statsW;
    }
    x+=gap;
  }
  if(hasStats){
    ctx.fillStyle='rgba(0,0,0,.4)';ctx.fillRect(x,metaH,statsW,headH);bmText(ctx,'ALL',x+statsW/2,metaH+headH/2,{size:10*s,bold:true});y=metaH+headH+section;
    for(let h=0;h<18;h++){ctx.fillStyle=(h+1)%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,statsW,rowH);if(allHoleCount[h])bmText(ctx,(allHoleSum[h]/allHoleCount[h]).toFixed(1),x+statsW/2,y+rowH/2,{size:10*s,bold:true});y+=rowH}
    y+=section;ctx.fillStyle='rgba(0,0,0,.4)';ctx.fillRect(x,y,statsW,tableB-y);bmText(ctx,(allPoints/(players.length*rounds)).toFixed(1),x+statsW/2,y+(tableB-y)/2,{size:10*s,bold:true});x+=statsW+gap;
  }
  bmRoundFill(ctx,x,metaH,sticky,headH,{tr:radius},'rgba(0,0,0,.4)');y=metaH+headH+section;
  for(let i=1;i<=18;i++){ctx.fillStyle=i%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,sticky,rowH);bmText(ctx,i,x+sticky/2,y+rowH/2,{size:12*s,bold:true});y+=rowH}
  bmRoundFill(ctx,x,y+section,sticky,tableB-y-section,{br:radius},'rgba(0,0,0,.4)');return canvas;
}

async function generateTrackStatsBitmapCanvas(game){
  const players=bmPlayers(game),s=2,sticky=35*s,scoreW=45*s,side=16*s,bottom=16*s,headH=40*s,rowH=25*s,metaH=60*s,section=2*s,gap=2*s;
  const W=2*side+2*sticky+8*gap+7*scoreW,tableH=headH+section+18*rowH+section+rowH,H=metaH+tableH+bottom,tableB=metaH+tableH;
  const canvas=document.createElement('canvas');canvas.width=W;canvas.height=H;const ctx=canvas.getContext('2d',{alpha:false}),{background}=await bmAssets();bmCover(ctx,background,W,H);
  bmText(ctx,'Bahnstatistik - Trefferverteilung',W/2,35*s,{size:18*s,bold:true,baseline:'alphabetic'});const dt=bmDateParts(game?.date),system=String(game?.system||SYSTEMS[0]).replace(/\n/g,' ');bmText(ctx,`${system} • ${dt.date}`,W/2,50*s,{size:10*s,color:'#d3d3d3',baseline:'alphabetic'});
  const stats=Array.from({length:18},()=>Array(7).fill(0));players.forEach(p=>p.rounds.forEach(r=>r.forEach((v,h)=>{v=Number(v);if(h<18&&v>=1&&v<=7)stats[h][v-1]++})));
  let x=side,y;const radius=15*s;bmRoundFill(ctx,x,metaH,sticky,headH,{tl:radius},'rgba(0,0,0,.4)');y=metaH+headH+section;
  for(let i=1;i<=18;i++){ctx.fillStyle=i%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,sticky,rowH);bmText(ctx,i,x+sticky/2,y+rowH/2,{size:12*s,bold:true});y+=rowH}bmRoundFill(ctx,x,y+section,sticky,tableB-y-section,{bl:radius},'rgba(0,0,0,.4)');x+=sticky+gap;
  const sums=Array(7).fill(0);
  for(let n=0;n<7;n++){
    ctx.fillStyle='rgba(0,0,0,.588)';ctx.fillRect(x,metaH,scoreW,headH);bmText(ctx,n+1,x+scoreW/2,metaH+headH/2,{size:14*s,bold:true});y=metaH+headH+section;
    for(let h=0;h<18;h++){ctx.fillStyle='#fff';ctx.fillRect(x,y,scoreW,rowH);if((h+1)%2===0){ctx.fillStyle='rgba(0,0,0,.078)';ctx.fillRect(x,y,scoreW,rowH)}const count=stats[h][n];bmText(ctx,count,x+scoreW/2,y+rowH/2,{size:11*s,color:'#000',shadow:false});sums[n]+=count;y+=rowH}
    y+=section;ctx.fillStyle='#fff';ctx.fillRect(x,y,scoreW,tableB-y);ctx.fillStyle='rgba(0,0,0,.157)';ctx.fillRect(x,y,scoreW,tableB-y);bmText(ctx,sums[n],x+scoreW/2,y+rowH/2,{size:11*s,bold:true,color:'#000',shadow:false});x+=scoreW+gap;
  }
  bmRoundFill(ctx,x,metaH,sticky,headH,{tr:radius},'rgba(0,0,0,.4)');y=metaH+headH+section;
  for(let i=1;i<=18;i++){ctx.fillStyle=i%2===0?'rgba(0,0,0,.4)':'rgba(0,0,0,.302)';ctx.fillRect(x,y,sticky,rowH);bmText(ctx,i,x+sticky/2,y+rowH/2,{size:12*s,bold:true});y+=rowH}bmRoundFill(ctx,x,y+section,sticky,tableB-y-section,{br:radius},'rgba(0,0,0,.4)');
  bmText(ctx,'MiniGolf Punktekarte',W-side,H-5*s,{size:8*s,color:'#d3d3d3',align:'right',baseline:'alphabetic'});return canvas;
}

function bmDownload(blob,name){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),1500)}
function bmFileName(game,prefix){const d=bmDateParts(game?.date).date.replace(/\./g,'-'),loc=String(game?.location||'').trim().replace(/[^a-z0-9äöüß_-]+/gi,'_').slice(0,30);return `${prefix}${loc?'_'+loc:''}_${d}.png`}
async function shareGameAsNativeImages(game){
  try{
    toast('Ergebnisbild wird vorbereitet …');
    const pkg=await ensureGameBitmapPackage(game);
    const files=pkg.items.map((item,i)=>new File([item.blob],i===0?'score_table.png':'track_stats.png',{type:'image/png'}));
    const data={title:'Ergebnis teilen',files};
    if(navigator.share&&(!navigator.canShare||navigator.canShare(data))){await navigator.share(data);return}
    pkg.items.forEach((item,i)=>setTimeout(()=>bmDownload(item.blob,item.name),i*200));toast(pkg.items.length===2?'Ergebnis- und Statistikbild gespeichert':'Ergebnisbild gespeichert');
  }catch(err){if(err?.name==='AbortError'){toast('Teilen abgebrochen');return}console.error(err);toast('Ergebnisbild konnte nicht erstellt werden')}
}

// Replace v44's text-only sharing after the stable application script has loaded.
shareGame=shareGameAsNativeImages;
window.generateResultBitmapCanvas=generateResultBitmapCanvas;
window.generateTrackStatsBitmapCanvas=generateTrackStatsBitmapCanvas;
window.ensureGameBitmapPackage=ensureGameBitmapPackage;
window.hydrateHistoryBitmapPreview=hydrateHistoryBitmapPreview;
window.openGameBitmapPreview=openGameBitmapPreview;
window.prewarmGameBitmap=prewarmGameBitmap;
window.bmSpinnerMarkup=bmSpinnerMarkup;
const bmOriginalPersistHistory=window.persistHistory;
if(typeof bmOriginalPersistHistory==='function'){
  window.persistHistory=function(...args){
    const result=bmOriginalPersistHistory.apply(this,args);
    if(window.endedGames?.[0])prewarmGameBitmap(window.endedGames[0]);
    else if(typeof endedGames!=='undefined'&&endedGames?.[0])prewarmGameBitmap(endedGames[0]);
    return result;
  };
}
const bmIdle=('requestIdleCallback' in window)?requestIdleCallback:(fn)=>setTimeout(fn,600);
bmIdle(()=>bmAssets().catch(()=>{}));
