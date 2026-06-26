'use strict';

// v61 – Gemeinsames .mgpk-Austauschformat für native Android-App und PWA.
// Die native App verwendet einen GZIP-komprimierten JSON-Wrapper. Ihr Import
// akzeptiert zusätzlich unkomprimiertes JSON; deshalb bleibt der Export auch
// auf Browsern ohne CompressionStream kompatibel.

const MGPK_APP_IDENTIFIER='MiniGolf_Punktekarte';

function mgpkNormalizedSystem(value){
  const text=String(value||'').replace(/\s+/g,' ').trim();
  const match=SYSTEMS.find(s=>s.replace(/\s+/g,' ').trim()===text);
  return match||value||SYSTEMS[0];
}
function mgpkNativeSystem(value){return String(value||SYSTEMS[0]).replace(/\s+/g,' ').trim()}
function mgpkDateMillis(value){
  if(typeof value==='number'&&Number.isFinite(value))return value;
  const parsed=new Date(value).getTime();
  return Number.isFinite(parsed)?parsed:Date.now();
}
function mgpkIsoDate(value){
  const millis=mgpkDateMillis(value);
  try{return new Date(millis).toISOString()}catch(_){return new Date().toISOString()}
}
function mgpkSafePart(value,fallback='Export'){
  const normalized=String(value||fallback).normalize?.('NFD')||String(value||fallback);
  const cleaned=normalized.replace(/[\u0300-\u036f]/g,'').replace(/[^a-zA-Z0-9]/g,'_').replace(/_+/g,'_').replace(/^_|_$/g,'');
  return cleaned||fallback;
}
function mgpkExtensionForMime(type){
  if(type==='image/png')return'png';
  if(type==='image/webp')return'webp';
  if(type==='image/gif')return'gif';
  return'jpg';
}
function mgpkMimeForBytes(bytes){
  if(bytes.length>=4&&bytes[0]===0x89&&bytes[1]===0x50&&bytes[2]===0x4e&&bytes[3]===0x47)return'image/png';
  if(bytes.length>=3&&bytes[0]===0xff&&bytes[1]===0xd8&&bytes[2]===0xff)return'image/jpeg';
  if(bytes.length>=12&&String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP')return'image/webp';
  if(bytes.length>=4&&String.fromCharCode(...bytes.slice(0,4))==='GIF8')return'image/gif';
  return'image/jpeg';
}
function mgpkBytesToBase64(bytes){
  let binary='';
  const chunk=0x8000;
  for(let i=0;i<bytes.length;i+=chunk)binary+=String.fromCharCode(...bytes.subarray(i,Math.min(i+chunk,bytes.length)));
  return btoa(binary);
}
function mgpkBase64ToBytes(value){
  let text=String(value||'').trim();
  const comma=text.indexOf(',');
  if(/^data:/i.test(text)&&comma>=0)text=text.slice(comma+1);
  text=text.replace(/\s+/g,'');
  const binary=atob(text),bytes=new Uint8Array(binary.length);
  for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);
  return bytes;
}
async function mgpkBlobToBase64(blob){return mgpkBytesToBase64(new Uint8Array(await blob.arrayBuffer()))}
async function mgpkBlobForImage(image){
  if(!image)return null;
  for(const id of [image.editedId,image.originalId]){
    if(!id)continue;
    const stored=await mediaGetBlob(id);
    if(stored)return stored;
  }
  const source=image.legacySrc||image.imagePath;
  if(typeof source==='string'&&source.startsWith('data:')){
    try{return mediaDataUrlToBlob(source)}catch(_){return null}
  }
  if(typeof source==='string'&&/^(blob:|https?:)/i.test(source)){
    try{const response=await fetch(source);if(response.ok)return await response.blob()}catch(_){ }
  }
  return null;
}
async function mgpkHoleToNative(hole,holeIndex,noteId){
  const images=[];
  for(let imageIndex=0;imageIndex<(hole.images||[]).length;imageIndex++){
    const blob=await mgpkBlobForImage(hole.images[imageIndex]);
    if(!blob)continue;
    const ext=mgpkExtensionForMime(blob.type);
    const marker=`pwa_${mgpkSafePart(noteId,'note')}_${holeIndex+1}_${imageIndex+1}.${ext}`;
    images.push({imagePath:marker,originalImagePath:marker,imageData:await mgpkBlobToBase64(blob)});
  }
  return{
    ball:String(hole.ball||''),
    startPoint:String(hole.start||hole.startPoint||''),
    notes:String(hole.notes||''),
    images,
    imagePath:null,
    originalImagePath:null
  };
}
async function mgpkNoteToNative(note){
  const normalized=mediaNormalizeNote(JSON.parse(JSON.stringify(note||emptyTournamentNote())));
  const holes=[];
  for(let i=0;i<18;i++)holes.push(await mgpkHoleToNative(normalized.holes[i],i,normalized.id||'note'));
  return{
    id:0,
    date:mgpkDateMillis(normalized.date),
    location:String(normalized.location||''),
    system:mgpkNativeSystem(normalized.system),
    notesJson:JSON.stringify(holes)
  };
}
async function mgpkBuildWrapper(notes){
  const nativeNotes=[];
  for(const note of notes)nativeNotes.push(await mgpkNoteToNative(note));
  return{version:1,appIdentifier:MGPK_APP_IDENTIFIER,exportDate:Date.now(),notes:nativeNotes};
}
async function mgpkPayloadBlob(payload){
  const json=JSON.stringify(payload),plain=new Blob([json],{type:'application/octet-stream'});
  if(typeof CompressionStream!=='function')return plain;
  try{
    const compressed=plain.stream().pipeThrough(new CompressionStream('gzip'));
    return new Blob([await new Response(compressed).arrayBuffer()],{type:'application/octet-stream'});
  }catch(err){console.warn('GZIP-Export nicht verfügbar, nutze kompatibles JSON',err);return plain}
}
function mgpkDownloadFile(file){
  const url=URL.createObjectURL(file),a=document.createElement('a');
  a.href=url;a.download=file.name;document.body.appendChild(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),2000);
}
async function mgpkCreateFile(notes,fileName){
  const wrapper=await mgpkBuildWrapper(notes),blob=await mgpkPayloadBlob(wrapper);
  return new File([blob],fileName,{type:'application/octet-stream',lastModified:Date.now()});
}
async function mgpkReadJson(file){
  const buffer=await file.arrayBuffer(),bytes=new Uint8Array(buffer);
  const gzipped=bytes.length>=2&&bytes[0]===0x1f&&bytes[1]===0x8b;
  if(!gzipped)return JSON.parse(new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/,''));
  if(typeof DecompressionStream!=='function')throw new Error('Dieser Browser kann komprimierte MGPK-Dateien nicht öffnen.');
  const stream=new Blob([buffer]).stream().pipeThrough(new DecompressionStream('gzip'));
  const text=await new Response(stream).text();
  return JSON.parse(text.replace(/^\uFEFF/,''));
}
function mgpkExtractObjects(payload){
  if(Array.isArray(payload))return payload;
  if(payload&&Array.isArray(payload.notes))return payload.notes;
  if(payload&&typeof payload==='object')return[payload];
  return[];
}
function mgpkField(obj,name,obfuscated,fallback=''){
  if(obj&&obj[name]!=null)return obj[name];
  if(obj&&obj[obfuscated]!=null)return obj[obfuscated];
  return fallback;
}
function mgpkParseHoleNotes(value){
  if(Array.isArray(value))return value;
  if(value&&typeof value==='object')return Array.isArray(value.notes)?value.notes:[];
  if(typeof value!=='string'||!value.trim())return[];
  const parsed=JSON.parse(value);
  return Array.isArray(parsed)?parsed:[];
}
function mgpkImagesFromNativeHole(hole){
  const images=Array.isArray(hole?.images)?hole.images.slice():[];
  if(hole?.imagePath&&hole?.originalImagePath){
    const duplicate=images.some(image=>image?.imagePath===hole.imagePath&&image?.originalImagePath===hole.originalImagePath);
    if(!duplicate)images.unshift({imagePath:hole.imagePath,originalImagePath:hole.originalImagePath,imageData:hole.imageData||null});
  }
  return images;
}
async function mgpkNativeImageToPwa(image){
  const encoded=image?.imageData;
  if(!encoded)return null;
  try{
    const bytes=mgpkBase64ToBytes(encoded),blob=new Blob([bytes],{type:mgpkMimeForBytes(bytes)}),id=await mediaPutBlob(blob);
    return{id:uid(),originalId:id,editedId:id,createdAt:Date.now()};
  }catch(err){console.warn('MGPK-Bild konnte nicht importiert werden',err);return null}
}
async function mgpkNativeNoteToPwa(obj){
  const rawNotes=mgpkField(obj,'notesJson','e','');
  const nativeHoles=mgpkParseHoleNotes(rawNotes),holes=[];
  for(let i=0;i<18;i++){
    const source=nativeHoles[i]||{},images=[];
    for(const image of mgpkImagesFromNativeHole(source)){
      const converted=await mgpkNativeImageToPwa(image);
      if(converted)images.push(converted);
    }
    holes.push({ball:String(source.ball||''),start:String(source.startPoint||source.start||''),notes:String(source.notes||''),images});
  }
  const rawDate=mgpkField(obj,'date','b',Date.now());
  return mediaNormalizeNote({
    id:uid(),date:mgpkIsoDate(Number(rawDate)||rawDate),
    location:String(mgpkField(obj,'location','c','')||''),
    system:mgpkNormalizedSystem(mgpkField(obj,'system','d',SYSTEMS[0])),holes
  });
}
function mgpkLooksNative(obj){return !!(obj&&typeof obj==='object'&&(obj.notesJson!=null||obj.e!=null))}
async function mgpkImportLegacyMedia(payload){
  if(!payload?.media||typeof payload.media!=='object')return;
  for(const [id,value] of Object.entries(payload.media)){
    if(!value)continue;
    try{await mediaPutBlob(mediaDataUrlToBlob(value),id)}catch(err){console.warn('Altes PWA-Bild konnte nicht importiert werden',err)}
  }
}
async function mgpkPayloadToPwaNotes(payload){
  await mgpkImportLegacyMedia(payload);
  const imported=[];
  for(const obj of mgpkExtractObjects(payload)){
    try{
      if(mgpkLooksNative(obj))imported.push(await mgpkNativeNoteToPwa(obj));
      else if(obj&&Array.isArray(obj.holes)){
        const copy=JSON.parse(JSON.stringify(obj));
        copy.id=uid();
        copy.date=mgpkIsoDate(copy.date);
        copy.system=mgpkNormalizedSystem(copy.system);
        imported.push(mediaNormalizeNote(copy));
      }
    }catch(err){console.warn('Turniernotiz übersprungen',err)}
  }
  return imported;
}

