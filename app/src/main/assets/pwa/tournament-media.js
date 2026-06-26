'use strict';

// v48 – native-style tournament image workflow.
// Images are stored as Blobs in IndexedDB; tournament notes keep only small media references.

const TOURNAMENT_MEDIA_DB='mg_pwa_tournament_media_v1';
const TOURNAMENT_MEDIA_STORE='images';
const TOURNAMENT_MEDIA_VERSION=1;
const mediaObjectUrls=new Map();
let mediaDbPromise=null;

function mediaDb(){
  if(mediaDbPromise)return mediaDbPromise;
  mediaDbPromise=new Promise((resolve,reject)=>{
    const req=indexedDB.open(TOURNAMENT_MEDIA_DB,TOURNAMENT_MEDIA_VERSION);
    req.onupgradeneeded=()=>{
      const db=req.result;
      if(!db.objectStoreNames.contains(TOURNAMENT_MEDIA_STORE))db.createObjectStore(TOURNAMENT_MEDIA_STORE,{keyPath:'id'});
    };
    req.onsuccess=()=>resolve(req.result);
    req.onerror=()=>reject(req.error||new Error('Bildspeicher konnte nicht geöffnet werden'));
  });
  return mediaDbPromise;
}
function mediaTx(mode,work){return mediaDb().then(db=>new Promise((resolve,reject)=>{const tx=db.transaction(TOURNAMENT_MEDIA_STORE,mode),store=tx.objectStore(TOURNAMENT_MEDIA_STORE);let result;try{result=work(store)}catch(err){reject(err);return}tx.oncomplete=()=>resolve(result);tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||new Error('Bildspeicher abgebrochen'))}))}
async function mediaPutBlob(blob,id=uid()){await mediaTx('readwrite',s=>s.put({id,blob,type:blob.type||'image/jpeg',createdAt:Date.now()}));mediaRevoke(id);return id}
async function mediaGetRecord(id){if(!id)return null;const db=await mediaDb();return new Promise((resolve,reject)=>{const tx=db.transaction(TOURNAMENT_MEDIA_STORE,'readonly'),req=tx.objectStore(TOURNAMENT_MEDIA_STORE).get(id);req.onsuccess=()=>resolve(req.result||null);req.onerror=()=>reject(req.error)})}
async function mediaGetBlob(id){return (await mediaGetRecord(id))?.blob||null}
async function mediaDelete(id){if(!id)return;mediaRevoke(id);await mediaTx('readwrite',s=>s.delete(id))}
function mediaRevoke(id){const u=mediaObjectUrls.get(id);if(u){URL.revokeObjectURL(u);mediaObjectUrls.delete(id)}}
async function mediaUrlFor(image,original=false){
  if(!image)return'';
  if(typeof image==='string')return image;
  if(image.legacySrc)return image.legacySrc;
  if(image.imagePath&&!image.editedId&&!image.originalId)return image.imagePath;
  const id=original?(image.originalId||image.editedId):(image.editedId||image.originalId);
  if(!id)return'';
  if(mediaObjectUrls.has(id))return mediaObjectUrls.get(id);
  const blob=await mediaGetBlob(id);if(!blob)return'';
  const url=URL.createObjectURL(blob);mediaObjectUrls.set(id,url);return url;
}
function mediaNormalizeImage(img){
  if(typeof img==='string')return{id:uid(),legacySrc:img,createdAt:Date.now()};
  if(!img||typeof img!=='object')return{id:uid(),createdAt:Date.now()};
  return{id:img.id||uid(),originalId:img.originalId||img.originalImageId||null,editedId:img.editedId||img.imageId||null,legacySrc:img.legacySrc||(!img.originalId&&!img.editedId?img.imagePath||'':''),createdAt:img.createdAt||Date.now()};
}
function mediaNormalizeNote(note){
  note.holes=Array.from({length:18},(_,i)=>{const h=note.holes?.[i]||{};return{ball:h.ball||'',start:h.start||h.startPoint||'',notes:h.notes||'',images:Array.isArray(h.images)?h.images.map(mediaNormalizeImage):[]}});return note;
}
function mediaHtmlIcon(name){return icon(name)}
function mediaFileToDataUrl(blob){return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(r.error);r.readAsDataURL(blob)})}
function mediaDataUrlToBlob(data){const [head,body]=String(data).split(',');const mime=(head.match(/data:([^;]+)/)||[])[1]||'image/jpeg',binary=atob(body||''),arr=new Uint8Array(binary.length);for(let i=0;i<binary.length;i++)arr[i]=binary.charCodeAt(i);return new Blob([arr],{type:mime})}
function mediaCanvasBlob(canvas,type='image/jpeg',quality=.9){return new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('Bild konnte nicht erzeugt werden')),type,quality))}
function mediaLoadImage(src){return new Promise((resolve,reject)=>{const img=new Image();img.decoding='async';img.onload=()=>resolve(img);img.onerror=()=>reject(new Error('Bild konnte nicht geladen werden'));img.src=src})}
function mediaThemePage(className){const p=document.createElement('div');p.className=`pageScreen ${className}`;applyTournamentTheme(p);applyTournamentNativeMetrics(p);return p}
function mediaRemovePage(page){page?.remove();document.body.classList.remove('mediaEditing')}

function mediaSourceDialog(onFile,galleryMode=false){
  const layer=document.createElement('div');layer.className='dialogLayer show mediaSourceLayer';
  const iconBlock=galleryMode?`<div class="mediaSourceIcon">${mediaHtmlIcon('add_a_photo')}</div>`:'';
  const textBlock=galleryMode?'':`<p class="mediaSourceText">Möchtest du ein Foto aufnehmen oder ein Bild aus der Galerie wählen?</p>`;
  const cameraContent=galleryMode?'Kamera':`${mediaHtmlIcon('camera_alt')}<span>Kamera</span>`;
  const galleryContent=galleryMode?'Galerie':`${mediaHtmlIcon('photo_library')}<span>Galerie</span>`;
  layer.innerHTML=`<div class="mediaSourceDialog ${galleryMode?'gallerySourceDialog':''}" onclick="event.stopPropagation()">${iconBlock}<h2>Bildquelle wählen</h2>${textBlock}<div class="mediaSourceActions"><button data-source="camera">${cameraContent}</button><button data-source="gallery">${galleryContent}</button></div></div>`;
  const choose=capture=>{const input=document.createElement('input');input.type='file';input.accept='image/*';if(capture)input.setAttribute('capture','environment');input.onchange=()=>{const f=input.files?.[0];layer.remove();if(f)onFile(f)};input.click()};
  layer.querySelector('[data-source="camera"]').onclick=()=>choose(true);
  layer.querySelector('[data-source="gallery"]').onclick=()=>choose(false);
  layer.addEventListener('pointerdown',e=>{if(e.target===layer)layer.remove()});
  document.body.appendChild(layer);
}


async function mediaCropScreen(file,onConfirm){
  const sourceUrl=URL.createObjectURL(file);let img;
  try{img=await mediaLoadImage(sourceUrl)}catch(err){URL.revokeObjectURL(sourceUrl);toast('Bild konnte nicht geöffnet werden');return}
  const page=mediaThemePage('mediaPage mediaCropPage');document.body.classList.add('mediaEditing');
  page.innerHTML=`<div class="mediaHeader"><button data-back>${mediaHtmlIcon('arrow_back')}</button><h1>Bildausschnitt wählen</h1><span class="mediaHeaderSpacer"></span></div><div class="mediaCropBody"><div class="mediaCropFrame"><canvas></canvas></div><div class="mediaCropControls"><button data-cancel aria-label="Abbrechen">${mediaHtmlIcon('close')}</button><button data-rotate aria-label="Drehen"><svg class="mi" viewBox="0 0 24 24" aria-hidden="true"><path d="M15.55 5.55 11 1v3.07a8 8 0 1 0 7.93 8.93h-2.02A6 6 0 1 1 11 6.07V10l4.55-4.45z"/></svg></button><button data-confirm aria-label="Bestätigen">${mediaHtmlIcon('check')}</button></div><div class="mediaCropHelp">Bild mit zwei Fingern zoomen und schieben</div></div>`;
  document.body.appendChild(page);applyTournamentNativeMetrics(page);
  const canvas=page.querySelector('canvas'),frame=page.querySelector('.mediaCropFrame'),ctx=canvas.getContext('2d');
  let zoom=1,offsetX=0,offsetY=0,baseScale=1,cssW=1,cssH=1,raf=0;const pointers=new Map();let lastPinch=null,lastCenter=null;
  const clamp=()=>{const dw=img.naturalWidth*baseScale*zoom,dh=img.naturalHeight*baseScale*zoom,limitX=Math.max(0,(dw-cssW)/2),limitY=Math.max(0,(dh-cssH)/2);offsetX=Math.max(-limitX,Math.min(limitX,offsetX));offsetY=Math.max(-limitY,Math.min(limitY,offsetY))};
  const draw=()=>{raf=0;const dpr=Math.min(2,window.devicePixelRatio||1),r=frame.getBoundingClientRect();cssW=Math.max(1,r.width);cssH=Math.max(1,r.height);canvas.width=Math.round(cssW*dpr);canvas.height=Math.round(cssH*dpr);canvas.style.width=`${cssW}px`;canvas.style.height=`${cssH}px`;ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,cssW,cssH);ctx.fillStyle='#303030';ctx.fillRect(0,0,cssW,cssH);baseScale=Math.min(cssW/img.naturalWidth,cssH/img.naturalHeight);clamp();const dw=img.naturalWidth*baseScale*zoom,dh=img.naturalHeight*baseScale*zoom;ctx.drawImage(img,(cssW-dw)/2+offsetX,(cssH-dh)/2+offsetY,dw,dh)};
  const schedule=()=>{if(!raf)raf=requestAnimationFrame(draw)};new ResizeObserver(schedule).observe(frame);schedule();
  canvas.addEventListener('pointerdown',e=>{e.preventDefault();canvas.setPointerCapture?.(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(pointers.size===2){const a=[...pointers.values()];lastPinch=Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y);lastCenter={x:(a[0].x+a[1].x)/2,y:(a[0].y+a[1].y)/2}}},{passive:false});
  canvas.addEventListener('pointermove',e=>{if(!pointers.has(e.pointerId))return;e.preventDefault();const prev=pointers.get(e.pointerId),next={x:e.clientX,y:e.clientY};pointers.set(e.pointerId,next);if(pointers.size===1){offsetX+=next.x-prev.x;offsetY+=next.y-prev.y}else if(pointers.size>=2){const a=[...pointers.values()].slice(0,2),dist=Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y),center={x:(a[0].x+a[1].x)/2,y:(a[0].y+a[1].y)/2};if(lastPinch){zoom=Math.max(1,Math.min(5,zoom*(dist/lastPinch)));offsetX+=center.x-lastCenter.x;offsetY+=center.y-lastCenter.y}lastPinch=dist;lastCenter=center}schedule()},{passive:false});
  const pointerEnd=e=>{pointers.delete(e.pointerId);if(pointers.size<2){lastPinch=null;lastCenter=null}};canvas.addEventListener('pointerup',pointerEnd);canvas.addEventListener('pointercancel',pointerEnd);
  canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(1,Math.min(5,zoom*(e.deltaY<0?1.12:.89)));schedule()},{passive:false});
  const close=()=>{URL.revokeObjectURL(sourceUrl);mediaRemovePage(page)};page.querySelector('[data-back]').onclick=close;page.querySelector('[data-cancel]').onclick=close;
  page.querySelector('[data-rotate]').onclick=async()=>{try{const rotated=document.createElement('canvas');rotated.width=img.naturalHeight;rotated.height=img.naturalWidth;const rctx=rotated.getContext('2d');rctx.translate(rotated.width,0);rctx.rotate(Math.PI/2);rctx.drawImage(img,0,0);img=await mediaLoadImage(rotated.toDataURL('image/jpeg',.94));zoom=1;offsetX=0;offsetY=0;schedule()}catch(_){toast('Bild konnte nicht gedreht werden')}};
  page.querySelector('[data-confirm]').onclick=async()=>{try{const out=document.createElement('canvas');out.width=1200;out.height=1600;const o=out.getContext('2d',{alpha:false}),sx=1200/cssW,sy=1600/cssH,dw=img.naturalWidth*baseScale*zoom,dh=img.naturalHeight*baseScale*zoom;o.fillStyle='#303030';o.fillRect(0,0,out.width,out.height);o.drawImage(img,((cssW-dw)/2+offsetX)*sx,((cssH-dh)/2+offsetY)*sy,dw*sx,dh*sy);const blob=await mediaCanvasBlob(out,'image/jpeg',.9);close();await onConfirm(blob)}catch(err){console.error(err);toast('Bild konnte nicht gespeichert werden')}};
}