const MGPK_SHARE_CACHE=new WeakMap();
const MGPK_SHARE_MIME_TYPES=['application/gzip','text/plain','application/octet-stream'];

function mgpkCloneFileWithType(file,type){
  return new File([file],file.name,{type,lastModified:file.lastModified||Date.now()});
}
function mgpkShareData(file){
  return{title:'Turniernotiz teilen',text:'MiniGolf Turniernotiz (.mgpk)',files:[file]};
}
function mgpkSelectShareVariant(file){
  if(!navigator.share)return null;
  for(const type of MGPK_SHARE_MIME_TYPES){
    const candidate=mgpkCloneFileWithType(file,type),data=mgpkShareData(candidate);
    if(!navigator.canShare)return{file:candidate,data,type};
    try{if(navigator.canShare(data))return{file:candidate,data,type}}catch(_){ }
  }
  return null;
}
function mgpkPrepareTournamentShare(note){
  let entry=MGPK_SHARE_CACHE.get(note);
  if(entry)return entry;
  entry={status:'pending',package:null,error:null,promise:null};
  entry.promise=(async()=>{
    const fileName=`${mgpkSafePart(note.location,'Export')}_${mgpkSafePart(mgpkNativeSystem(note.system),'Turnier')}.mgpk`;
    const downloadFile=await mgpkCreateFile([note],fileName);
    entry.package={downloadFile,share:mgpkSelectShareVariant(downloadFile)};
    entry.status='ready';
    return entry.package;
  })().catch(err=>{
    entry.status='error';entry.error=err;console.error('MGPK-Vorbereitung fehlgeschlagen',err);throw err;
  });
  MGPK_SHARE_CACHE.set(note,entry);
  return entry;
}
function mgpkBindTournamentShareButton(note,button){
  if(!button)return;
  const entry=mgpkPrepareTournamentShare(note);
  button.classList.toggle('mgpkPreparing',entry.status==='pending');
  button.setAttribute('aria-busy',entry.status==='pending'?'true':'false');
  entry.promise.then(()=>{
    if(!button.isConnected)return;
    button.classList.remove('mgpkPreparing');
    button.setAttribute('aria-busy','false');
  }).catch(()=>{
    if(!button.isConnected)return;
    button.classList.remove('mgpkPreparing');
    button.setAttribute('aria-busy','false');
  });
}
function mgpkShareFallback(downloadFile,message='Teilen nicht verfügbar – MGPK gespeichert'){
  mgpkDownloadFile(downloadFile);toast(message);
}