function mediaHueFromX(el,x){const r=el.getBoundingClientRect(),p=Math.max(0,Math.min(1,(x-r.left)/r.width));return `hsl(${Math.round(p*360)}, 100%, 50%)`}
async function mediaDrawingScreen(media,onSaved){
  const src=await mediaUrlFor(media);if(!src){toast('Bild nicht gefunden');return}
  let base;try{base=await mediaLoadImage(src)}catch(_){toast('Bild konnte nicht geladen werden');return}
  const page=mediaThemePage('mediaPage mediaDrawingPage');document.body.classList.add('mediaEditing');
  page.innerHTML=`<div class="mediaHeader"><button data-back>${mediaHtmlIcon('arrow_back')}</button><h1>Bild bearbeiten</h1><button data-save>${mediaHtmlIcon('save')}</button></div><div class="mediaDrawBody"><div class="mediaDrawFrame"><canvas width="900" height="1200"></canvas></div><div class="mediaColorRow"><span class="mediaColorDot"></span><div class="mediaHueBar"><i></i></div></div><div class="mediaDrawTools"><button data-reset aria-label="Zurücksetzen">${mediaHtmlIcon('refresh')}</button><button data-undo aria-label="Rückgängig"><svg class="mi" viewBox="0 0 24 24" aria-hidden="true"><path d="M7.5 7H17a5 5 0 0 1 0 10h-4v-2h4a3 3 0 0 0 0-6H7.5l3.25 3.25L9.34 13.66 3.67 8l5.67-5.66 1.41 1.41L7.5 7z"/></svg></button></div></div>`;
  document.body.appendChild(page);applyTournamentNativeMetrics(page);
  const canvas=page.querySelector('canvas'),ctx=canvas.getContext('2d'),paths=[];let current=null,color='#f44336';
  const drawBase=()=>{ctx.clearRect(0,0,900,1200);ctx.drawImage(base,0,0,900,1200);ctx.lineCap='round';ctx.lineJoin='round';for(const path of paths){ctx.strokeStyle=path.color;ctx.lineWidth=path.width;ctx.beginPath();path.points.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y));ctx.stroke()}if(current){ctx.strokeStyle=current.color;ctx.lineWidth=current.width;ctx.beginPath();current.points.forEach((p,i)=>i?ctx.lineTo(p.x,p.y):ctx.moveTo(p.x,p.y));ctx.stroke()}};drawBase();
  const point=e=>{const r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)*900/r.width,y:(e.clientY-r.top)*1200/r.height}};
  canvas.addEventListener('pointerdown',e=>{if(e.pointerType==='mouse'&&e.button!==0)return;e.preventDefault();canvas.setPointerCapture?.(e.pointerId);current={id:e.pointerId,color,width:10,points:[point(e)]}},{passive:false});
  canvas.addEventListener('pointermove',e=>{if(!current||current.id!==e.pointerId)return;e.preventDefault();current.points.push(point(e));drawBase()},{passive:false});
  const finish=e=>{if(!current||current.id!==e.pointerId)return;if(current.points.length>1)paths.push(current);current=null;drawBase()};canvas.addEventListener('pointerup',finish);canvas.addEventListener('pointercancel',finish);
  const hue=page.querySelector('.mediaHueBar'),dot=page.querySelector('.mediaColorDot'),knob=hue.querySelector('i');dot.style.background=color;
  const setHue=e=>{color=mediaHueFromX(hue,e.clientX);dot.style.background=color;const r=hue.getBoundingClientRect();knob.style.left=`${Math.max(0,Math.min(r.width,e.clientX-r.left))}px`};hue.addEventListener('pointerdown',e=>{e.preventDefault();hue.setPointerCapture?.(e.pointerId);setHue(e)});hue.addEventListener('pointermove',e=>{if(e.buttons||e.pointerType!=='mouse')setHue(e)});
  page.querySelector('[data-back]').onclick=()=>mediaRemovePage(page);
  page.querySelector('[data-undo]').onclick=()=>{paths.pop();drawBase()};
  page.querySelector('[data-reset]').onclick=()=>confirmDialog('Bild zurücksetzen?','Möchtest du alle Zeichnungen entfernen und das Originalbild wiederherstellen?',async()=>{const original=await mediaUrlFor(media,true);if(original){base=await mediaLoadImage(original);paths.length=0;media.editedId=media.originalId;drawBase();toast('Original wiederhergestellt')}});
  page.querySelector('[data-save]').onclick=async()=>{try{const blob=await mediaCanvasBlob(canvas,'image/jpeg',.9),old=media.editedId,id=await mediaPutBlob(blob);media.editedId=id;if(old&&old!==media.originalId)mediaDelete(old).catch(()=>{});await onSaved(media);mediaRemovePage(page);toast('Bild gespeichert')}catch(err){console.error(err);toast('Bild konnte nicht gespeichert werden')}};
}

async function mediaPreview(image){
  const src=await mediaUrlFor(image);if(!src)return;
  const overlay=document.createElement('div');overlay.className='mediaPreviewOverlay';overlay.innerHTML=`<button>${mediaHtmlIcon('close')}</button><img alt="Bildvorschau">`;const img=overlay.querySelector('img');img.src=src;let scale=1,x=0,y=0,pointers=new Map(),lastDist=null,lastCenter=null;
  const apply=()=>img.style.transform=`translate3d(${x}px,${y}px,0) scale(${scale})`;
  overlay.querySelector('button').onclick=()=>overlay.remove();overlay.addEventListener('pointerdown',e=>{if(e.target===overlay)overlay.remove()});
  img.addEventListener('pointerdown',e=>{e.preventDefault();img.setPointerCapture?.(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(pointers.size===2){const a=[...pointers.values()];lastDist=Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y);lastCenter={x:(a[0].x+a[1].x)/2,y:(a[0].y+a[1].y)/2}}},{passive:false});
  img.addEventListener('pointermove',e=>{if(!pointers.has(e.pointerId))return;e.preventDefault();const prev=pointers.get(e.pointerId),next={x:e.clientX,y:e.clientY};pointers.set(e.pointerId,next);if(pointers.size===1&&scale>1){x+=next.x-prev.x;y+=next.y-prev.y}else if(pointers.size>=2){const a=[...pointers.values()].slice(0,2),d=Math.hypot(a[0].x-a[1].x,a[0].y-a[1].y),c={x:(a[0].x+a[1].x)/2,y:(a[0].y+a[1].y)/2};if(lastDist){scale=Math.max(1,Math.min(5,scale*d/lastDist));x+=c.x-lastCenter.x;y+=c.y-lastCenter.y}lastDist=d;lastCenter=c}apply()},{passive:false});
  const end=e=>{pointers.delete(e.pointerId);if(pointers.size<2){lastDist=null;lastCenter=null}if(scale===1){x=0;y=0;apply()}};img.addEventListener('pointerup',end);img.addEventListener('pointercancel',end);img.addEventListener('wheel',e=>{e.preventDefault();scale=Math.max(1,Math.min(5,scale*(e.deltaY<0?1.15:.87)));if(scale===1){x=0;y=0}apply()},{passive:false});
  applyTournamentTheme?.(overlay);applyTournamentNativeMetrics?.(overlay);document.body.appendChild(overlay);
}