shareTournamentNote=function(note){
  const entry=mgpkPrepareTournamentShare(note);
  if(entry.status==='pending'){
    toast('Turniernotiz wird vorbereitet – bitte gleich erneut teilen');
    return;
  }
  if(entry.status==='error'||!entry.package){
    console.error(entry.error);toast('Turniernotiz konnte nicht erstellt werden');return;
  }
  const {downloadFile,share}=entry.package;
  if(!share){
    mgpkShareFallback(downloadFile);
    return;
  }
  try{
    // Wichtig: navigator.share() wird ohne vorheriges await direkt im Klick-Handler
    // aufgerufen. So bleibt die von Android/iOS verlangte Benutzeraktivierung erhalten.
    const result=navigator.share(share.data);
    Promise.resolve(result).catch(err=>{
      if(err?.name==='AbortError')return;
      console.warn('Dateifreigabe fehlgeschlagen, speichere MGPK stattdessen',err);
      mgpkShareFallback(downloadFile);
    });
  }catch(err){
    if(err?.name==='AbortError')return;
    console.warn('Dateifreigabe nicht verfügbar, speichere MGPK stattdessen',err);
    mgpkShareFallback(downloadFile);
  }
};

window.mgpkPrepareTournamentShare=mgpkPrepareTournamentShare;
window.mgpkBindTournamentShareButton=mgpkBindTournamentShareButton;