async function mediaDeleteImageFiles(image){const ids=[image?.editedId,image?.originalId].filter(Boolean);for(const id of [...new Set(ids)])await mediaDelete(id).catch(()=>{})}
function mediaCollectIds(target,set=new Set()){
  if(!target)return set;
  mediaNormalizeNote(target).holes.forEach(h=>h.images.forEach(im=>{if(im.originalId)set.add(im.originalId);if(im.editedId)set.add(im.editedId)}));
  return set;
}
async function mediaCleanupUnreferenced(candidateIds){
  const referenced=new Set();
  tournamentNotes.forEach(n=>mediaCollectIds(n,referenced));
  for(const id of candidateIds)if(id&&!referenced.has(id))await mediaDelete(id).catch(()=>{});
}
function mediaDeleteConfirm(onConfirm){
  const layer=document.createElement('div');
  layer.className='dialogLayer show mediaDeleteConfirmLayer';
  layer.setAttribute('role','presentation');
  layer.setAttribute('data-top-dialog','media-delete');
  // This dialog must sit above the full-screen gallery (z=520), image preview
  // (z=950) and drag ghost (z=900). Keep the critical portal geometry inline
  // as well, so older cached CSS cannot push it behind the gallery again.
  const topRules={
    position:'fixed',inset:'0',left:'0',top:'0',right:'0',bottom:'0',
    width:'100vw',height:'100dvh',transform:'none',borderRadius:'0',
    overflow:'visible',zIndex:'2147483000',display:'flex'
  };
  Object.entries(topRules).forEach(([name,value])=>{
    const cssName=name.replace(/[A-Z]/g,m=>'-'+m.toLowerCase());
    layer.style.setProperty(cssName,value,'important');
  });
  layer.innerHTML=`<div class="confirmDialog mediaDeleteConfirmDialog" role="dialog" aria-modal="true" aria-labelledby="mediaDeleteTitle" onclick="event.stopPropagation()"><h2 id="mediaDeleteTitle">Bild entfernen?</h2><p>Möchtest du dieses Bild wirklich unwiderruflich aus der Galerie löschen?</p><div class="confirmActions"><button class="pillButton gray" data-cancel>Abbrechen</button><button class="pillButton danger pureRed" data-delete>Löschen</button></div></div>`;
  let closed=false;
  const close=()=>{if(closed)return;closed=true;document.removeEventListener('keydown',onKey,true);layer.remove()};
  const onKey=e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close()}};
  layer.addEventListener('pointerdown',e=>{if(e.target===layer)close()});
  layer.querySelector('[data-cancel]').onclick=close;
  layer.querySelector('[data-delete]').onclick=()=>{close();onConfirm?.()};
  document.addEventListener('keydown',onKey,true);
  document.body.appendChild(layer);
  layer.querySelector('[data-cancel]')?.focus({preventScroll:true});
}
async function mediaGalleryScreen(note,holeIndex,{readonly=false,onChanged=()=>{}}={}){
  mediaNormalizeNote(note);const images=note.holes[holeIndex].images,page=mediaThemePage('mediaPage mediaGalleryPage');document.body.classList.add('mediaEditing');let targetIndex=null;
  async function addBlob(blob){const id=await mediaPutBlob(blob),entry={id:uid(),originalId:id,editedId:id,createdAt:Date.now()};images.push(entry);onChanged();draw();toast('Bild hinzugefügt')}
  function chooseNew(){mediaSourceDialog(file=>mediaCropScreen(file,addBlob),true)}
  function draw(){
    page.innerHTML=`<div class="mediaHeader"><button data-back>${mediaHtmlIcon('arrow_back')}</button><h1>Galerie - Bahn ${holeIndex+1}</h1><span class="mediaHeaderSpacer"></span></div><div class="mediaGalleryGrid">${images.map((im,i)=>`<div class="mediaGalleryItem" data-media-index="${i}"><div class="mediaThumbSkeleton">${mediaHtmlIcon('image')}</div>${readonly?'':`<div class="mediaCardActions"><button data-edit="${i}">${mediaHtmlIcon('edit')}</button><button data-delete="${i}">${mediaHtmlIcon('delete')}</button></div>`}</div>`).join('')}${readonly?'':`<button class="mediaGalleryAdd">${mediaHtmlIcon('add_a_photo')}<span>Neu</span></button>`}</div>`;
    page.querySelector('[data-back]').onclick=()=>mediaRemovePage(page);page.querySelector('.mediaGalleryAdd')?.addEventListener('click',chooseNew);
    page.querySelectorAll('[data-media-index]').forEach(card=>{const i=Number(card.dataset.mediaIndex),im=images[i];mediaUrlFor(im).then(src=>{if(!src||!card.isConnected)return;card.insertAdjacentHTML('afterbegin',`<img src="${esc(src)}" alt="Bild ${i+1}">`)});card.addEventListener('click',e=>{if(e.target.closest('button'))return;mediaPreview(im)});if(!readonly)mediaBindGalleryDrag(card,i,images,()=>{onChanged();draw()})});
    page.querySelectorAll('[data-edit]').forEach(btn=>btn.onclick=e=>{e.stopPropagation();const i=Number(btn.dataset.edit);mediaDrawingScreen(images[i],()=>{onChanged();draw()})});
    page.querySelectorAll('[data-delete]').forEach(btn=>btn.onclick=e=>{
      e.preventDefault();e.stopPropagation();
      const i=Number(btn.dataset.delete);
      mediaDeleteConfirm(()=>{
        if(i<0||i>=images.length)return;
        images.splice(i,1);
        onChanged();
        draw();
        toast('Bild entfernt');
      });
    });
    applyTournamentNativeMetrics(page);
  }
  document.body.appendChild(page);draw();
}
function mediaBindGalleryDrag(card,index,images,onDone){
  let timer=null,dragging=false,pid=null,ghost=null,from=index,target=index,offX=0,offY=0;
  const clearOrders=()=>card.parentElement?.querySelectorAll('.mediaGalleryItem').forEach(x=>{x.style.order='';x.classList.remove('mediaDropTarget','mediaDragSource')});
  const visualOrder=()=>{const order=images.map((_,i)=>i),item=order.splice(from,1)[0];order.splice(target,0,item);const pos=new Map(order.map((v,i)=>[v,i]));card.parentElement?.querySelectorAll('.mediaGalleryItem').forEach(x=>x.style.order=pos.get(Number(x.dataset.mediaIndex)))};
  const start=e=>{dragging=true;card.classList.add('mediaDragSource');const r=card.getBoundingClientRect();offX=e.clientX-r.left;offY=e.clientY-r.top;ghost=card.cloneNode(true);ghost.className='mediaGalleryGhost';ghost.style.width=`${r.width}px`;ghost.style.height=`${r.height}px`;ghost.style.left=`${r.left}px`;ghost.style.top=`${r.top}px`;document.body.appendChild(ghost);window.mgHaptic?.(20)};
  card.addEventListener('pointerdown',e=>{if(e.pointerType==='mouse'&&e.button!==0||e.target.closest('button'))return;pid=e.pointerId;try{card.setPointerCapture(pid)}catch(_){}timer=setTimeout(()=>start(e),520)});
  card.addEventListener('pointermove',e=>{if(e.pointerId!==pid)return;if(!dragging){if(e.buttons===0)clearTimeout(timer);return}e.preventDefault();ghost.style.left=`${e.clientX-offX}px`;ghost.style.top=`${e.clientY-offY}px`;ghost.style.transform='scale(1.08)';const under=document.elementFromPoint(e.clientX,e.clientY)?.closest('.mediaGalleryItem');if(under){const next=Number(under.dataset.mediaIndex);if(Number.isFinite(next)&&next!==target){target=next;visualOrder();card.parentElement.querySelectorAll('.mediaGalleryItem').forEach(x=>x.classList.toggle('mediaDropTarget',Number(x.dataset.mediaIndex)===target));window.mgHaptic?.(8)}}},{passive:false});
  const end=e=>{if(e.pointerId!==pid)return;clearTimeout(timer);if(dragging){const [item]=images.splice(from,1);images.splice(target,0,item);ghost?.remove();clearOrders();onDone()}dragging=false;pid=null;ghost=null};card.addEventListener('pointerup',end);card.addEventListener('pointercancel',end);card.addEventListener('contextmenu',e=>e.preventDefault())
}

async function mediaHydrateHoleButtons(root,note,readonly=false){
  const buttons=[...root.querySelectorAll('[data-camera]')];for(const btn of buttons){const i=Number(btn.dataset.camera),images=note.holes[i]?.images||[];if(!images.length)continue;const src=await mediaUrlFor(images[0]);if(!src||!btn.isConnected)continue;btn.classList.add('hasMedia');btn.innerHTML=`<img src="${esc(src)}" alt="Bahn ${i+1}"><span class="mediaBrushBadge">${mediaHtmlIcon('edit')}</span>${images.length>1?`<span class="mediaCountBadge">${images.length}</span>`:''}`;if(readonly)btn.classList.add('viewCamera')}
}

// v60 – native save/discard guard for edited tournament notes.
function mediaTournamentExitDialog({onSave,onDiscard,onCancel}){
  const layer=document.createElement('div');
  layer.className=`dialogLayer show tournamentExitLayer ${getTournamentThemeMode()==='Dunkel'?'tourExitDark':'tourExitLight'}`;
  layer.innerHTML=`<div class="confirmDialog tournamentExitDialog" role="dialog" aria-modal="true" aria-labelledby="tournamentExitTitle" onclick="event.stopPropagation()"><h2 id="tournamentExitTitle">Änderungen speichern?</h2><p>Du hast ungespeicherte Änderungen. Möchtest du diese vor dem Verlassen speichern?</p><div class="confirmActions"><button class="pillButton red" data-discard>Verwerfen</button><button class="pillButton" data-save>Speichern</button></div></div>`;
  let closed=false;
  const close=reason=>{
    if(closed)return;
    closed=true;
    document.removeEventListener('keydown',onKey,true);
    layer.remove();
    if(reason==='save')onSave?.();
    else if(reason==='discard')onDiscard?.();
    else onCancel?.();
  };
  const onKey=e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close('cancel')}};
  layer.addEventListener('pointerdown',e=>{if(e.target===layer)close('cancel')});
  layer.querySelector('[data-save]').onclick=()=>close('save');
  layer.querySelector('[data-discard]').onclick=()=>close('discard');
  document.addEventListener('keydown',onKey,true);
  document.body.appendChild(layer);
  return {close:()=>close('cancel'),element:layer};
}