async function exportTournamentNote(note){
  try{
    toast('Turniernotiz wird exportiert …');
    const entry=mgpkPrepareTournamentShare(note);
    const pkg=entry.status==='ready'?entry.package:await entry.promise;
    if(!pkg?.downloadFile)throw new Error('MGPK-Datei konnte nicht erstellt werden');
    mgpkDownloadFile(pkg.downloadFile);
    toast('Turniernotiz lokal gespeichert');
    return pkg.downloadFile;
  }catch(err){
    console.error(err);toast('Export fehlgeschlagen');return null;
  }
}
window.exportTournamentNote=exportTournamentNote;


exportTournamentNotes=async function(){
  try{
    if(!tournamentNotes.length){toast('Keine Turniernotizen vorhanden');return}
    toast('Kompatibles Backup wird erstellt …');
    const file=await mgpkCreateFile(tournamentNotes,'MiniGolf_Turniernotizen.mgpk');
    mgpkDownloadFile(file);
    toast(`Backup mit ${tournamentNotes.length} Notizen erstellt`);
  }catch(err){console.error(err);toast('Backup fehlgeschlagen')}
};

importTournamentNotes=function(onImported){
  const callback=typeof onImported==='function'?onImported:(typeof onImported?.onImported==='function'?onImported.onImported:null);
  const input=document.createElement('input');
  input.type='file';input.accept='.mgpk,.bgsc,application/octet-stream,application/gzip,application/json,*/*';
  input.onchange=async()=>{
    const file=input.files?.[0];if(!file)return;
    try{
      toast('Turniernotizen werden importiert …');
      const payload=await mgpkReadJson(file),notes=await mgpkPayloadToPwaNotes(payload);
      if(!notes.length)throw new Error('Keine kompatiblen Notizen gefunden');
      const existingIds=new Set(tournamentNotes.map(note=>String(note?.id||'')));
      const prepared=notes.map(note=>{
        const normalized=typeof mediaNormalizeNote==='function'?mediaNormalizeNote(note):note;
        if(!normalized.id||existingIds.has(String(normalized.id)))normalized.id=uid();
        existingIds.add(String(normalized.id));
        return normalized;
      });
      tournamentNotes=[...prepared,...tournamentNotes];
      persistTournament();
      callback?.(prepared);
      toast(`${prepared.length} Turniernotiz${prepared.length===1?'':'en'} importiert`);
    }catch(err){console.error(err);toast(err?.message?.includes('komprimierte MGPK')?err.message:'Import fehlgeschlagen')}
  };
  input.click();
};

window.mgpkCompat={mgpkBuildWrapper,mgpkPayloadBlob,mgpkReadJson,mgpkPayloadToPwaNotes,mgpkPrepareTournamentShare};