// Override the tournament editor from the stable v44/v47 base, keeping its native scaling.
showTournamentEditor=function(existing,parentPage){
  const editing=!!existing;
  let note=mediaNormalizeNote(existing?JSON.parse(JSON.stringify(existing)):emptyTournamentNote());
  const page=mediaThemePage('tournamentEditor nativeTournamentEditor');
  let hasChanges=false;
  let exitDialog=null;
  let closed=false;
  let historyArmed=false;
  const historyToken=`mg-tournament-editor-${uid()}`;
  const initialMediaIds=new Set();
  const observedDraftMediaIds=new Set();
  const collectMediaIds=(target,set)=>{
    mediaNormalizeNote(target).holes.forEach(h=>h.images.forEach(im=>{
      if(im.originalId)set.add(im.originalId);
      if(im.editedId)set.add(im.editedId);
    }));
  };
  collectMediaIds(note,initialMediaIds);
  collectMediaIds(note,observedDraftMediaIds);
  const markChanged=()=>{
    hasChanges=true;
    collectMediaIds(note,observedDraftMediaIds);
  };
  const cleanupDiscardedMedia=async()=>{
    for(const id of observedDraftMediaIds){
      if(!initialMediaIds.has(id)){
        try{await mediaDelete(id)}catch(err){console.warn('Entwurfsbild konnte nicht entfernt werden',err)}
      }
    }
  };
  const armHistory=()=>{
    if(closed||historyArmed)return;
    try{
      history.pushState({mgTournamentEditor:historyToken},'',location.href);
      historyArmed=true;
    }catch(_){historyArmed=false}
  };
  const closeEditor=({discard=false,afterClose=null}={})=>{
    if(closed)return;
    closed=true;
    exitDialog?.close?.();
    exitDialog=null;
    window.removeEventListener('popstate',onPopState);
    if(historyArmed){
      historyArmed=false;
      // V68 global PWA back guard must not interpret this internal history cleanup
      // as another user back action on the underlying tournament page.
      window.__mgIgnoreNextPopState=true;
      try{history.back()}catch(_){window.__mgIgnoreNextPopState=false}
    }
    if(discard)void cleanupDiscardedMedia();
    mediaRemovePage(page);
    afterClose?.();
  };
  const saveNote=()=>{
    note.date=new Date().toISOString();
    if(editing){
      const idx=tournamentNotes.findIndex(n=>n.id===note.id);
      if(idx>=0)tournamentNotes[idx]=note;
      else tournamentNotes.unshift(note);
    }else tournamentNotes.unshift(note);
    persistTournament();
    // Delete image blobs only after the note was really saved. This keeps
    // "Verwerfen" reversible while preventing orphaned files after deletion.
    void mediaCleanupUnreferenced(new Set([...initialMediaIds,...observedDraftMediaIds]));
    hasChanges=false;
    toast('Notizen gespeichert');
    closeEditor({afterClose:()=>{if(parentPage){parentPage.remove();showTournamentHome()}}});
  };
  const requestClose=()=>{
    if(closed||exitDialog)return;
    if(!hasChanges){closeEditor();return}
    exitDialog=mediaTournamentExitDialog({
      onSave:()=>{exitDialog=null;saveNote()},
      onDiscard:()=>{exitDialog=null;closeEditor({discard:true})},
      onCancel:()=>{exitDialog=null;armHistory()}
    });
  };
  const onPopState=()=>{
    if(closed)return;
    // A dialog/media screen above the editor owns this history step. The
    // global PWA back handler marks it so the note itself is not closed just
    // because the camera/gallery chooser or crop screen disappeared.
    if(window.__mgIgnoreTournamentEditorPopState){
      window.__mgIgnoreTournamentEditorPopState=false;
      return;
    }
    historyArmed=false;
    if(exitDialog){
      exitDialog.close();
      exitDialog=null;
      armHistory();
      return;
    }
    requestClose();
    if(!closed)armHistory();
  };
  const holesHtml=()=>note.holes.map((h,i)=>`<div class="tourHole"><b>${i+1}</b><div class="tourHoleContent"><div class="tourLine"><div class="tourInputs"><input data-hole="${i}" data-field="ball" value="${esc(h.ball)}" placeholder="Ball"><input data-hole="${i}" data-field="start" value="${esc(h.start)}" placeholder="Abschlag"></div><button class="cameraBtn" data-camera="${i}">${mediaHtmlIcon(h.images.length?'image':'add_a_photo')}</button></div><input class="tourNotesInput" data-hole="${i}" data-field="notes" value="${esc(h.notes)}" placeholder="Notizen..."></div></div>`).join('');
  const redraw=()=>{
    if(closed)return;
    const locationClass=note.location?'hasValue':'';
    page.innerHTML=`<div class="editorTop"><button class="pageIcon" id="editorBack">${mediaHtmlIcon('arrow_back')}</button><div class="pageTitle">${editing?'Notiz bearbeiten':'Notiz erstellen'}</div><button class="saveIcon" id="saveNote">${mediaHtmlIcon('save')}</button></div><div class="editorBody"><div class="editorMeta"><label class="fieldLabel locationField ${locationClass}"><span class="outlineLabel">Ort</span><input id="noteLocation" value="${esc(note.location)}" autocomplete="off"></label><label class="fieldLabel selectLabel hasValue"><span class="outlineLabel">Anlagentyp</span><select id="noteSystem">${SYSTEMS.map(s=>`<option ${s===note.system?'selected':''}>${esc(s.replace('\n',' '))}</option>`).join('')}</select></label></div><div class="holesScroller"><div class="holesList">${holesHtml()}</div></div></div>`;
    page.querySelector('#editorBack').onclick=requestClose;
    page.querySelector('#saveNote').onclick=saveNote;
    const loc=page.querySelector('#noteLocation');
    const locField=page.querySelector('.locationField');
    const sync=()=>locField.classList.toggle('hasValue',!!loc.value.trim());
    loc.oninput=e=>{note.location=e.target.value;markChanged();sync()};
    loc.onblur=sync;
    page.querySelector('#noteSystem').onchange=e=>{
      note.system=SYSTEMS.find(s=>s.replace('\n',' ')===e.target.value)||e.target.value;
      markChanged();
    };
    page.querySelectorAll('[data-hole]').forEach(inp=>inp.oninput=()=>{
      note.holes[Number(inp.dataset.hole)][inp.dataset.field]=inp.value;
      markChanged();
    });
    page.querySelectorAll('[data-camera]').forEach(btn=>btn.onclick=()=>{
      const i=Number(btn.dataset.camera),imgs=note.holes[i].images;
      if(imgs.length){
        mediaGalleryScreen(note,i,{onChanged:()=>{markChanged();redraw()}});
      }else{
        mediaSourceDialog(file=>mediaCropScreen(file,async blob=>{
          const id=await mediaPutBlob(blob);
          imgs.push({id:uid(),originalId:id,editedId:id,createdAt:Date.now()});
          markChanged();
          redraw();
          toast('Bild hinzugefügt');
        }));
      }
    });
    mediaHydrateHoleButtons(page,note);
    applyTournamentNativeMetrics(page);
  };
  document.body.appendChild(page);
  window.addEventListener('popstate',onPopState);
  armHistory();
  redraw();
};

showTournamentView=function(existing,parentPage){
  const note=mediaNormalizeNote(JSON.parse(JSON.stringify(existing||emptyTournamentNote()))),page=mediaThemePage('tournamentEditor tournamentView nativeTournamentEditor');
  const sys=String(note.system||SYSTEMS[0]).replace('\n',' '),loc=note.location||'Ortangabe';
  const holesHtml=()=>note.holes.map((h,i)=>`<div class="tourHole"><b>${i+1}</b><div class="tourHoleContent"><div class="tourLine"><div class="tourInputs"><input readonly value="${esc(h.ball)}" placeholder="Ball"><input readonly value="${esc(h.start)}" placeholder="Abschlag"></div><button class="cameraBtn viewCamera" data-camera="${i}" ${h.images.length?'':'disabled'}>${mediaHtmlIcon(h.images.length?'image':'image_not_supported')}</button></div><input class="tourNotesInput" readonly value="${esc(h.notes)}" placeholder="Notizen..."></div></div>`).join('');
  page.innerHTML=`<div class="editorTop viewEditorTop"><button class="pageIcon" id="viewBack">${mediaHtmlIcon('arrow_back')}</button><div class="viewTitleBlock"><div class="viewTitleLine">${mediaHtmlIcon('place')}<b>${esc(loc)}</b></div><div class="viewSub">${esc(sys)}</div><div class="viewDate">${mediaHtmlIcon('calendar_month')}<span>${esc(formatGameDate(note.date))}</span></div></div><span class="viewHeaderSpacer" aria-hidden="true"></span></div><div class="editorBody viewEditorBody"><div class="holesScroller viewHolesScroller"><div class="holesList">${holesHtml()}</div></div></div>`;
  page.querySelector('#viewBack').onclick=()=>page.remove();
  page.querySelectorAll('[data-camera]').forEach(btn=>btn.onclick=()=>{const i=Number(btn.dataset.camera);if(note.holes[i].images.length)mediaGalleryScreen(note,i,{readonly:true})});
  document.body.appendChild(page);
  mediaHydrateHoleButtons(page,note,true);applyTournamentNativeMetrics(page);
};

// Backup format v2 embeds IndexedDB image blobs while keeping notes human-readable JSON.
exportTournamentNotes=async function(){
  try{toast('Backup wird erstellt …');const ids=new Set();tournamentNotes.forEach(n=>mediaNormalizeNote(n).holes.forEach(h=>h.images.forEach(im=>{if(im.originalId)ids.add(im.originalId);if(im.editedId)ids.add(im.editedId)})));const media={};for(const id of ids){const blob=await mediaGetBlob(id);if(blob)media[id]=await mediaFileToDataUrl(blob)}const payload={version:2,createdAt:new Date().toISOString(),notes:tournamentNotes,media};const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(payload)],{type:'application/json'}));a.download='MiniGolf_Turniernotizen.mgpk';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1500);toast('Backup inklusive Bilder erstellt')}catch(err){console.error(err);toast('Backup fehlgeschlagen')}};
importTournamentNotes=function(){const inp=document.createElement('input');inp.type='file';inp.accept='.mgpk,application/json,*/*';inp.onchange=()=>{const f=inp.files?.[0];if(!f)return;const r=new FileReader();r.onload=async()=>{try{const data=JSON.parse(r.result),notes=Array.isArray(data.notes)?data.notes:(Array.isArray(data)?data:[]);if(data.media&&typeof data.media==='object')for(const [id,value] of Object.entries(data.media)){if(value)await mediaPutBlob(mediaDataUrlToBlob(value),id)}tournamentNotes=[...notes.map(mediaNormalizeNote),...tournamentNotes];persistTournament();toast(`${notes.length} Notizen inklusive Bilder importiert!`)}catch(err){console.error(err);toast('Import fehlgeschlagen')}};r.readAsText(f)};inp.click()};

window.mediaGalleryScreen=mediaGalleryScreen;
window.mediaDrawingScreen=mediaDrawingScreen;
window.mediaCropScreen=mediaCropScreen;
