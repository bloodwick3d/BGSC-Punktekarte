'use strict';

const KEY='mg_pwa_v10_state';
const ACTIVE_KEY='mg_pwa_v10_active_games';
const HISTORY_KEY='mg_pwa_v10_ended_games';
const TOURNAMENT_KEY='mg_pwa_v10_tournament_notes';
const MIGRATE_KEYS=['mg_pwa_v9_state','mg_pwa_v8_state','mg_pwa_v7_state','mg_pwa_v6_state'];
const MIGRATE_HISTORY_KEYS=['mg_pwa_v9_ended_games','mg_pwa_v8_ended_games','mg_pwa_v7_ended_games','mg_pwa_v6_ended_games'];
const MIGRATE_ACTIVE_KEYS=['mg_pwa_v9_active_games','mg_pwa_v8_active_games','mg_pwa_v7_active_games'];
const SYSTEMS=['Miniaturgolf\n(Eternit)','Minigolf\n(Beton)','Filzgolf','Cobigolf','Sterngolf'];
const PLAYER_COLORS=['#b71c2a','#a5a315','#2735a3','#b02062','#4caf50','#9c27b0','#ff9800','#03a9f4','#f44336','#607d8b'];
const MAX_PLAYERS=10;
const main=document.getElementById('mainLayer');
const scrim=document.getElementById('scrim');

function blankRound(){return Array(18).fill(null)}
function uid(){return 'g'+Date.now().toString(36)+Math.random().toString(36).slice(2,7)}
function defaultState(){return{gameId:uid(),system:'Miniaturgolf\n(Eternit)',location:'',players:[{name:'Spieler 1',color:'#b71c2a',roundScores:[blankRound()]}],settings:{vib:true,sound:true,wake:false,full:false,bgData:'',tournamentEnabled:false,tournamentTheme:'Hell',stats:false},saveWithStats:false}}
function freshTableState(settings){const fresh=defaultState();fresh.settings={...fresh.settings,...(settings||{})};return fresh}
function safeRead(key,fallback){try{return JSON.parse(localStorage.getItem(key))??fallback}catch(_){return fallback}}
function readFirst(keys,fallback){for(const k of keys){const v=localStorage.getItem(k);if(v){try{return JSON.parse(v)}catch(_){}}}return fallback}
// v55: Die Haupttabelle ist – wie im frischen nativen ViewModel – nur Sitzungsspeicher.
// Bei einem echten PWA-Neustart beginnt sie leer; nur die dauerhaften Einstellungen werden übernommen.
const restoredState=localStorage.getItem(KEY)?safeRead(KEY,defaultState()):readFirst(MIGRATE_KEYS,defaultState());
let state=freshTableState(restoredState?.settings);
// Wie in der nativen App ist die Tickbox kein dauerhaft gespeicherter Preference-Wert.
state.saveWithStats=false;
let activeGames=localStorage.getItem(ACTIVE_KEY)?safeRead(ACTIVE_KEY,[]):readFirst(MIGRATE_ACTIVE_KEYS,[]);
let endedGames=localStorage.getItem(HISTORY_KEY)?safeRead(HISTORY_KEY,[]):readFirst(MIGRATE_HISTORY_KEYS,[]);
let tournamentNotes=safeRead(TOURNAMENT_KEY,[]);
let longPressTimer=null,longPressStartX=0,longPressStartY=0,suppressNextClickUntil=0,roundHeaderSwipe=false,wakeLock=null,devClickCount=0,devLastClick=0;
let transientClickBlockUntil=0;
try{window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change',refreshOpenTournamentThemes)}catch(_){}

function ensureState(){
  if(!state||!Array.isArray(state.players)||!state.players.length) state=defaultState();
  state.gameId=state.gameId||uid();
  state.system=state.system||SYSTEMS[0];
  state.location=state.location||'';
  state.settings={vib:true,sound:true,wake:false,full:false,bgData:'',tournamentEnabled:false,tournamentTheme:'Hell',stats:false,...(state.settings||{})};
  state.saveWithStats=!!state.saveWithStats;
  // Native Logik: Ohne Turnier- oder Statistikmodus ist die Tickbox aus und nicht sichtbar.
  if(!state.settings.tournamentEnabled||!state.settings.stats)state.saveWithStats=false;
  state.players.forEach((p,i)=>{if(p.name==null)p.name=`Spieler ${i+1}`;p.color=p.color||PLAYER_COLORS[i%PLAYER_COLORS.length];if(!Array.isArray(p.roundScores)||!p.roundScores.length)p.roundScores=[blankRound()];p.roundScores=p.roundScores.map(r=>Array.from({length:18},(_,i)=>r?.[i]??null));});
  normalizeRounds(false);
}
function persist(opts={}){ensureState();localStorage.setItem(KEY,JSON.stringify(state));applyBg();if(opts.autosave)autosaveActive()}
function persistActive(){activeGames=activeGames.slice(0,30);localStorage.setItem(ACTIVE_KEY,JSON.stringify(activeGames))}
function persistHistory(){endedGames=endedGames.slice(0,60);localStorage.setItem(HISTORY_KEY,JSON.stringify(endedGames))}
function persistTournament(){tournamentNotes=tournamentNotes.slice(0,120);localStorage.setItem(TOURNAMENT_KEY,JSON.stringify(tournamentNotes))}
function getTournamentThemeMode(){
  const choice=state?.settings?.tournamentTheme||'Hell';
  if(choice==='System'){
    try{return window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'Dunkel':'Hell'}catch(_){return 'Hell'}
  }
  return choice==='Dunkel'?'Dunkel':'Hell';
}
function applyTournamentTheme(el){
  if(!el)return;
  el.classList.remove('tourThemeLight','tourThemeDark');
  el.classList.add(getTournamentThemeMode()==='Dunkel'?'tourThemeDark':'tourThemeLight');
}
function tournamentThemeIcon(){
  const t=state?.settings?.tournamentTheme||'Hell';
  return t==='Dunkel'?icon('dark_mode'):(t==='System'?icon('brightness_auto'):icon('light_mode'));
}
function applyTournamentNativeMetrics(el){
  if(!el)return;
  const appEl=document.querySelector('.app');
  const width=Math.max(320,Math.min(480,appEl?.getBoundingClientRect().width||window.innerWidth||360));
  const s=width/360;
  const set=(name,value)=>el.style.setProperty(name,`${(value*s).toFixed(2)}px`);
  // Media and editor CSS share the same adaptive scale as ResponsiveUtils.kt.
  el.style.setProperty('--native-scale',s.toFixed(5));
  el.style.setProperty('--tour-scale',s.toFixed(5));

  // Shared adaptiveDp/adaptiveSp values from the native Compose tournament screens.
  for(const v of [4,6,8,10,12,14,16,20,24,32,52,54,64,110]) set(`--tour-a${v}`,v);
  el.style.setProperty('--tour-fixed-icon-button','48px');
  el.style.setProperty('--tour-fixed-icon','24px');
  el.style.setProperty('--tour-header-height',`${(48+32*s).toFixed(2)}px`);

  // Tournament editor/details values (TournamentScreen.kt / TournamentHistoryScreen.kt).
  set('--tn-pad4',4);
  set('--tn-pad6',6);
  set('--tn-pad8',8);
  set('--tn-pad12',12);
  set('--tn-pad16',16);
  set('--tn-num-width',28);
  set('--tn-title-size',20);
  set('--tn-number-size',14);
  set('--tn-input-size',13);
  set('--tn-camera-size',50);
  set('--tn-camera-radius',8);
  set('--tn-field-radius',6);
  set('--tn-camera-icon',16);
  set('--tn-input-height',28);
  set('--tn-view-title-size',18);
  set('--tn-view-meta-size',12);
  set('--tn-view-date-icon',12);
  el.style.setProperty('--tn-header-height',`${(48+32*s).toFixed(2)}px`);

  // Tournament selection screen values (TournamentSelectionScreen.kt).
  set('--ts-header-title',20);
  set('--ts-card-height',110);
  set('--ts-card-radius',24);
  set('--ts-card-padding',20);
  set('--ts-card-icon-box',54);
  set('--ts-card-icon-radius',14);
  set('--ts-card-icon-padding',12);
  set('--ts-card-title',17);
  set('--ts-card-subtitle',12);
  set('--ts-option-circle',52);
  set('--ts-option-icon-pad',13);
  set('--ts-option-label',10);

  // Saved tournament notes values (TournamentHistoryScreen.kt).
  set('--th-title-size',20);
  set('--th-card-radius',16);
  set('--th-card-padding',16);
  set('--th-location-size',16);
  set('--th-system-size',14);
  set('--th-date-size',12);
  set('--th-place-icon',16);
  set('--th-date-icon',12);
  set('--th-swipe-padding',24);
  set('--th-swipe-icon',24);
  set('--th-shadow',4);
}
function refreshOpenTournamentThemes(){
  document.querySelectorAll('.tournamentPage,.tournamentEditor,.tournamentHistory,.mediaPage').forEach(el=>{applyTournamentTheme(el);applyTournamentNativeMetrics(el)});
}
window.addEventListener('resize',()=>document.querySelectorAll('.tournamentPage,.tournamentEditor,.tournamentHistory,.mediaPage').forEach(applyTournamentNativeMetrics));


// Native-App-Icons: Die Android-App nutzt hauptsächlich Material Icons in Compose.
// In der PWA werden diese Icons als inline SVGs nachgebaut, damit keine Emoji-/Platzhaltericons
// mehr sichtbar sind und die Darstellung näher an der nativen App liegt.
const ICONS={
  arrow_back:'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.42-1.41L7.83 13H20v-2z',
  arrow_forward:'M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8-8-8z',
  search:'M9.5 3a6.5 6.5 0 0 0 0 13c1.61 0 3.09-.59 4.23-1.57L19.29 20 20.7 18.59l-5.56-5.56A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 0 0 9.5 3zm0 2C12 5 14 7 14 9.5S12 14 9.5 14 5 12 5 9.5 7 5 9.5 5z',
  close:'M18.3 5.71 12 12l6.3 6.29-1.41 1.41L10.59 13.41 4.29 19.71 2.88 18.3 9.17 12 2.88 5.71 4.29 4.29l6.3 6.3 6.29-6.3z',
  add:'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  add_circle:'M12 2a10 10 0 1 0 .01 0zM17 13h-4v4h-2v-4H7v-2h4V7h2v4h4v2z',
  add_circle_outline:'M13 7h-2v4H7v2h4v4h2v-4h4v-2h-4V7zm-1-5a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
  stop:'M6 6h12v12H6z',
  play_arrow:'M8 5v14l11-7z',
  play_circle_outline:'M10 16.5 16 12l-6-4.5v9zM12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16z',
  check:'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  check_circle:'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 15-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z',
  block:'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM4 12a8 8 0 0 1 12.9-6.32L5.68 16.9A7.97 7.97 0 0 1 4 12zm8 8a7.97 7.97 0 0 1-4.9-1.68L18.32 7.1A8 8 0 0 1 12 20z',
  history:'M13 3a9 9 0 1 1-8.49 6H2l3.3-3.3L8.7 9H6.54A7 7 0 1 0 13 5c-1.93 0-3.68.78-4.95 2.05L6.64 5.64A8.96 8.96 0 0 1 13 3zm-1 5h1.5v5l4.2 2.5-.75 1.23L12 14V8z',
  share:'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11A2.99 2.99 0 1 0 15 5c0 .24.04.47.09.7L8.04 9.81A3 3 0 1 0 8.04 14.2l7.12 4.18c-.05.2-.08.41-.08.62a2.92 2.92 0 1 0 2.92-2.92z',
  delete:'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zm7.5-5-1-1h-5l-1 1H5v2h14V4z',
  edit:'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  save:'M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zM12 19a3 3 0 1 1 0-6 3 3 0 0 1 0 6zM6 8V5h9v3H6z',
  save_alt:'M19 12v7H5v-7H3v7c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7h-2zm-6-9h-2v9.17L7.41 8.59 6 10l6 6 6-6-1.41-1.41L13 12.17V3z',
  zoom_in:'M9.5 3A6.5 6.5 0 1 0 13.53 14.6L19 20.07 20.07 19l-5.47-5.47A6.5 6.5 0 0 0 9.5 3zm0 2A4.5 4.5 0 1 1 5 9.5 4.5 4.5 0 0 1 9.5 5zM9 7v2H7v1.5h2v2h1.5v-2h2V9h-2V7H9z',
  place:'M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z',
  calendar_month:'M20 3h-1V1h-2v2H7V1H5v2H4C2.9 3 2 3.9 2 5v15c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 17H4V8h16v12z',
  clock:'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  emoji_events:'M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.64 4.39 4.94A5.01 5.01 0 0 0 11 15.9V19H8v2h8v-2h-3v-3.1a5.01 5.01 0 0 0 3.61-2.96C19.08 12.64 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82A3.01 3.01 0 0 1 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z',
  settings:'M19.43 12.98c.04-.32.07-.65.07-.98s-.02-.66-.07-.98l2.11-1.65a.5.5 0 0 0 .12-.64l-2-3.46a.5.5 0 0 0-.61-.22l-2.49 1a7.28 7.28 0 0 0-1.69-.98L14.5 2.42A.5.5 0 0 0 14 2h-4a.5.5 0 0 0-.5.42L9.12 5.07c-.61.23-1.18.55-1.69.98l-2.49-1a.5.5 0 0 0-.61.22l-2 3.46a.5.5 0 0 0 .12.64l2.11 1.65c-.04.32-.06.65-.06.98s.02.66.07.98l-2.11 1.65a.5.5 0 0 0-.12.64l2 3.46c.14.24.43.34.68.22l2.49-1c.51.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.61-.25 1.18-.58 1.69-.98l2.49 1c.25.12.54.02.68-.22l2-3.46a.5.5 0 0 0-.12-.64l-2.11-1.65zM12 15.5A3.5 3.5 0 1 1 12 8a3.5 3.5 0 0 1 0 7.5z',
  refresh:'M17.65 6.35A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.45 5h-2.1A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h8V3l-3.35 3.35z',
  power_settings_new:'M13 3h-2v10h2V3zm4.83 2.17-1.42 1.42A6.92 6.92 0 0 1 19 12a7 7 0 1 1-11.41-5.41L6.17 5.17A9 9 0 1 0 21 12c0-2.74-1.23-5.19-3.17-6.83z',
  description:'M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zM13 9V3.5L18.5 9H13zM8 13h8v2H8v-2zm0 4h8v2H8v-2zm0-8h3v2H8V9z',
  file_upload:'M5 20h14v-2H5v2zM19 13h-4v6H9v-6H5l7-7 7 7z',
  file_download:'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
  more_vert:'M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  cloud_upload:'M19.35 10.04A7.49 7.49 0 0 0 5.5 8C2.46 8 0 10.46 0 13.5S2.46 19 5.5 19H19c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM13 13v4h-2v-4H8l4-4 4 4h-3z',
  cloud_download:'M19.35 10.04A7.49 7.49 0 0 0 5.5 8C2.46 8 0 10.46 0 13.5S2.46 19 5.5 19H19c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM11 11h2v4h3l-4 4-4-4h3v-4z',
  cloud:'M19.35 10.04A7.49 7.49 0 0 0 5.5 8C2.46 8 0 10.46 0 13.5S2.46 19 5.5 19H19c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
  content_copy:'M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8C6.9 5 6 5.9 6 7v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  bar_chart:'M5 9.2h3V19H5V9.2zm5.5-4.2h3V19h-3V5zm5.5 7h3v7h-3v-7z',
  light_mode:'M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.8 1.42-1.42zM1 13h3v-2H1v2zm10-12h2v3h-2V1zm8.04 2.46-1.41-1.41-1.8 1.79 1.42 1.42 1.79-1.8zM17.24 19.16l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 11v2h3v-2h-3zM12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12zm-1 14h2v3h-2v-3zm-7.45-.45 1.41 1.41 1.8-1.79-1.42-1.42-1.79 1.8z',
  dark_mode:'M12 2a10 10 0 0 0 0 20c4.09 0 7.61-2.46 9.16-5.98A8 8 0 0 1 12 4a7.9 7.9 0 0 1 .53-2H12z',
  brightness_auto:'M10.85 12.65h2.3L12 9.3l-1.15 3.35zM20 8.69V4h-4.69L12 0.69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM14.3 16l-.7-2h-3.2l-.7 2H7.8L11 8h2l3.2 8h-1.9z',
  camera_alt:'M20 5h-3.17l-1.84-2H9.01L7.17 5H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-8 13a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z',
  add_a_photo:'M3 5h3.17L8 3h8l1.83 2H21c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H3c-1.1 0-2-.9-2-2V7c0-1.1.9-2 2-2zm9 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0-8a3 3 0 1 1 0 6 3 3 0 0 1 0-6zm7-4h-2V4h-2v2h-2v2h2v2h2V8h2V6z',
  fullscreen:'M5 5h5V3H3v7h2V5zm9-2v2h5v5h2V3h-7zm5 16h-5v2h7v-7h-2v5zM5 14H3v7h7v-2H5v-5z',
  vibration:'M0 15h2V9H0v6zm3 4h2V5H3v14zm18-10v6h3V9h-3zm-2 10h2V5h-2v14zM16 3H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H8V5h8v14z',
  volume_up:'M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05A4.5 4.5 0 0 0 16.5 12zm-2.5-8.35v2.06A7 7 0 0 1 14 18.29v2.06a9 9 0 0 0 0-16.7z',
  image:'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 11.5l2.5 3.01L14.5 10l4.5 6H5l3.5-4.5z',
  image_not_supported:'M21.9 21.9 2.1 2.1.69 3.51 3 5.83V19c0 1.1.9 2 2 2h13.17l1.41 1.41 1.42-1.42zM5 19V7.83L16.17 19H5zm14-14v10.17L14.83 11 17 8.5 20 12V5c0-1.1-.9-2-2-2H8.83l2 2H19z',
  info:'M11 17h2v-6h-2v6zm0-8h2V7h-2v2zM12 2a10 10 0 1 0 .01 0z',
  photo_library:'M22 16V4c0-1.1-.9-2-2-2H8C6.9 2 6 2.9 6 4v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11.5-2.67 2.5 3.01 3.5-4.51 4.5 6H8l2.5-4.5zM2 6H0v14c0 1.1.9 2 2 2h14v-2H2V6z',

  arrow_drop_down:'M7 10l5 5 5-5H7z',
  keyboard_arrow_down:'M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z',
  brightness_high:'M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69L23.31 12 20 8.69zM12 18a6 6 0 1 1 0-12 6 6 0 0 1 0 12zm0-10a4 4 0 1 0 0 8 4 4 0 0 0 0-8z',
  military_tech:'M17 10.43V2H7v8.43c0 .35.18.68.49.86l3.18 1.91-.75 1.78-2.42.21 1.84 1.59-.55 2.37L12 17.9l3.21 1.25-.55-2.37 1.84-1.59-2.42-.21-.75-1.78 3.18-1.91c.31-.18.49-.51.49-.86zM9 4h6v5.86l-3 1.8-3-1.8V4z',
  photo:'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 11.5l2.5 3.01L14.5 10l4.5 6H5l3.5-4.5z',
  contrast:'M12 2a10 10 0 1 0 0 20V2zm0 18a8 8 0 0 1 0-16v16z',
  palette:'M12 3C7.03 3 3 6.58 3 11c0 3.87 3.13 7 7 7h1.65c.74 0 1.35-.61 1.35-1.35 0-.35-.14-.69-.38-.94-.24-.25-.38-.58-.38-.94 0-.74.61-1.35 1.35-1.35H15c2.76 0 5-2.24 5-5C20 5.43 16.42 3 12 3zM6.5 12A1.5 1.5 0 1 1 6.5 9a1.5 1.5 0 0 1 0 3zm3-4A1.5 1.5 0 1 1 9.5 5a1.5 1.5 0 0 1 0 3zm5 0A1.5 1.5 0 1 1 14.5 5a1.5 1.5 0 0 1 0 3zm2.5 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z',
  blur_on:'M6 13a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm6 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM9 7.5A1.5 1.5 0 1 0 9 4.5a1.5 1.5 0 0 0 0 3zm6 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM9 19.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm6 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
  filter_hdr:'M3 5v14h18V5H3zm2 12 4-5 3 4 2-3 5 4H5z',
  trophy:'M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.64 4.39 4.94A5.01 5.01 0 0 0 11 15.9V19H8v2h8v-2h-3v-3.1a5.01 5.01 0 0 0 3.61-2.96C19.08 12.64 21 10.55 21 8V7c0-1.1-.9-2-2-2z',
  score:'M3 3h18v18H3V3zm2 2v14h14V5H5zm2 2h10v2H7V7zm0 4h10v2H7v-2zm0 4h7v2H7v-2z'
};
function icon(name,cls='mi'){
  // Icons.Default.Info aus Compose ist ein gefüllter Kreis mit ausgespartem "i".
  // Der frühere Ein-Pfad-Nachbau füllte Kreis und Zeichen gleichfarbig und wirkte
  // deshalb nur wie ein Punkt. Even-Odd erhält die beiden Aussparungen zuverlässig.
  if(name==='info'){
    return `<svg class="${cls} mi-info" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path fill-rule="evenodd" clip-rule="evenodd" d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zM11 7h2v2h-2V7zm0 4h2v6h-2v-6z"></path></svg>`;
  }
  if(!ICONS[name])return icon('info',cls);
  const d=ICONS[name];
  return `<svg class="${cls} mi-${name}" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="${d}"></path></svg>`;
}

function applyBg(){const bg=document.querySelector('.bg');if(bg)bg.style.backgroundImage=state?.settings?.bgData?`url(${state.settings.bgData})`:`url('assets/bg_minigolf.jpg')`}
function overlayOn(){main.classList.add('blurred');scrim.classList.add('show')}
function overlayOff(){main.classList.remove('blurred');scrim.classList.remove('show')}

// v58: Web-Pendant zu AudioManager.FX_KEY_CLICK und ProvideSafeHaptic.
// Sound und Vibration werden zentral an den aktuell gespeicherten Einstellungen geprüft.
let touchAudioContext=null,touchSoundAt=0,hapticAt=0,feedbackAt=0;
function playTouchSound(){
  if(!state?.settings?.sound)return;
  const now=performance.now();if(now-touchSoundAt<55)return;touchSoundAt=now;
  try{
    const AudioCtx=window.AudioContext||window.webkitAudioContext;if(!AudioCtx)return;
    touchAudioContext=touchAudioContext||new AudioCtx();
    const play=()=>{
      const ctx=touchAudioContext,t=ctx.currentTime;
      const osc=ctx.createOscillator(),gain=ctx.createGain();
      osc.type='triangle';
      osc.frequency.setValueAtTime(1450,t);
      osc.frequency.exponentialRampToValueAtTime(760,t+0.022);
      gain.gain.setValueAtTime(0.045,t);
      gain.gain.exponentialRampToValueAtTime(0.0001,t+0.028);
      osc.connect(gain);gain.connect(ctx.destination);
      osc.start(t);osc.stop(t+0.03);
    };
    if(touchAudioContext.state==='suspended')touchAudioContext.resume().then(play).catch(()=>{});else play();
  }catch(_){ }
}
function haptic(pattern=18){
  if(!state?.settings?.vib||!navigator.vibrate)return;
  const now=performance.now();if(now-hapticAt<45)return;hapticAt=now;
  try{navigator.vibrate(pattern)}catch(_){ }
}
function golfFeedback({sound=true,vibration=true,pattern=18}={}){
  const now=performance.now();if(now-feedbackAt<45)return;feedbackAt=now;
  if(sound)playTouchSound();
  if(vibration)haptic(pattern);
}
window.mgTouchSound=playTouchSound;
window.mgHaptic=haptic;
window.mgFeedback=golfFeedback;

const GOLF_FEEDBACK_SELECTOR=[
  'button:not(:disabled)','a[href]','.drawerItem','.scoreCell','.nameCell','#roundHeader','#addPlayerTop',
  '#logoBtn','#systemBtn','.locationBox','.systemItem','.roundItem','.historyCard','.activeCard',
  '.nativeGameCard','.tournamentCard','.tourSmall','.settingsRow','.mediaGalleryItem'
].join(',');
function installGolfFeedbackBridge(){
  document.addEventListener('click',e=>{
    const target=e.target?.closest?.(GOLF_FEEDBACK_SELECTOR);
    if(!target||target.closest('[data-no-golf-feedback]'))return;
    if(target.matches('[aria-disabled="true"]')&&target.id==='addPlayerTop')return;
    golfFeedback();
  },true);
}
function toast(t){const el=document.getElementById('toast');el.textContent=t;el.style.display='block';clearTimeout(window.__to);window.__to=setTimeout(()=>el.style.display='none',1600)}
function esc(s){return String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}
function hasAnyScore(g=state){return g.players?.some(p=>p.roundScores?.some(r=>r.some(v=>v!=null)))}
function lastRoundIndex(){return (state.players[0]?.roundScores.length||1)-1}
function isPastRound(ri){return ri<lastRoundIndex()}
function playerTotal(p){return (p.roundScores||[]).flat().reduce((a,b)=>a+(b||0),0)}
function roundTotal(p,r){return (p.roundScores?.[r]||[]).reduce((a,b)=>a+(b||0),0)}
function playedCount(scores){return (scores||[]).filter(v=>v!=null&&v>0).length}
function currentHole(){const last=lastRoundIndex();for(let i=0;i<18;i++){if(state.players.some(p=>p.roundScores[last]?.[i]==null))return i}return 17}
function normalizeRounds(doPersist=true){const n=state.players[0]?.roundScores.length||1;state.players.forEach(p=>{while(p.roundScores.length<n)p.roundScores.push(blankRound());while(p.roundScores.length>n)p.roundScores.pop()});if(doPersist)persist()}
function roundFlexStyle(r,n){if(n<=1)return 'flex:1';const grow=(r===n-1)?1:(1/(n-1));return `flex:${grow} 1 0`}
function cellBackgroundClass(i){return i%2===0?'odd':'even'}
function formatGameDate(date){try{const d=new Date(date);return d.toLocaleDateString('de-DE',{day:'2-digit',month:'2-digit',year:'numeric'})+' - '+d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'})}catch(_){return ''}}
function scoreColorClass(total, rounds=1, played=0, def='scoreColorBlack'){
  if(total===0)return 'scoreColorBlack';
  const effective=played>0?(rounds*18+(total-played)):total, avg=effective/rounds, sys=state.system;
  if(sys.includes('Eternit')){if(avg<18)return def;if(avg<20)return 'scoreColorBlue';if(avg<25)return 'scoreColorGreen';if(avg<30)return 'scoreColorRed';return 'scoreColorBlack'}
  if(sys.includes('Beton')){if(avg<18)return def;if(avg<25)return 'scoreColorBlue';if(avg<30)return 'scoreColorGreen';if(avg<36)return 'scoreColorRed';return 'scoreColorBlack'}
  if(avg<18)return def;if(avg<30)return 'scoreColorBlue';if(avg<36)return 'scoreColorGreen';if(avg<40)return 'scoreColorRed';return 'scoreColorBlack';
}

function gameSnapshot(reason='Aktives Spiel'){
  ensureState();
  return {id:state.gameId||uid(),date:new Date().toISOString(),reason,system:state.system,location:state.location,hasStats:!!state.saveWithStats,players:state.players.map(p=>({name:p.name,color:p.color,roundScores:p.roundScores.map(r=>r.slice())})),total:state.players.map(p=>({name:p.name,total:playerTotal(p)}))};
}
function snapshotToState(g){return {gameId:g.id||uid(),system:g.system||SYSTEMS[0],location:g.location||'',players:(g.players||defaultState().players).map((p,i)=>({name:p.name||`Spieler ${i+1}`,color:p.color||PLAYER_COLORS[i%PLAYER_COLORS.length],roundScores:(p.roundScores||[blankRound()]).map(r=>Array.from({length:18},(_,i)=>r?.[i]??null))})),settings:state.settings,saveWithStats:false}}
function autosaveActive(force=false){
  if(!force&&!hasAnyScore())return;
  const snap=gameSnapshot('Aktives Spiel');
  const idx=activeGames.findIndex(g=>g.id===snap.id);
  if(idx>=0)activeGames[idx]=snap; else activeGames.unshift(snap);
  persistActive();
}
function removeActive(id=state.gameId){activeGames=activeGames.filter(g=>g.id!==id);persistActive()}
function saveCurrentToHistory(reason){
  if(!hasAnyScore())return false;
  const snap=gameSnapshot(reason);
  snap.id=uid();
  endedGames.unshift(snap);persistHistory();removeActive(state.gameId);return true;
}
function finishActiveGame(g){
  const isCurrentlyLoaded=!!g?.id&&g.id===state.gameId;
  endedGames.unshift({...g,id:uid(),reason:'Spiel beendet',date:new Date().toISOString()});
  persistHistory();
  activeGames=activeGames.filter(x=>x.id!==g.id);
  persistActive();
  // Native GolfViewModel.completeGame(): Nur wenn genau dieses Spiel geladen ist,
  // wird die sichtbare Haupttabelle vollständig geleert.
  if(isCurrentlyLoaded)resetToStandard();
}
function resetKeepingPlayers(){const players=state.players.map(p=>({name:p.name,color:p.color,roundScores:[blankRound()]}));state={...state,gameId:uid(),players,saveWithStats:false};persist();render()}
function resetToStandard(){const currentId=state.gameId,settings=state.settings;removeActive(currentId);state=freshTableState(settings);persist();render()}
function loadGame(g){state=snapshotToState(g);persist();render();toast('Spiel geladen')}

function render(){
  ensureState();applyBg();const h=currentHole();const n=state.players[0]?.roundScores.length||1;document.documentElement.style.setProperty('--footer-factor',n>1?'1.5':'1');
  main.className='mainLayer';scrim.className='scrim';
  main.innerHTML=`<div class="topbar"><div class="top-left"><img class="logo" src="assets/minigolf_logo.png" id="logoBtn"><div class="locationBox"><span class="pin">${icon('place')}</span><input id="locInput" value="${esc(state.location)}" placeholder="Ort angeben..."></div></div><div class="systemBox" id="systemBtn"><span>${esc(state.system)}</span><span class="systemDrop">${icon('arrow_drop_down')}</span></div></div><div class="scoreArea"><div class="scoreViewport" id="scoreViewport"><div class="scoreContent">${renderHeader(n)}<div class="scoreSpacer"></div><div class="scoreBody">${Array.from({length:18},(_,i)=>renderHoleRow(i,h,n)).join('')}</div><div class="scoreSpacer"></div>${renderFooter(n)}</div></div></div>`;
  document.getElementById('locInput').addEventListener('input',e=>{state.location=e.target.value;persist();});
  document.getElementById('locInput').addEventListener('change',()=>autosaveActive());
  document.getElementById('logoBtn').onclick=showDrawer;
  document.getElementById('systemBtn').onclick=e=>showSystemMenu(e.currentTarget);
  const statsCheck=document.getElementById('saveWithStatsCheck');
  if(statsCheck){
    statsCheck.addEventListener('pointerdown',e=>{e.stopPropagation()});
    statsCheck.addEventListener('click',e=>{
      e.preventDefault();e.stopPropagation();
      state.saveWithStats=!state.saveWithStats;
      statsCheck.classList.toggle('checked',state.saveWithStats);
      statsCheck.setAttribute('aria-pressed',state.saveWithStats?'true':'false');
      statsCheck.innerHTML=state.saveWithStats?icon('check'):'';
      persist({autosave:hasAnyScore()});
    });
  }
  bindRoundHeaderGestures();
  bindPlayerHeaderDrag();
  bindScoreViewportDragScroll();
  requestAnimationFrame(applyMainNativeMetrics);
  syncWinnerBadge();
}
function renderHeader(n){const canAdd=state.players.length<MAX_PLAYERS;return `<div class="scoreRow scoreHeader"><div class="cell leftCell black leftTop gesture" id="roundHeader"><span class="roundRefresh">${icon('refresh')}</span><span class="roundNum">${n}</span></div>${state.players.map((p,i)=>`<div class="cell playerCol nameCell gesture" style="--pcolor:${p.color};background:${p.color}" data-edit="${i}" data-player-head="${i}" data-player-col="${i}"><span>${esc(p.name)}</span></div>`).join('')}<div class="cell rightSide rightTop${canAdd?'':' addPlayerLimit'}" id="addPlayerTop" aria-disabled="${canAdd?'false':'true'}">${canAdd?`<span class="plusIcon">${icon('add')}</span>`:''}</div></div>`}
function renderHoleRow(i,h,n){return `<div class="scoreRow holeRow"><div class="cell leftCell ${i===h?'current':(i%2?'black':'black2')}">${i+1}</div>${state.players.map((p,pi)=>`<div class="playerCol" data-player-col="${pi}" style="--pcolor:${p.color}">${p.roundScores.map((scores,ri)=>{const val=scores[i], isLast=ri===n-1, cur=i===h&&isLast;return `<div class="cell scoreCell gesture ${cur?'current':cellBackgroundClass(i)} ${!isLast&&!cur?'roundShadow':''}" style="${roundFlexStyle(ri,n)}" data-p="${pi}" data-r="${ri}" data-h="${i}">${val??''}</div>`}).join('')}</div>`).join('')}<div class="cell rightSide"></div></div>`}
function renderFooter(n){
  const statsVisible=!!state.settings.tournamentEnabled&&!!state.settings.stats;
  const statsBox=statsVisible
    ?`<button type="button" class="statsCheck ${state.saveWithStats?'checked':''}" id="saveWithStatsCheck" aria-label="Ergebnis mit Statistiken speichern" aria-pressed="${state.saveWithStats?'true':'false'}">${state.saveWithStats?icon('check'):''}</button>`
    :'';
  return `<div class="scoreRow scoreFooter"><div class="cell leftCell black leftBottom">${statsBox}</div>${state.players.map(p=>{const total=playerTotal(p), totalClass=scoreColorClass(total,n,p.roundScores.flat().filter(v=>v!=null&&v>0).length,'scoreColorWhite');if(n===1)return `<div class="cell footerCol footerSingle ${totalClass}" data-player-col="${state.players.indexOf(p)}" style="--pcolor:${p.color}">${total}</div>`;return `<div class="footerCol" data-player-col="${state.players.indexOf(p)}" style="--pcolor:${p.color}"><div class="roundTotals">${p.roundScores.map((rs,ri)=>{const rt=roundTotal(p,ri), cls=scoreColorClass(rt,1,playedCount(rs),'scoreColorBlack');return `<div class="cell roundTotalCell ${ri<n-1?'roundShadow':''} ${cls}" style="${roundFlexStyle(ri,n)}">${rt}</div>`}).join('')}</div><div class="footerDivider"></div><div class="cell grandTotal ${totalClass}">${total}</div></div>`}).join('')}<div class="cell rightSide rightBottom"></div></div>`;
}

function openMainTransient(){
  return document.querySelector('.systemMenu,.roundMenu,.nativeScoreOverlay,.overlay.show');
}
function closeMainDropdowns(){
  document.querySelectorAll('.systemMenu,.roundMenu').forEach(el=>el.remove());
}
function closeScoreCycle(){
  const ov=document.querySelector('.nativeScoreOverlay,.overlay.show');
  if(!ov)return false;
  if(typeof ov._nativeDismiss==='function')ov._nativeDismiss();
  else{ov.remove();overlayOff();document.body.classList.remove('scoreCycleOpen')}
  return true;
}
function closeMainTransient(){
  const hadDropdown=!!document.querySelector('.systemMenu,.roundMenu');
  const hadScore=!!document.querySelector('.nativeScoreOverlay,.overlay.show');
  closeMainDropdowns();
  if(hadScore)closeScoreCycle();
  return hadDropdown||hadScore;
}
function swallowCurrentActivation(e){
  // Android/Chrome kann nach einem Pointer-Up noch einen kompatiblen Click erzeugen.
  // Wird das Score-Overlay im selben Pointer-Up entfernt, würde dieser Click die
  // darunterliegende Tabellenzelle treffen und den Score-Cycle sofort neu öffnen.
  transientClickBlockUntil=Date.now()+520;
  suppressNextClickUntil=Math.max(suppressNextClickUntil,Date.now()+520);
  if(e?.cancelable)e.preventDefault();
  e?.stopImmediatePropagation?.();
  e?.stopPropagation?.();
}

// Capture-Phase: den nachgereichten mobilen Click abfangen, bevor er die neu
// freigelegte Tabelle erreicht. Normale Klicks außerhalb des kurzen Fensters
// bleiben vollständig unverändert.
document.addEventListener('click',e=>{
  if(Date.now()>=transientClickBlockUntil)return;
  if(e.cancelable)e.preventDefault();
  e.stopImmediatePropagation();
  e.stopPropagation();
},true);
function showSystemMenu(anchor){
  document.querySelectorAll('.systemMenu').forEach(x=>x.remove());
  haptic();
  const r=anchor.getBoundingClientRect(),appRect=visibleAppRect();
  const options=SYSTEMS.map((sys,i)=>({sys,i})).filter(entry=>entry.sys!==state.system);
  const m=document.createElement('div');
  m.className='systemMenu gesture';
  m.style.visibility='hidden';
  m.innerHTML=options.map(({sys,i})=>`<div class="systemItem" data-system="${i}">${esc(sys)}</div>`).join('');
  document.body.appendChild(m);
  const width=m.offsetWidth,height=m.offsetHeight;
  const left=Math.max(appRect.left+8,Math.min(r.right-width,appRect.right-width-8));
  const top=Math.max(appRect.top+8,Math.min(r.bottom,appRect.bottom-height-8));
  m.style.left=`${left}px`;
  m.style.top=`${top}px`;
  m.style.visibility='visible';
  const close=ev=>{if(!m.contains(ev.target)&&ev.target!==anchor){m.remove();document.removeEventListener('pointerdown',close,true)}};
  setTimeout(()=>document.addEventListener('pointerdown',close,true),0);
  m.addEventListener('click',e=>{
    const it=e.target.closest('[data-system]');if(!it)return;
    state.system=SYSTEMS[Number(it.dataset.system)];
    persist({autosave:true});m.remove();render();haptic();
  });
}
function clearLongPressTimer(){if(longPressTimer){clearTimeout(longPressTimer);longPressTimer=null}}
function mainPointerDown(e){
  const t=e.target.closest('[data-p][data-r][data-h]');if(!t||e.target.closest('input,textarea'))return;if(e.pointerType==='mouse'&&e.button!==0)return;
  if(e.cancelable)e.preventDefault();const ri=Number(t.dataset.r);if(!isPastRound(ri))return;
  longPressStartX=e.clientX;longPressStartY=e.clientY;try{t.setPointerCapture(e.pointerId)}catch(_){}clearLongPressTimer();
  longPressTimer=setTimeout(()=>{suppressNextClickUntil=Date.now()+850;openWheel(Number(t.dataset.p),ri,Number(t.dataset.h),t);longPressTimer=null},620);
}
function mainPointerMove(e){if(!longPressTimer)return;if(e.cancelable)e.preventDefault();if(Math.hypot(e.clientX-longPressStartX,e.clientY-longPressStartY)>16)clearLongPressTimer()}
function cancelLongPress(){clearLongPressTimer()}
function mainClick(e){
  if(Date.now()<suppressNextClickUntil){e.preventDefault();return}
  const t=e.target.closest('[data-p][data-r][data-h]');
  if(t){const ri=Number(t.dataset.r);if(isPastRound(ri)){toast('Alte Runde: zum Bearbeiten halten');return}openWheel(Number(t.dataset.p),ri,Number(t.dataset.h),t);return}
  const ed=e.target.closest('[data-edit]');if(ed){editPlayer(Number(ed.dataset.edit));return}
  if(e.target.closest('#addPlayerTop')){if(state.players.length<MAX_PLAYERS)showPlayerDialog(null);return}
  if(e.target.closest('#roundHeader')){if(roundHeaderSwipe){roundHeaderSwipe=false;return}showRoundMenu(e.target.closest('#roundHeader'));return}
}
function bindDynamicClicks(){main.addEventListener('click',mainClick);main.addEventListener('pointerdown',mainPointerDown,{passive:false});main.addEventListener('pointermove',mainPointerMove,{passive:false});main.addEventListener('pointerup',cancelLongPress);main.addEventListener('pointercancel',cancelLongPress)}


function bindScoreViewportDragScroll(){
  const vp=document.getElementById('scoreViewport');
  if(!vp)return;
  let active=false,dragging=false,sx=0,sy=0,startScroll=0,pid=null,downTarget=null;
  vp.addEventListener('pointerdown',e=>{
    if(e.pointerType==='mouse'&&e.button!==0)return;
    if(e.target.closest('#roundHeader,[data-player-head],button,input,textarea,select,.overlay,.drawerOverlay,.dialogLayer'))return;
    active=true;dragging=false;sx=e.clientX;sy=e.clientY;startScroll=vp.scrollLeft;pid=e.pointerId;downTarget=e.target.closest('[data-p][data-r][data-h]');
    try{vp.setPointerCapture(e.pointerId)}catch(_){}
  },{passive:false});
  vp.addEventListener('pointermove',e=>{
    if(!active||e.pointerId!==pid)return;
    const dx=e.clientX-sx,dy=e.clientY-sy;
    if(!dragging&&Math.abs(dx)>10&&Math.abs(dx)>Math.abs(dy)*1.20){dragging=true;suppressNextClickUntil=Date.now()+900;vp.classList.add('tableDragging')}
    if(dragging){if(e.cancelable)e.preventDefault();vp.scrollLeft=startScroll-dx;suppressNextClickUntil=Date.now()+500;clearLongPressTimer()}
  },{passive:false});
  const end=e=>{
    if(!active||e.pointerId!==pid)return;
    const dx=e.clientX-sx,dy=e.clientY-sy;
    if(dragging){if(e.cancelable)e.preventDefault();suppressNextClickUntil=Date.now()+650}
    else if(downTarget && Math.hypot(dx,dy)<7 && Date.now()>=suppressNextClickUntil){
      // Direkter Fallback für Punkte: falls Browser durch Drag-Setup keinen click feuert.
      const ri=Number(downTarget.dataset.r);
      if(!isPastRound(ri)){e.preventDefault();openWheel(Number(downTarget.dataset.p),ri,Number(downTarget.dataset.h),downTarget);suppressNextClickUntil=Date.now()+650;}
    }
    active=false;dragging=false;pid=null;downTarget=null;vp.classList.remove('tableDragging')
  };
  vp.addEventListener('pointerup',end,{passive:false});
  vp.addEventListener('pointercancel',end,{passive:false});
}

function bindRoundHeaderGestures(){
  // Native ScoreTable: Die linke obere Ecke öffnet das Menü nur per Tippen.
  // Das Löschen per Wischgeste findet ausschließlich auf den einzelnen Rundenzeilen statt.
  const h=document.getElementById('roundHeader');if(!h)return;
  h.style.touchAction='manipulation';
}
function bindNativeRoundSwipe(item,{index,roundCount,closeMenu}){
  let active=false,pid=null,sx=0,sy=0,dx=0,horizontal=false;
  const foreground=item.querySelector('.roundItemForeground');
  const deleteIcon=item.querySelector('.roundDeleteIcon');
  // ScoreTable.kt setzt offsetX am Ende direkt auf 0; es gibt dort keine
  // zusätzliche Web-Rückfederung. Dadurch wirkt der Swipe exakt wie Compose.
  const reset=()=>{
    active=false;pid=null;dx=0;horizontal=false;
    item.classList.remove('roundSwiping','swipeRight','swipeLeft');
    if(foreground){foreground.style.transition='none';foreground.style.transform='translateX(0px)'}
    item.style.setProperty('--round-swipe-alpha','0');
    if(deleteIcon)deleteIcon.style.transform='scale(.5)';
  };
  item.addEventListener('pointerdown',e=>{
    if(e.pointerType==='mouse'&&e.button!==0)return;
    active=true;pid=e.pointerId;sx=e.clientX;sy=e.clientY;dx=0;horizontal=false;
    if(foreground)foreground.style.transition='none';
    try{item.setPointerCapture(e.pointerId)}catch(_){ }
  },{passive:true});
  item.addEventListener('pointermove',e=>{
    if(!active||e.pointerId!==pid||roundCount<=1)return;
    const mx=e.clientX-sx,my=e.clientY-sy;
    if(!horizontal&&Math.abs(mx)>7&&Math.abs(mx)>Math.abs(my)*1.05)horizontal=true;
    if(!horizontal)return;
    if(e.cancelable)e.preventDefault();
    // Compose akkumuliert den realen Drag ohne künstliche Begrenzung auf die
    // Menübreite. Hintergrundalpha und Papierkorbskalierung folgen offsetX.
    dx=mx;
    item.classList.add('roundSwiping');
    item.classList.toggle('swipeRight',dx>0);
    item.classList.toggle('swipeLeft',dx<0);
    item.style.setProperty('--round-swipe-alpha',String(Math.min(.8,Math.abs(dx)/300)));
    if(deleteIcon)deleteIcon.style.transform=`scale(${Math.max(.5,Math.min(1.2,Math.abs(dx)/200))})`;
    if(foreground)foreground.style.transform=`translateX(${dx}px)`;
  },{passive:false});
  const finish=e=>{
    if(!active||e.pointerId!==pid)return;
    const width=Math.max(1,item.getBoundingClientRect().width);
    const shouldDelete=roundCount>1&&horizontal&&Math.abs(dx)>width*.75;
    if(shouldDelete){
      if(e.cancelable)e.preventDefault();
      haptic(28);
      // Vor dem Dialog vollständig entfernen, damit der erste Bestätigungstap
      // nicht vom Popup abgefangen wird.
      closeMenu({immediate:true});
      requestAnimationFrame(()=>confirmDialog(
        'Runde löschen?',
        `Möchtest du Runde ${index+1} wirklich löschen? Alle eingetragenen Punkte dieser Runde gehen verloren.`,
        ()=>{removeRound(index);toast('Runde entfernt')},
        'Löschen'
      ));
      return;
    }
    const wasTap=!horizontal&&Math.hypot(e.clientX-sx,e.clientY-sy)<7;
    reset();
    if(wasTap)closeMenu();
  };
  item.addEventListener('pointerup',finish,{passive:false});
  item.addEventListener('pointercancel',reset,{passive:true});
}
function showRoundMenu(anchor){
  document.querySelectorAll('.roundMenu').forEach(x=>x.remove());
  const r=anchor.getBoundingClientRect(),appRect=visibleAppRect(),n=state.players[0]?.roundScores.length||1;
  const m=document.createElement('div');m.className='roundMenu gesture';
  m.style.visibility='hidden';
  // Die echten Swipe-Zeilen sind absolut aufgebaut und würden deshalb keine
  // intrinsische Popupbreite liefern. Der unsichtbare Sizer bildet exakt die
  // breiteste native Zeile (Plus + 8dp + „Neue Runde“) ab.
  const sizer=n<4?`<div class="roundMenuSizer" aria-hidden="true"><span class="bigPlus">${icon('add')}</span><span>Neue Runde</span></div>`:'';
  m.innerHTML=sizer+Array.from({length:n},(_,i)=>`<div class="roundItem gesture" data-round-item="${i}"><div class="roundSwipeBackground"><span class="roundDeleteIcon">${icon('delete')}</span></div><div class="roundItemForeground"><span>Runde ${i+1}</span></div></div>`).join('')+(n<4?`<div class="roundItem roundAddItem" id="addRoundItem"><div class="roundItemForeground"><span class="bigPlus">${icon('add')}</span><span>Neue Runde</span></div></div>`:'');
  let outsideClose=null,closing=false;
  const closeMenu=({immediate=false}={})=>{
    if(closing)return;closing=true;
    if(outsideClose)document.removeEventListener('pointerdown',outsideClose,true);
    outsideClose=null;
    if(immediate){m.remove();return}
    m.classList.remove('roundMenuOpen');
    m.classList.add('roundMenuClosing');
    setTimeout(()=>m.remove(),75);
  };
  document.body.appendChild(m);
  const width=m.offsetWidth,height=m.offsetHeight;
  const left=Math.max(appRect.left+8,Math.min(r.left,appRect.right-width-8));
  const top=Math.max(appRect.top+8,Math.min(r.bottom,appRect.bottom-height-8));
  m.style.left=`${left}px`;
  m.style.top=`${top}px`;
  m.style.transformOrigin='0 0';
  m.style.visibility='visible';
  requestAnimationFrame(()=>m.classList.add('roundMenuOpen'));
  m.querySelectorAll('[data-round-item]').forEach(item=>bindNativeRoundSwipe(item,{index:Number(item.dataset.roundItem),roundCount:n,closeMenu}));
  m.querySelector('#addRoundItem')?.addEventListener('click',()=>{closeMenu({immediate:true});addRound()});
  outsideClose=ev=>{if(!m.contains(ev.target)&&ev.target!==anchor)closeMenu()};
  setTimeout(()=>{if(m.isConnected&&outsideClose)document.addEventListener('pointerdown',outsideClose,true)},0);
}
function addRound(){if((state.players[0]?.roundScores.length||1)>=4){toast('Maximal 4 Runden');return}state.players.forEach(p=>p.roundScores.push(blankRound()));if(state.players.length>1){const first=state.players.shift();state.players.push(first)}persist({autosave:true});render();haptic()}
function removeRound(idx){const n=state.players[0]?.roundScores.length||1;if(n<=1)return;state.players.forEach(p=>p.roundScores.splice(idx,1));persist({autosave:true});render();haptic()}
function addPlayer(name,color){if(state.players.length>=MAX_PLAYERS)return false;normalizeRounds(false);const rounds=state.players[0]?.roundScores.length||1;state.players.push({name:name||`Spieler ${state.players.length+1}`,color:color||PLAYER_COLORS[state.players.length%PLAYER_COLORS.length],roundScores:Array.from({length:rounds},blankRound)});persist({autosave:true});render();haptic();return true}

function visibleAppRect(){
  const app=document.querySelector('.app');
  if(app){const r=app.getBoundingClientRect();if(r.width&&r.height)return r;}
  return {left:0,top:0,width:Math.max(320,innerWidth||360),height:Math.max(560,innerHeight||800),right:innerWidth||360,bottom:innerHeight||800};
}

function composeSpringProgress(seconds){
  // Native Compose spring: DampingRatioMediumBouncy + StiffnessMediumLow.
  const dampingRatio=.5,stiffness=400,omega0=Math.sqrt(stiffness);
  const root=Math.sqrt(1-dampingRatio*dampingRatio),omegaD=omega0*root;
  return 1-Math.exp(-dampingRatio*omega0*seconds)*(
    Math.cos(omegaD*seconds)+(dampingRatio/root)*Math.sin(omegaD*seconds)
  );
}
function animateNativeScoreButton(holder,button,dx,dy,delayMs){
  const duration=620;
  let raf=0,started=0,cancelled=false;
  const tick=now=>{
    if(cancelled)return;
    if(!started)started=now;
    const elapsed=now-started-delayMs;
    if(elapsed<0){raf=requestAnimationFrame(tick);return}
    const t=Math.min(duration,elapsed),p=t>=duration?1:composeSpringProgress(t/1000);
    holder.style.transform=`translate3d(${dx*p}px,${dy*p}px,0)`;
    button.style.transform=`scale(${.8+.2*p})`;
    button.style.opacity=String(Math.max(0,Math.min(1,p)));
    if(t<duration)raf=requestAnimationFrame(tick);
    else{holder.style.transform=`translate3d(${dx}px,${dy}px,0)`;button.style.transform='scale(1)';button.style.opacity='1'}
  };
  raf=requestAnimationFrame(tick);
  return()=>{cancelled=true;cancelAnimationFrame(raf)};
}
function openWheel(pi,ri,hi,cell){
  // v53: Score-Cycle nach dem nativen Compose-Verhalten.
  // Das Menü fliegt federnd aus der Mitte; beim Auswählen verschwindet es sofort
  // und der Hintergrund wird bereits während des 450-ms-Punkteflugs wieder scharf.
  golfFeedback();
  closeMainDropdowns();
  closeScoreCycle();
  overlayOn();
  document.body.classList.add('scoreCycleOpen');
  document.querySelectorAll('.scoreCell.selected').forEach(x=>x.classList.remove('selected'));
  cell.classList.add('selected');

  const current=state.players[pi].roundScores[ri][hi];
  const ov=document.createElement('div');
  ov.className='overlay show gesture nativeScoreOverlay';
  const box=document.createElement('div');
  box.className='wheelBox nativeScoreCycle';
  ov.appendChild(box);
  document.body.appendChild(ov);

  const orect=ov.getBoundingClientRect();
  const crect=cell.getBoundingClientRect();
  const appRect=visibleAppRect();
  const baseW=Math.min(orect.width||appRect.width,appRect.width||innerWidth||360);
  const menuSize=baseW*.85;
  document.documentElement.style.setProperty('--main-wheel-size',menuSize+'px');
  box.style.width=menuSize+'px';
  box.style.height=menuSize+'px';
  box.style.left='50%';
  const targetY=(crect.top+crect.height/2)-orect.top;
  box.style.top=Math.max(0,Math.min((orect.height||appRect.height)-menuSize,targetY-menuSize/2))+'px';

  const cancelAnimations=[];
  let closed=false;
  const dismiss=()=>{
    if(closed)return;
    closed=true;
    cancelAnimations.splice(0).forEach(cancel=>cancel());
    cell.classList.remove('selected');
    ov.remove();
    overlayOff();
    setTimeout(()=>{if(!document.querySelector('.nativeScoreOverlay'))document.body.classList.remove('scoreCycleOpen')},320);
  };
  ov._nativeDismiss=dismiss;
  ov.addEventListener('pointerdown',ev=>{if(ev.target===ov)dismiss()},{passive:false});

  const nums=[null,1,2,3,4,5,6,7];
  nums.forEach((n,idx)=>{
    const holder=document.createElement('div');
    holder.className='scoreButtonHolder';
    const b=document.createElement('button');
    b.type='button';
    b.className='circleBtn '+(n==null?'center ':'')+((current===n)?'selected':'');
    b.textContent=n==null?'':n;
    b.style.fontSize=(menuSize*.108)+'px';
    holder.appendChild(b);
    box.appendChild(holder);

    let dx=0,dy=0;
    if(n!=null){
      const angle=(-90+n*360/7)*Math.PI/180;
      dx=Math.cos(angle)*menuSize*.32;
      dy=Math.sin(angle)*menuSize*.32;
    }
    cancelAnimations.push(animateNativeScoreButton(holder,b,dx,dy,idx*30));

    const choose=ev=>{
      if(closed)return;
      // Muss vor dismiss() passieren: Nach dem Entfernen des Overlays darf der
      // synthetische Android-Click nicht auf die Zelle darunter durchfallen.
      swallowCurrentActivation(ev);
      const br=b.getBoundingClientRect(),cr=cell.getBoundingClientRect();
      const start={x:br.left+br.width/2-20,y:br.top+br.height/2-20};
      const end={x:cr.left+cr.width/2-20,y:cr.top+cr.height/2-20};

      // Native App: Auswahl-Overlay sofort schließen. Dadurch laufen Blur und Scrim
      // schon während des Fluges in 300 ms aus, statt bis nach dem Flug stehenzubleiben.
      dismiss();
      haptic();
      flyScore(n,start,end,()=>{
        state.players[pi].roundScores[ri][hi]=n;
        persist({autosave:true});
        render();
      });
    };
    b.style.touchAction='none';
    b.addEventListener('pointerdown',ev=>{
      if(ev.cancelable)ev.preventDefault();
      ev.stopPropagation();
    },{passive:false});
    b.addEventListener('pointerup',choose,{passive:false});
    b.addEventListener('click',ev=>{
      if(ev.cancelable)ev.preventDefault();
      ev.stopImmediatePropagation();
      ev.stopPropagation();
    },{passive:false});
  });
}
function flyScore(score,start,end,done){
  const f=document.createElement('div');
  f.className='flyingScore nativeFlyingScore';
  f.textContent=score??'';
  f.style.left=start.x+'px';
  f.style.top=start.y+'px';
  f.style.setProperty('--tx',end.x+'px');
  f.style.setProperty('--ty',end.y+'px');
  document.body.appendChild(f);
  f.addEventListener('animationend',()=>{f.remove();done&&done()},{once:true});
}

let trustedAndroidMajor = null;
(function detectRealAndroidVersion(){
  const uaData=navigator.userAgentData;
  if(!uaData || typeof uaData.getHighEntropyValues!=="function")return;
  uaData.getHighEntropyValues(["platform","platformVersion"]).then(info=>{
    if(String(info.platform||"").toLowerCase()!=="android")return;
    const major=parseInt(String(info.platformVersion||"").split(".")[0],10);
    if(Number.isFinite(major))trustedAndroidMajor=major;
  }).catch(()=>{});
})();

function hueToColor(h){h=((h%360)+360)%360;const s=.8,v=.6,c=v*s,x=c*(1-Math.abs((h/60)%2-1)),m=v-c;let r=0,g=0,b=0;if(h<60){r=c;g=x}else if(h<120){r=x;g=c}else if(h<180){g=c;b=x}else if(h<240){g=x;b=c}else if(h<300){r=x;b=c}else{r=c;b=x}const hex=n=>Math.round((n+m)*255).toString(16).padStart(2,'0');return `#${hex(r)}${hex(g)}${hex(b)}`}
function roughHue(color){if(!color||!color.startsWith('#'))return 230;let r=parseInt(color.slice(1,3),16)/255,g=parseInt(color.slice(3,5),16)/255,b=parseInt(color.slice(5,7),16)/255,max=Math.max(r,g,b),min=Math.min(r,g,b),h=0,d=max-min;if(d===0)h=0;else if(max===r)h=60*(((g-b)/d)%6);else if(max===g)h=60*((b-r)/d+2);else h=60*((r-g)/d+4);return (h+360)%360}
function showPlayerDialog(existingIndex){
  const editing=existingIndex!=null;
  if(!editing&&state.players.length>=MAX_PLAYERS)return;
  const p=editing?state.players[existingIndex]:null;
  let hue=editing?roughHue(p.color):Math.random()*360;
  let color=hueToColor(hue);
  const layer=document.createElement('div');
  // Do not parse the normal UA here: modern Chrome deliberately freezes it
  // to Android 10 even on Android 12+, which previously forced the opaque
  // legacy dialog seen in the user's screenshot. Default to the native glass
  // surface and use legacy white only when UA-CH provides a trustworthy API.
  const useLegacyPlayerSurface=trustedAndroidMajor!=null&&trustedAndroidMajor<12;
  layer.className=`dialogLayer playerDialogLayer show${useLegacyPlayerSurface?' legacyPlayerDialogLayer':''}`;
  overlayOn();layer.onclick=()=>{layer.remove();overlayOff()};
  layer.innerHTML=`<div class="glassDialog playerDialog ${editing?'editPlayerDialog':'addPlayerDialog'}" onclick="event.stopPropagation()"><h2>${editing?'Spieler bearbeiten':'Spieler hinzufügen'}</h2><div class="playerDialogBody"><input class="nameInput" id="playerName" placeholder="Name" value="${esc(p?.name||'')}" style="--dialog-color:${color}" autocapitalize="words" autocomplete="off" enterkeyhint="done"><div class="hueBar gesture" id="hueBar"><div class="hueKnob" id="hueKnob"></div></div></div><div class="dialogActions"><button class="pillButton" id="savePlayer">${editing?icon('save')+' Speichern':icon('add')+' Hinzufügen'}</button>${editing&&state.players.length>1?`<button class="pillButton red" id="deletePlayer">${icon('delete')} Entfernen</button>`:''}</div></div>`;
  document.body.appendChild(layer);
  const bar=layer.querySelector('#hueBar'),knob=layer.querySelector('#hueKnob'),input=layer.querySelector('#playerName');
  function placeKnob(h){hue=Math.max(0,Math.min(360,Number(h)||0));knob.style.left=`calc(4px + (100% - 8px) * ${hue/360})`}
  function applyColor(c){color=c;input.style.setProperty('--dialog-color',color)}
  // Native Compose-Logik: Farbton übernehmen, Sättigung und Helligkeit auf HSV 0,8 / 0,6 setzen.
  placeKnob(hue);applyColor(color);
  function updateFromHue(h){placeKnob(h);applyColor(hueToColor(hue))}
  function setFromEvent(ev){if(ev.cancelable)ev.preventDefault();const r=bar.getBoundingClientRect();updateFromHue(Math.max(0,Math.min(1,(ev.clientX-r.left)/r.width))*360)}
  bar.addEventListener('pointerdown',ev=>{setFromEvent(ev);bar.setPointerCapture(ev.pointerId);const stop=()=>{bar.onpointermove=null;bar.onpointerup=null;bar.onpointercancel=null};bar.onpointermove=setFromEvent;bar.onpointerup=stop;bar.onpointercancel=stop},{passive:false});
  layer.querySelector('#savePlayer').onclick=()=>{const name=editing?input.value:(input.value.trim()||`Spieler ${state.players.length+1}`);if(editing){state.players[existingIndex].name=name;state.players[existingIndex].color=color;persist({autosave:true});render()}else addPlayer(name,color);layer.remove();overlayOff()};
  layer.querySelector('#deletePlayer')?.addEventListener('click',()=>{state.players.splice(existingIndex,1);persist({autosave:true});layer.remove();overlayOff();render()});
}
function editPlayer(i){showPlayerDialog(i)}

function showDrawer(){
  haptic();overlayOn();
  const d=document.createElement('div');
  d.className='drawerOverlay show gesture';
  d.onclick=()=>closeDrawer(d);
  const activeCount=activeGames.length+(hasAnyScore()&&!activeGames.some(g=>g.id===state.gameId)?1:0);
  const rounds=state.players[0]?.roundScores.length||1;
  const completed=isCurrentGameComplete();
  const tournamentItem=state.settings.tournamentEnabled?`<div class="drawerItem drawerGold" id="drawerTournament"><span class="drawerIcon">${icon('military_tech')}</span><span>Turnier-Modus</span></div>`:'';
  const nextRoundItem=rounds<4?`<div class="drawerItem" id="drawerRound"><span class="drawerIcon">${icon('add_circle_outline')}</span><span>Nächste Runde</span></div>`:'';
  const resultItem=completed?`<div class="drawerItem" id="drawerResults"><span class="drawerIcon">${icon('emoji_events')}</span><span>Ergebniskarte</span></div>`:'';
  d.innerHTML=`<div class="drawerPanel" onclick="event.stopPropagation()"><div class="drawerHead"><img src="assets/minigolf_logo.png"><div class="drawerTitle">MiniGolf<br>Punktekarte</div></div><div class="drawerTopItems"><div class="drawerItem" id="drawerAdd"><span class="drawerIcon">${icon('add')}</span><span>Spieler hinzufügen</span></div>${nextRoundItem}<div class="drawerItem" id="drawerNew"><span class="drawerIcon">${icon('add_circle')}</span><span>Neues Spiel</span></div><div class="drawerItem" id="drawerEnd"><span class="drawerIcon">${icon('stop')}</span><span>Spiel beenden</span></div></div><div class="drawerSpacer"></div><div class="drawerBottomItems">${tournamentItem}${resultItem}<div class="drawerItem" id="drawerActive"><span class="drawerIcon">${icon('play_circle_outline')}</span><span>Aktive Spiele</span><span class="badge ${activeCount?'':'hidden'}">${activeCount}</span></div><div class="drawerItem" id="drawerHistory"><span class="drawerIcon">${icon('history')}</span><span>Beendete Spiele</span></div></div><div class="drawerDivider"></div><div class="drawerFooter"><button class="gear" id="drawerSettings">${icon('settings')}</button><div class="drawerFoot" id="drawerFoot">© MiniGolf Punktekarte<br>Created by Patrick</div></div></div>`;
  document.body.appendChild(d);
  d.querySelector('#drawerAdd').onclick=()=>{if(state.players.length>=MAX_PLAYERS)return;d.remove();overlayOff();showPlayerDialog(null)};
  d.querySelector('#drawerRound')?.addEventListener('click',()=>{addRound();closeDrawer(d)});
  d.querySelector('#drawerNew').onclick=()=>{autosaveActive(true);resetKeepingPlayers();closeDrawer(d);toast('Neues Spiel')};
  d.querySelector('#drawerEnd').onclick=()=>{confirmDialog('Spiel beenden','Aktuelles Spiel beenden und Tabelle komplett leeren?',()=>{const saved=saveCurrentToHistory('Spiel beendet');resetToStandard();closeDrawer(d);toast(saved?'Spiel beendet':'Tabelle geleert')})};
  d.querySelector('#drawerResults')?.addEventListener('click',()=>{d.remove();overlayOff();showWinnerCard()});
  d.querySelector('#drawerActive').onclick=()=>{d.remove();overlayOff();showActivePage()};
  d.querySelector('#drawerHistory').onclick=()=>{d.remove();overlayOff();showHistoryPage()};
  d.querySelector('#drawerSettings').onclick=ev=>{ev.stopPropagation();d.remove();overlayOff();setTimeout(showSettingsDialog,60)};
  d.querySelector('#drawerTournament')?.addEventListener('click',()=>{d.remove();overlayOff();showTournamentHome()});
  d.querySelector('#drawerFoot').onclick=ev=>{
    if(state.settings.tournamentEnabled)return;
    ev.stopPropagation();
    const now=Date.now();
    devClickCount=(now-devLastClick>1100)?1:devClickCount+1;
    devLastClick=now;
    if(devClickCount>=7){
      state.settings.tournamentEnabled=true;
      persist();
      devClickCount=0;
      toast('Turnier-Modus aktiviert!');

      // Native Compose behavior: the drawer stays open. Recomposition only
      // adds the new tournament entry to the existing drawer instead of
      // closing and reopening the whole panel (which caused a visible flash).
      if(!d.querySelector('#drawerTournament')){
        const item=document.createElement('div');
        item.className='drawerItem drawerGold';
        item.id='drawerTournament';
        item.innerHTML=`<span class="drawerIcon">${icon('military_tech')}</span><span>Turnier-Modus</span>`;
        d.querySelector('.drawerBottomItems')?.prepend(item);
        item.addEventListener('click',()=>{d.remove();overlayOff();showTournamentHome()});
      }
    }else if(devClickCount>=3){
      toast(`Noch ${7-devClickCount} Klicks bis Turnier-Modus`);
    }
  };
}
function closeDrawer(d){d.remove();overlayOff()}
function roundTotalsText(p){return (p.roundScores||[]).map((_,i)=>roundTotal(p,i)).join(' | ')}
function isFullGame(g){return !!(g&&Array.isArray(g.players)&&g.players.length&&g.players.every(p=>Array.isArray(p.roundScores)&&p.roundScores.length&&p.roundScores.every(r=>Array.isArray(r)&&r.length===18&&r.every(v=>v!=null&&v>0))))}
function winnersText(g){const players=g.players||[];if(!players.length)return '';const totals=players.map(p=>({name:p.name,total:(p.roundScores||[]).flat().reduce((a,b)=>a+(b||0),0)})),min=Math.min(...totals.map(t=>t.total));return totals.filter(t=>t.total===min).map(t=>t.name).join(', ')}
function gameTotal(g,p){return (p.roundScores||[]).flat().reduce((a,b)=>a+(b||0),0)}
function previewGrid(g){const players=g.players||[],cols=Math.max(1,players.reduce((a,p)=>a+(p.roundScores?.length||1),0));let cells='';for(let r=0;r<9;r++)for(let c=0;c<cols;c++)cells+=`<span class="pvCell ${r%2?'b':''}"></span>`;return `<div class="scorePreview"><div class="previewLabel">Punktekarte</div><div class="previewSide">${[5,6,7,8,9,10,11,12,13].map(n=>`<b>${n}</b>`).join('')}</div><div class="previewGrid" style="grid-template-columns:repeat(${cols},1fr)">${cells}</div><div class="previewSide right">${[5,6,7,8,9,10,11,12,13].map(n=>`<b>${n}</b>`).join('')}</div><div class="previewZoom">${icon('search')}</div></div>`}
function compactHistoryCard(g,i){return `<div class="historyCard compact gesture" data-history-card="${i}"><div class="histLeftIcon">${icon(isFullGame(g)?'check_circle':'block')}</div><div class="histMain"><div class="histTitle">${esc(String(g.system||SYSTEMS[0]).replace('\n',' '))}</div><div class="histDate">${icon('calendar_month')} ${esc(formatGameDate(g.date))}</div></div><div class="histWinners">${icon('emoji_events')} ${esc(winnersText(g))}</div></div>`}
function expandedHistoryCard(g,i){const players=(g.players||[]).map(p=>`<div class="histPlayerRow" style="--pc:${p.color||'#b02062'}"><div><div class="histPlayerName">${icon('emoji_events')} ${esc(p.name)}</div><div class="histRounds">Runden: ${esc((p.roundScores||[]).map(r=>r.reduce((a,b)=>a+(b||0),0)).join(' | '))}</div></div><div class="histPoints">${gameTotal(g,p)} Pkt.</div></div>`).join('');return `<div class="historyCard expanded gesture" data-history-card="${i}"><div class="histHeaderLine"><div class="histLeftIcon">${icon(isFullGame(g)?'check_circle':'block')}</div><div><div class="histTitle">${esc(String(g.system||SYSTEMS[0]).replace('\n',' '))}</div><div class="histDate">${icon('calendar_month')} ${esc(formatGameDate(g.date))}</div></div></div><div class="histDivider"></div>${players}<div class="histDivider"></div>${previewGrid(g)}</div>`}
function showHistoryPage(){let expanded=-1;const page=document.createElement('div');page.className='pageScreen historyPageScreen';function draw(){const rows=endedGames.length?endedGames.map((g,i)=>i===expanded?expandedHistoryCard(g,i):compactHistoryCard(g,i)).join(''):`<div class="historyEmpty">Noch keine beendeten Spiele.</div>`;page.innerHTML=`<div class="historyTop"><button class="pageIcon" id="historyBack">${icon('arrow_back')}</button><div class="pageTitle">Beendete Spiele</div><button class="pageIcon search" id="historySearch">${icon('search')}</button></div><div class="historyListPage"><div class="swipeHint">Karte: rechts ziehen = Teilen · links ziehen = Löschen</div>${rows}</div>`;page.querySelector('#historyBack').onclick=()=>page.remove();page.querySelector('#historySearch').onclick=()=>toast('Suche später');page.querySelectorAll('[data-history-card]').forEach(card=>{const i=Number(card.dataset.historyCard);makeSwipeable(card,{threshold:76,onSwipe:(dir)=>{if(dir>0)shareGame(endedGames[i]);else confirmDialog('Spiel löschen','Dieses beendete Spiel löschen?',()=>{endedGames.splice(i,1);persistHistory();draw()})},onTap:()=>{expanded=expanded===i?-1:i;draw()},maxTranslate:74})})}draw();document.body.appendChild(page)}
function activeCard(g,i){return `<div class="activeCard gesture" data-active-card="${i}"><div class="histLeftIcon">${icon('play_arrow')}</div><div class="histMain"><div class="histTitle">${esc(String(g.system||SYSTEMS[0]).replace('\n',' '))}</div><div class="histDate">${icon('place')} ${esc(g.location||'ohne Ort')} · ${icon('calendar_month')} ${esc(formatGameDate(g.date))}</div></div><div class="histWinners">${esc((g.players||[]).length)} Spieler</div></div>`}
function showActivePage(){if(hasAnyScore())autosaveActive();const page=document.createElement('div');page.className='pageScreen';function draw(){const rows=activeGames.length?activeGames.map(activeCard).join(''):`<div class="historyEmpty">Keine aktiven Spiele.</div>`;page.innerHTML=`<div class="activeTop"><button class="pageIcon" id="activeBack">${icon('arrow_back')}</button><div class="pageTitle">Aktive Spiele</div></div><div class="activeListPage"><div class="swipeHint">Karte: rechts ziehen = Fortsetzen · links ziehen = Beenden</div>${rows}</div>`;page.querySelector('#activeBack').onclick=()=>page.remove();page.querySelectorAll('[data-active-card]').forEach(card=>{const i=Number(card.dataset.activeCard);makeSwipeable(card,{threshold:76,onSwipe:(dir)=>{if(dir>0){loadGame(activeGames[i]);page.remove()}else confirmDialog('Spiel beenden','Aktives Spiel in beendete Spiele verschieben?',()=>{finishActiveGame(activeGames[i]);draw()})},onTap:()=>{loadGame(activeGames[i]);page.remove()},maxTranslate:74})})}draw();document.body.appendChild(page)}
function shareText(g){const players=(g.players||[]).map(p=>`${p.name}: ${gameTotal(g,p)} (${(p.roundScores||[]).map(r=>r.reduce((a,b)=>a+(b||0),0)).join(' | ')})`).join('\n');return `MiniGolf Punktekarte\n${String(g.system||'').replace('\n',' ')}\n${g.location||'ohne Ort'}\n${formatGameDate(g.date)}\n\n${players}`}
async function shareGame(g){const text=shareText(g);try{if(navigator.share)await navigator.share({title:'MiniGolf Punktekarte',text});else if(navigator.clipboard){await navigator.clipboard.writeText(text);toast('In Zwischenablage kopiert')}else downloadText(text,'minigolf_ergebnis.txt')}catch(_){toast('Teilen abgebrochen')}}
function downloadText(text,name){const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text],{type:'text/plain'}));a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),500)}


function bindPlayerHeaderDrag(){
  // v21: nativeres Spalten-Drag ohne Tabellen-Verrutschen, mit nur einer sichtbaren Ghost-Spalte.
  // Die echte Tabellen-Spalte bleibt an ihrem Platz unsichtbar, eine feste Ghost-Spalte
  // wird über der Tabelle bewegt. Dadurch entstehen keine Zeilenlücken und die Tabelle
  // bleibt innerhalb des Viewports geclippt. Der Plus-Bereich rechts bleibt unverändert.
  document.querySelectorAll('[data-player-head]').forEach(el=>{
    let sx=0,sy=0,pid=null,timer=null,dragging=false;
    let activeOriginal=0,visualOrder=[],logicalOffsetX=0,visualOffsetX=0,colWidth=0,moved=false;
    let lastClientX=0,pointerX=0,autoScrollRaf=0,ghost=null;
    const vp=()=>document.getElementById('scoreViewport');
    const clearTimer=()=>{if(timer){clearTimeout(timer);timer=null}};
    const playerEls=idx=>Array.from(document.querySelectorAll(`[data-player-col="${idx}"]`));
    const allColumnEls=()=>Array.from(document.querySelectorAll('[data-player-col]'));
    const applyVisualOrder=()=>{
      visualOrder.forEach((origIdx,orderIdx)=>{
        playerEls(origIdx).forEach(node=>{node.style.order=String(orderIdx);});
      });
      document.querySelectorAll('.rightSide').forEach(node=>node.style.order='999');
      document.querySelectorAll('.leftCell').forEach(node=>node.style.order='-999');
    };
    const clearDragStyles=()=>{
      allColumnEls().forEach(node=>{
        node.style.order='';
        node.style.transform='';
        node.style.zIndex='';
        node.style.opacity='';
        node.style.visibility='';
        node.style.transition='';
        node.style.pointerEvents='';
        node.classList.remove('draggingColumn','dragHiddenColumn','dragDropTarget');
      });
      document.querySelectorAll('.rightSide,.leftCell').forEach(node=>node.style.order='');
      if(ghost){ghost.remove();ghost=null;}
    };
    const draggedNodes=()=>playerEls(activeOriginal);
    const hideDraggedSource=()=>{
      draggedNodes().forEach(node=>{
        node.style.visibility='visible';
        node.style.pointerEvents='none';
        node.classList.add('dragHiddenColumn');
      });
    };
    const createGhost=()=>{
      if(ghost)ghost.remove();
      const nodes=draggedNodes();
      const rects=nodes.map(node=>({node,rect:node.getBoundingClientRect()})).filter(x=>x.rect.width&&x.rect.height);
      if(!rects.length)return;
      const left=Math.min(...rects.map(x=>x.rect.left));
      const top=Math.min(...rects.map(x=>x.rect.top));
      const right=Math.max(...rects.map(x=>x.rect.right));
      const bottom=Math.max(...rects.map(x=>x.rect.bottom));
      ghost=document.createElement('div');
      ghost.className='playerColumnGhost';
      ghost.style.left=left+'px';
      ghost.style.top=top+'px';
      ghost.style.width=(right-left)+'px';
      ghost.style.height=(bottom-top)+'px';
      ghost.dataset.baseLeft=String(left);
      ghost.dataset.width=String(right-left);
      ghost.dataset.baseTop=String(top);
      rects.forEach(({node,rect})=>{
        const clone=node.cloneNode(true);
        clone.removeAttribute('id');
        // Wichtig: Ghost-Klone dürfen NICHT mehr als echte Spieler-Spalten gefunden werden.
        // Sonst werden sie beim Verstecken der Originalspalte ebenfalls unsichtbar.
        clone.removeAttribute('data-player-col');
        clone.removeAttribute('data-player-head');
        clone.querySelectorAll('[data-player-col],[data-player-head]').forEach(n=>{
          n.removeAttribute('data-player-col');
          n.removeAttribute('data-player-head');
        });
        clone.classList.add('ghostPart');
        clone.classList.remove('dragHiddenColumn','draggingColumn','dragDropTarget');
        clone.style.position='absolute';
        clone.style.left=(rect.left-left)+'px';
        clone.style.top=(rect.top-top)+'px';
        clone.style.width=rect.width+'px';
        clone.style.height=rect.height+'px';
        clone.style.margin='0';
        clone.style.transform='none';
        clone.style.order='';
        clone.style.visibility='visible';
        clone.style.pointerEvents='none';
        ghost.appendChild(clone);
      });
      document.body.appendChild(ghost);
    };
    const setGhostTransform=()=>{
      if(!ghost)return;
      const view=vp();
      const vrect=view?view.getBoundingClientRect():{left:0,right:innerWidth};
      const baseLeft=Number(ghost.dataset.baseLeft||0);
      const width=Number(ghost.dataset.width||0);
      let visual=visualOffsetX;
      // Ghost folgt dem Finger/Mauszeiger glatt; die Tauschlogik bleibt davon getrennt.
      const minX=vrect.left-baseLeft;
      const maxX=vrect.right-baseLeft-width;
      if(Number.isFinite(minX)&&Number.isFinite(maxX)) visual=Math.max(minX,Math.min(maxX,visual));
      ghost.style.transform=`translate3d(${visual}px,0,0)`;
    };
    const markDropTarget=()=>{
      document.querySelectorAll('.dragDropTarget').forEach(n=>n.classList.remove('dragDropTarget'));
      const currentOrder=visualOrder.indexOf(activeOriginal);
      visualOrder.forEach((origIdx,orderIdx)=>{
        if(orderIdx===currentOrder||origIdx===activeOriginal)return;
        if(Math.abs(orderIdx-currentOrder)===1){
          // nur unmittelbare Nachbarn dezent markieren, wie ein Zielbereich
          playerEls(origIdx).forEach(n=>n.classList.add('dragDropTarget'));
        }
      });
    };
    const animateReorder=(before)=>{
      const activeSet=new Set(draggedNodes());
      requestAnimationFrame(()=>{
        allColumnEls().forEach(node=>{
          if(activeSet.has(node))return;
          const b=before.get(node);if(b==null)return;
          const a=node.getBoundingClientRect().left;
          const dx=b-a;
          if(Math.abs(dx)<0.5)return;
          node.style.transition='none';
          node.style.transform=`translate3d(${dx}px,0,0)`;
          node.getBoundingClientRect();
          requestAnimationFrame(()=>{
            node.style.transition='transform .16s ease';
            node.style.transform='';
            setTimeout(()=>{node.style.transition=''},190);
          });
        });
      });
    };
    const orderSwap=(a,b)=>{
      const before=new Map(allColumnEls().map(node=>[node,node.getBoundingClientRect().left]));
      [visualOrder[a],visualOrder[b]]=[visualOrder[b],visualOrder[a]];
      applyVisualOrder();
      animateReorder(before);
      moved=true;
      markDropTarget();
      haptic();
    };
    const swapAdjacentIfNeeded=()=>{
      if(!dragging||!colWidth)return;
      let currentOrder=visualOrder.indexOf(activeOriginal);
      while(logicalOffsetX>colWidth*0.78 && currentOrder<visualOrder.length-1){
        orderSwap(currentOrder,currentOrder+1);
        currentOrder++;
        logicalOffsetX-=colWidth;
      }
      while(logicalOffsetX<-colWidth*0.78 && currentOrder>0){
        orderSwap(currentOrder,currentOrder-1);
        currentOrder--;
        logicalOffsetX+=colWidth;
      }
    };
    const autoScrollTick=()=>{
      if(!dragging)return;
      const view=vp();
      if(view){
        const r=view.getBoundingClientRect();
        const edge=48;
        let delta=0;
        if(pointerX<r.left+edge && view.scrollLeft>0){
          const ratio=Math.min(1,(r.left+edge-pointerX)/edge);
          delta=-Math.max(1,Math.round(ratio*10));
        }else if(pointerX>r.right-edge && view.scrollLeft<view.scrollWidth-view.clientWidth-1){
          const ratio=Math.min(1,(pointerX-(r.right-edge))/edge);
          delta=Math.max(1,Math.round(ratio*10));
        }
        if(delta){
          const before=view.scrollLeft;
          view.scrollLeft=Math.max(0,Math.min(view.scrollWidth-view.clientWidth,view.scrollLeft+delta));
          const actual=view.scrollLeft-before;
          if(actual){
            logicalOffsetX+=actual;
            visualOffsetX+=actual;
            swapAdjacentIfNeeded();
          }
        }
      }
      setGhostTransform();
      autoScrollRaf=requestAnimationFrame(autoScrollTick);
    };
    const beginDrag=()=>{
      if(dragging||state.players.length<2)return;
      dragging=true;moved=false;clearTimer();suppressNextClickUntil=Date.now()+1800;
      activeOriginal=Number(el.dataset.playerHead);
      visualOrder=state.players.map((_,i)=>i);
      const r=el.getBoundingClientRect();
      const cssGap=parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--gap'))||2;
      colWidth=r.width+cssGap;
      logicalOffsetX=0;
      visualOffsetX=0;
      lastClientX=sx;
      document.body.classList.add('playerDragActive');
      applyVisualOrder();
      createGhost();
      hideDraggedSource();
      setGhostTransform();
      markDropTarget();
      haptic();
      cancelAnimationFrame(autoScrollRaf);
      autoScrollRaf=requestAnimationFrame(autoScrollTick);
    };
    const onMove=e=>{
      if(pid!==e.pointerId)return;
      const dx=e.clientX-sx,dy=e.clientY-sy;
      pointerX=e.clientX;
      if(!dragging){
        if(Math.hypot(dx,dy)>14)clearTimer();
        return;
      }
      if(e.cancelable)e.preventDefault();
      const step=e.clientX-lastClientX;
      lastClientX=e.clientX;
      logicalOffsetX+=step;
      visualOffsetX+=step;
      swapAdjacentIfNeeded();
      setGhostTransform();
      suppressNextClickUntil=Date.now()+900;
    };
    const finish=e=>{
      if(pid!==null&&e?.pointerId!==pid)return;
      clearTimer();
      cancelAnimationFrame(autoScrollRaf);autoScrollRaf=0;
      document.removeEventListener('pointermove',onMove,true);
      document.removeEventListener('pointerup',finish,true);
      document.removeEventListener('pointercancel',finish,true);
      const wasDragging=dragging;
      if(wasDragging){
        if(e?.cancelable)e.preventDefault();
        const oldPlayers=state.players.slice();
        suppressNextClickUntil=Date.now()+1100;
        if(moved){
          state.players=visualOrder.map(i=>oldPlayers[i]);
          persist({autosave:true});
          document.body.classList.remove('playerDragActive');
          clearDragStyles();
          render();
          haptic();
        }else{
          document.body.classList.remove('playerDragActive');
          clearDragStyles();
          toast('Spieler nicht verschoben');
        }
      }else{
        document.body.classList.remove('playerDragActive');
        clearDragStyles();
      }
      pid=null;dragging=false;visualOrder=[];logicalOffsetX=0;visualOffsetX=0;colWidth=0;moved=false;lastClientX=0;pointerX=0;
    };
    el.addEventListener('pointerdown',e=>{
      if(e.pointerType==='mouse'&&e.button!==0)return;
      if(e.target.closest('button,input,textarea,select'))return;
      if(state.players.length<2)return;
      if(e.cancelable)e.preventDefault();
      sx=e.clientX;sy=e.clientY;pointerX=e.clientX;lastClientX=e.clientX;pid=e.pointerId;
      dragging=false;moved=false;activeOriginal=Number(el.dataset.playerHead);visualOrder=[];logicalOffsetX=0;
      try{el.setPointerCapture(e.pointerId)}catch(_){ }
      clearTimer();
      timer=setTimeout(beginDrag,520);
      document.addEventListener('pointermove',onMove,true);
      document.addEventListener('pointerup',finish,true);
      document.addEventListener('pointercancel',finish,true);
    },{passive:false});
    el.addEventListener('contextmenu',e=>e.preventDefault());
    el.addEventListener('dragstart',e=>e.preventDefault());
  });
}
function movePlayer(fromIndex,toIndex){if(fromIndex===toIndex||fromIndex<0||toIndex<0||fromIndex>=state.players.length||toIndex>=state.players.length)return;const list=state.players;const p=list.splice(fromIndex,1)[0];list.splice(toIndex,0,p);persist({autosave:true});render();toast('Spieler verschoben')}

function showTournamentHome(){ensureState();state.settings.tournamentEnabled=true;persist();let page=document.createElement('div');page.className='pageScreen tournamentPage';applyTournamentTheme(page);applyTournamentNativeMetrics(page);function draw(){applyTournamentTheme(page);applyTournamentNativeMetrics(page);const themeLabel=state.settings.tournamentTheme||'Hell';page.innerHTML=`<div class="tournamentTop"><button class="pageIcon" id="tourBack">${icon('arrow_back')}</button><div class="pageTitle">Turnier-Modus</div><button class="powerBtn" id="tourPower">${icon('power_settings_new')}</button></div><div class="tournamentBody"><button class="tournamentCard" id="tourNew"><span class="tourIcon green">${icon('add')}</span><span class="tourCardText"><b>Notiz erstellen</b><small>Erstelle eine neue Strategie</small></span></button><button class="tournamentCard" id="tourSaved"><span class="tourIcon blue">${icon('description')}</span><span class="tourCardText"><b>Gespeicherte Notizen</b><small>Deine Strategien ansehen</small></span></button></div><div class="tourBottom"><button class="tourSmall" id="tourTheme"><span class="purple">${tournamentThemeIcon()}</span><b>${esc(themeLabel)}</b></button><button class="tourSmall" id="tourBackup"><span class="orange">${icon('file_upload')}</span><b>Backup</b></button><button class="tourSmall" id="tourImport"><span class="cyan">${icon('file_download')}</span><b>Import</b></button><button class="tourSmall" id="tourStats"><span class="${state.settings.stats?'greenText':'redText'}">${icon('bar_chart')}</span><b>Stats</b></button></div>`;page.querySelector('#tourBack').onclick=()=>page.remove();page.querySelector('#tourNew').onclick=()=>showTournamentEditor(null,page);page.querySelector('#tourSaved').onclick=()=>showTournamentHistory(page);page.querySelector('#tourPower').onclick=()=>confirmDialog('Turnier-Modus deaktivieren?','Möchtest du den Turnier-Modus wirklich deaktivieren? Du kannst ihn jederzeit über das Menü wieder aktivieren.',()=>{state.settings.tournamentEnabled=false;state.settings.stats=false;state.saveWithStats=false;persist({autosave:hasAnyScore()});page.remove();render();toast('Turnier-Modus deaktiviert')},'Deaktivieren');page.querySelector('#tourTheme').onclick=()=>{const order=['Hell','Dunkel','System'];state.settings.tournamentTheme=order[(order.indexOf(state.settings.tournamentTheme)+1)%order.length];persist();refreshOpenTournamentThemes();draw()};page.querySelector('#tourBackup').onclick=()=>exportTournamentNotes();page.querySelector('#tourImport').onclick=()=>importTournamentNotes();page.querySelector('#tourStats').onclick=()=>{state.settings.stats=!state.settings.stats;if(!state.settings.stats)state.saveWithStats=false;persist({autosave:hasAnyScore()});toast(state.settings.stats?'Statistik-Modus aktiviert!':'Statistik-Modus deaktiviert!');draw();render()}}draw();document.body.appendChild(page);applyTournamentNativeMetrics(page)}
function emptyTournamentNote(){return{id:uid(),date:new Date().toISOString(),location:'',system:state.system,holes:Array.from({length:18},()=>({ball:'',start:'',notes:'',images:[]}))}}
function showTournamentEditor(existing,parentPage){
  const editing=!!existing;
  let note=existing?JSON.parse(JSON.stringify(existing)):emptyTournamentNote();
  const page=document.createElement('div');
  page.className='pageScreen tournamentEditor nativeTournamentEditor';
  applyTournamentTheme(page);
  applyTournamentNativeMetrics(page);
  const holesHtml=()=>note.holes.map((h,i)=>`<div class="tourHole"><b>${i+1}</b><div class="tourHoleContent"><div class="tourLine"><div class="tourInputs"><input data-hole="${i}" data-field="ball" value="${esc(h.ball)}" placeholder="Ball"><input data-hole="${i}" data-field="start" value="${esc(h.start)}" placeholder="Abschlag"></div><button class="cameraBtn" data-camera="${i}">${icon('add_a_photo')}</button></div><input class="tourNotesInput" data-hole="${i}" data-field="notes" value="${esc(h.notes)}" placeholder="Notizen..."></div></div>`).join('');
  function draw(){
    const locationClass=note.location?'hasValue':'';
    page.innerHTML=`<div class="editorTop"><button class="pageIcon" id="editorBack">${icon('arrow_back')}</button><div class="pageTitle">${editing?'Notiz bearbeiten':'Notiz erstellen'}</div><button class="saveIcon" id="saveNote">${icon('save')}</button></div><div class="editorBody"><div class="editorMeta"><label class="fieldLabel locationField ${locationClass}"><span class="outlineLabel">Ort</span><input id="noteLocation" value="${esc(note.location)}" autocomplete="off"></label><label class="fieldLabel selectLabel hasValue"><span class="outlineLabel">Anlagentyp</span><select id="noteSystem">${SYSTEMS.map(s=>`<option ${s===note.system?'selected':''}>${esc(s.replace('\n',' '))}</option>`).join('')}</select></label></div><div class="holesScroller"><div class="holesList">${holesHtml()}</div></div></div>`;
    page.querySelector('#editorBack').onclick=()=>page.remove();
    page.querySelector('#saveNote').onclick=save;
    const locationInput=page.querySelector('#noteLocation');
    const locationField=page.querySelector('.locationField');
    const syncLocationClass=()=>locationField.classList.toggle('hasValue',!!locationInput.value.trim());
    locationInput.oninput=e=>{note.location=e.target.value;syncLocationClass()};
    locationInput.onblur=syncLocationClass;
    page.querySelector('#noteSystem').onchange=e=>note.system=SYSTEMS.find(s=>s.replace('\n',' ')===e.target.value)||e.target.value;
    page.querySelectorAll('[data-hole]').forEach(inp=>inp.oninput=()=>{note.holes[+inp.dataset.hole][inp.dataset.field]=inp.value});
    page.querySelectorAll('[data-camera]').forEach(b=>b.onclick=()=>toast('Bildfunktion folgt'));
    applyTournamentNativeMetrics(page);
  }
  function save(){
    note.date=new Date().toISOString();
    if(editing){const idx=tournamentNotes.findIndex(n=>n.id===note.id);if(idx>=0)tournamentNotes[idx]=note;else tournamentNotes.unshift(note)}else tournamentNotes.unshift(note);
    persistTournament();toast('Notizen gespeichert');page.remove();if(parentPage){parentPage.remove();showTournamentHome()}
  }
  draw();
  document.body.appendChild(page);
  applyTournamentNativeMetrics(page);
}
function tournamentCard(n,i){
  const sys=String(n.system||SYSTEMS[0]).replace('\n',' '), loc=n.location||'Unbekannter Ort';
  const cloudStatus=window.CloudShare?.cardStatusHtml?.(n,i)||'';
  const cloudClass=cloudStatus?' hasCloudShare':'';
  return `<div class="tourSwipeRow"><div class="tourSwipeBg"><div class="editBg">${icon('edit')}</div><div class="deleteBg">${icon('delete')}</div></div><div class="tourNoteCard gesture${cloudClass}" data-tour-note="${i}"><div class="tourNoteMain"><b>${icon('place')} ${esc(loc)}</b><small>${esc(sys)}</small><small class="noteMetaLine">${icon('calendar_month')} ${esc(formatGameDate(n.date))}</small></div><button type="button" class="tourNoteMenuButton" data-tour-menu="${i}" aria-label="Aktionen für ${esc(loc)}" title="Weitere Aktionen">${icon('more_vert')}</button>${cloudStatus}</div></div>`
}
function closeTournamentCardMenu(page){
  page.querySelector('.tourCardMenuLayer')?.remove();
}
function showTournamentCardMenu(page,button,note){
  closeTournamentCardMenu(page);
  const layer=document.createElement('div');
  layer.className='tourCardMenuLayer';
  const cloudActive=window.CloudShare?.isActive?.(note);
  layer.innerHTML=`<div class="tourCardMenu" role="menu"><button type="button" role="menuitem" data-tour-action="export">${icon('file_download')}<span>Lokal exportieren</span></button><button type="button" role="menuitem" data-tour-action="cloud">${icon('cloud_upload')}<span>${cloudActive?'Online-Freigabe verwalten':'Temporär online teilen'}</span></button>${cloudActive?`<button type="button" role="menuitem" data-tour-action="copy">${icon('content_copy')}<span>Freigabecode kopieren</span></button>`:''}</div>`;
  page.appendChild(layer);
  const menu=layer.querySelector('.tourCardMenu');
  const pageRect=page.getBoundingClientRect(),buttonRect=button.getBoundingClientRect();
  requestAnimationFrame(()=>{
    const menuRect=menu.getBoundingClientRect();
    const top=Math.max(12,Math.min(pageRect.height-menuRect.height-12,buttonRect.bottom-pageRect.top+6));
    const right=Math.max(12,pageRect.right-buttonRect.right);
    menu.style.top=`${top}px`;
    menu.style.right=`${right}px`;
  });
  layer.addEventListener('click',event=>{if(event.target===layer)closeTournamentCardMenu(page)});
  menu.addEventListener('click',event=>event.stopPropagation());
  menu.querySelector('[data-tour-action="export"]').onclick=()=>{
    closeTournamentCardMenu(page);
    if(typeof window.exportTournamentNote==='function')window.exportTournamentNote(note);
    else toast('Exportfunktion nicht verfügbar');
  };
  menu.querySelector('[data-tour-action="cloud"]').onclick=()=>{
    closeTournamentCardMenu(page);
    if(window.CloudShare?.openShareDialog)window.CloudShare.openShareDialog(note);
    else toast('Online-Freigabe nicht verfügbar');
  };
  menu.querySelector('[data-tour-action="copy"]')?.addEventListener('click',()=>{
    closeTournamentCardMenu(page);
    window.CloudShare?.copyShareCode?.(note);
  });
}
function showTournamentHistory(parentPage){
  const page=document.createElement('div');
  page.className='pageScreen tournamentHistory';
  applyTournamentTheme(page);applyTournamentNativeMetrics(page);
  let search='',searchExpanded=false;
  const refreshCloud=()=>{if(page.isConnected)draw()};
  window.addEventListener('mg-cloud-share-changed',refreshCloud);
  const closePage=()=>{window.removeEventListener('mg-cloud-share-changed',refreshCloud);page.remove()};
  const suggestions=()=>{
    if(!searchExpanded||!tournamentNotes.length)return [];
    const values=[...tournamentNotes.map(n=>n.location).filter(Boolean),...tournamentNotes.map(n=>String(n.system||'').replace(/\s+/g,' ').trim()).filter(Boolean)];
    return [...new Set(values)].filter(v=>v.toLowerCase().includes(search.toLowerCase())&&v.toLowerCase()!==search.toLowerCase()).slice(0,8);
  };
  function draw(){
    applyTournamentTheme(page);applyTournamentNativeMetrics(page);
    const q=search.trim().toLowerCase();
    const filtered=tournamentNotes.filter(n=>`${n.location||''} ${String(n.system||'').replace(/\s+/g,' ')}`.toLowerCase().includes(q));
    const chips=suggestions();
    const header=searchExpanded
      ?`<div class="historyTop tourSearchTop"><button class="pageIcon" id="tourHistSearchBack">${icon('arrow_back')}</button><label class="tourSearchField"><input id="tourHistQuery" value="${esc(search)}" placeholder="Suchen..." autocomplete="off"><button type="button" class="pageIcon" id="tourHistSearchClose">${icon('close')}</button></label></div>${chips.length?`<div class="tourSearchSuggestions">${chips.map(v=>`<button class="tourSearchChip" data-tour-suggestion="${esc(v)}">${esc(v)}</button>`).join('')}</div>`:''}`
      :`<div class="historyTop"><button class="pageIcon" id="tourHistBack">${icon('arrow_back')}</button><div class="pageTitle">Gespeicherte Notizen</div><button class="pageIcon search" id="tourHistSearch">${icon('search')}</button></div>`;
    const empty=`<div class="tourHistoryEmpty"><div class="tourHistoryEmptyIcon"><span>${icon(q?'search':'history')}</span></div><div>${q?'Keine passenden Notizen gefunden':'Noch keine Notizen gespeichert'}</div></div>`;
    page.innerHTML=`<div class="tourHistoryHeaderSurface">${header}</div><div class="tourHistoryList">${filtered.length?filtered.map(n=>tournamentCard(n,tournamentNotes.indexOf(n))).join(''):empty}</div><div class="tourImportFabs" aria-label="Turniernotizen importieren"><button type="button" class="tourImportFab cloud" id="tourCloudImportFab" aria-label="Geteilte Notiz per Cloud-Code laden" title="Cloud-Code laden">${icon('cloud_download')}</button><button type="button" class="tourImportFab local" id="tourLocalImportFab" aria-label="Turniernotiz von diesem Gerät importieren" title="Lokale Datei importieren">${icon('file_upload')}</button></div>`;
    page.querySelector('#tourHistBack')?.addEventListener('click',closePage);
    page.querySelector('#tourHistSearch')?.addEventListener('click',()=>{searchExpanded=true;draw()});
    page.querySelector('#tourHistSearchBack')?.addEventListener('click',()=>{searchExpanded=false;search='';draw()});
    page.querySelector('#tourHistSearchClose')?.addEventListener('click',()=>{if(search){search='';draw()}else{searchExpanded=false;draw()}});
    const input=page.querySelector('#tourHistQuery');
    if(input){
      input.addEventListener('input',e=>{search=e.target.value;draw()});
      requestAnimationFrame(()=>{const live=page.querySelector('#tourHistQuery');if(live){live.focus({preventScroll:true});const len=live.value.length;live.setSelectionRange?.(len,len)}});
    }
    page.querySelectorAll('[data-tour-suggestion]').forEach(btn=>btn.addEventListener('click',()=>{search=btn.dataset.tourSuggestion||'';draw()}));
    page.querySelector('#tourCloudImportFab')?.addEventListener('click',event=>{event.stopPropagation();if(window.CloudShare?.openImportDialog)window.CloudShare.openImportDialog();else toast('Cloud-Import nicht verfügbar')});
    page.querySelector('#tourLocalImportFab')?.addEventListener('click',event=>{
      event.stopPropagation();
      if(typeof window.importTournamentNotes==='function')window.importTournamentNotes(()=>draw());
      else toast('Importfunktion nicht verfügbar');
    });
    page.querySelectorAll('[data-cloud-copy]').forEach(button=>button.addEventListener('click',event=>{
      event.preventDefault();event.stopPropagation();
      const note=tournamentNotes[Number(button.dataset.cloudCopy)];
      if(note)window.CloudShare?.copyShareCode?.(note);
    }));
    page.querySelectorAll('[data-cloud-manage]').forEach(status=>status.addEventListener('click',event=>{
      if(event.target.closest('[data-cloud-copy]'))return;
      event.preventDefault();event.stopPropagation();
      const note=tournamentNotes[Number(status.dataset.cloudManage)];
      if(note)window.CloudShare?.openManageDialog?.(note);
    }));
    page.querySelectorAll('[data-tour-menu]').forEach(button=>button.addEventListener('click',event=>{
      event.preventDefault();event.stopPropagation();
      const index=Number(button.dataset.tourMenu),note=tournamentNotes[index];
      if(note)showTournamentCardMenu(page,button,note);
    }));
    page.querySelectorAll('[data-tour-note]').forEach(card=>{
      const i=+card.dataset.tourNote,w=Math.max(240,card.getBoundingClientRect().width||320);
      makeSwipeable(card,{threshold:w*.5,maxTranslate:w*.48,swipeSound:true,onSwipe:(dir)=>{
        if(dir>0)showTournamentEditor(tournamentNotes[i],page);
        else confirmDialog('Notiz löschen?',`Möchtest du die Notizen für "${tournamentNotes[i].location||'diesen Ort'}" wirklich unwiderruflich löschen?`,()=>{tournamentNotes.splice(i,1);persistTournament();draw()},'Löschen');
      },onTap:()=>showTournamentView(tournamentNotes[i],page)});
    });
  }
  document.body.appendChild(page);draw();applyTournamentNativeMetrics(page);
}

function tournamentNoteText(n){
  const lines=[`MiniGolf Turnier-Notiz`,`${n.location||'Ortangabe'}`,`${String(n.system||SYSTEMS[0]).replace('\n',' ')}`,`${formatGameDate(n.date)}`,''];
  (n.holes||[]).forEach((h,i)=>{
    const parts=[];
    if(h.ball)parts.push(`Ball: ${h.ball}`);
    if(h.start)parts.push(`Abschlag: ${h.start}`);
    if(h.notes)parts.push(`Notiz: ${h.notes}`);
    if(parts.length)lines.push(`Bahn ${i+1}: ${parts.join(' · ')}`);
  });
  return lines.join('\n');
}
async function shareTournamentNote(n){
  const text=tournamentNoteText(n);
  try{if(navigator.share)await navigator.share({title:'MiniGolf Turnier-Notiz',text});else if(navigator.clipboard){await navigator.clipboard.writeText(text);toast('Notiz kopiert')}else downloadText(text,'minigolf_turniernotiz.txt')}
  catch(_){toast('Teilen abgebrochen')}
}
function showTournamentView(existing,parentPage){
  const note=JSON.parse(JSON.stringify(existing||emptyTournamentNote()));
  const page=document.createElement('div');
  page.className='pageScreen tournamentEditor tournamentView nativeTournamentEditor';
  applyTournamentTheme(page);
  applyTournamentNativeMetrics(page);
  const sys=String(note.system||SYSTEMS[0]).replace('\n',' '), loc=note.location||'Ortangabe';
  const holesHtml=()=>note.holes.map((h,i)=>`<div class="tourHole"><b>${i+1}</b><div class="tourHoleContent"><div class="tourLine"><div class="tourInputs"><input readonly value="${esc(h.ball)}" placeholder="Ball"><input readonly value="${esc(h.start)}" placeholder="Abschlag"></div><button class="cameraBtn viewCamera" disabled>${icon((h.images&&h.images.length)?'image':'image_not_supported')}</button></div><input class="tourNotesInput" readonly value="${esc(h.notes)}" placeholder="Notizen..."></div></div>`).join('');
  page.innerHTML=`<div class="editorTop viewEditorTop"><button class="pageIcon" id="viewBack">${icon('arrow_back')}</button><div class="viewTitleBlock"><div class="viewTitleLine">${icon('place')}<b>${esc(loc)}</b></div><div class="viewSub">${esc(sys)}</div><div class="viewDate">${icon('calendar_month')}<span>${esc(formatGameDate(note.date))}</span></div></div><button class="saveIcon viewShareIcon" id="shareNote">${icon('share')}</button></div><div class="editorBody viewEditorBody"><div class="holesScroller viewHolesScroller"><div class="holesList">${holesHtml()}</div></div></div>`;
  page.querySelector('#viewBack').onclick=()=>page.remove();
  page.querySelector('#shareNote').onclick=()=>shareTournamentNote(note);
  document.body.appendChild(page);
  applyTournamentNativeMetrics(page);
}
function exportTournamentNotes(){const text=JSON.stringify({version:1,notes:tournamentNotes},null,2);const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([text],{type:'application/json'}));a.download='MiniGolf_Turniernotizen.mgpk';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),500);toast('Backup erstellt')}
function importTournamentNotes(){const inp=document.createElement('input');inp.type='file';inp.accept='.mgpk,application/json,*/*';inp.onchange=()=>{const f=inp.files?.[0];if(!f)return;const r=new FileReader();r.onload=()=>{try{const data=JSON.parse(r.result);const notes=Array.isArray(data.notes)?data.notes:(Array.isArray(data)?data:[]);tournamentNotes=[...notes,...tournamentNotes];persistTournament();toast(`${notes.length} Notizen importiert!`)}catch(_){toast('Import fehlgeschlagen')}};r.readAsText(f)};inp.click()}

// v59 — Nativer Hintergrundeditor. Die Bearbeitung wird in das gespeicherte JPEG eingebrannt,
// damit Hauptansicht, Vorschaubilder und Neustarts denselben Hintergrund verwenden.
const BG_FILTERS=[
  ['NONE','Original'],['BW','S/W'],['SEPIA','Sepia'],['COLD','Kalt'],['WARM','Warm'],['VINTAGE','Vintage']
];
function clampByte(v){return v<0?0:v>255?255:v}
function drawImageCover(ctx,img,width,height){
  const iw=img.naturalWidth||img.width,ih=img.naturalHeight||img.height;
  const scale=Math.max(width/iw,height/ih),sw=width/scale,sh=height/scale;
  const sx=(iw-sw)/2,sy=(ih-sh)/2;
  ctx.clearRect(0,0,width,height);ctx.drawImage(img,sx,sy,sw,sh,0,0,width,height);
}
function applyBackgroundColorMatrix(canvas,edit){
  const ctx=canvas.getContext('2d',{willReadFrequently:true});
  let image;
  try{image=ctx.getImageData(0,0,canvas.width,canvas.height)}catch(_){return}
  const d=image.data,preset=edit.preset||'NONE';
  const sat=Math.max(0,1+(Number(edit.saturation)||0)*.1);
  const contrast=Math.max(0,1+(Number(edit.contrast)||0)*.1);
  const brightness=Number(edit.brightness)||0;
  const applySat=(r,g,b,s)=>{
    const y=.213*r+.715*g+.072*b;
    return [y+(r-y)*s,y+(g-y)*s,y+(b-y)*s];
  };
  for(let i=0;i<d.length;i+=4){
    let r=d[i],g=d[i+1],b=d[i+2];
    if(preset==='BW'||preset==='SEPIA'){
      [r,g,b]=applySat(r,g,b,0);
      if(preset==='SEPIA'){r+=30;g+=10;b*=.8}
    }else if(preset==='COLD'){
      const oldG=g;r=.9*r;g=.9*g;b=.2*oldG+1.2*b+10;
    }else if(preset==='WARM'){
      r=1.2*r+10;g+=5;b=.8*b;
    }else if(preset==='VINTAGE'){
      r+=10;g=.9*g+5;b=.9*b;[r,g,b]=applySat(r,g,b,.8);
    }
    [r,g,b]=applySat(r,g,b,sat);
    const translate=brightness+128*(1-contrast);
    d[i]=clampByte(contrast*r+translate);
    d[i+1]=clampByte(contrast*g+translate);
    d[i+2]=clampByte(contrast*b+translate);
  }
  ctx.putImageData(image,0,0);
}
function blurBackgroundCanvas(canvas,radius){
  radius=Math.max(0,Number(radius)||0);if(radius<.5)return;
  const copy=document.createElement('canvas'),w=canvas.width,h=canvas.height;copy.width=w;copy.height=h;
  copy.getContext('2d').drawImage(canvas,0,0);
  const ctx=canvas.getContext('2d');ctx.save();ctx.clearRect(0,0,w,h);
  if('filter' in ctx){
    const pad=Math.max(2,Math.ceil(radius*2));
    const padded=document.createElement('canvas');padded.width=w+pad*2;padded.height=h+pad*2;
    const pctx=padded.getContext('2d');
    pctx.drawImage(copy,pad,pad);
    // Randpixel nach außen verlängern, damit beim Blur keine transparenten/dunklen Säume entstehen.
    pctx.drawImage(copy,0,0,w,1,pad,0,w,pad);
    pctx.drawImage(copy,0,h-1,w,1,pad,pad+h,w,pad);
    pctx.drawImage(copy,0,0,1,h,0,pad,pad,h);
    pctx.drawImage(copy,w-1,0,1,h,pad+w,pad,pad,h);
    pctx.drawImage(copy,0,0,1,1,0,0,pad,pad);
    pctx.drawImage(copy,w-1,0,1,1,pad+w,0,pad,pad);
    pctx.drawImage(copy,0,h-1,1,1,0,pad+h,pad,pad);
    pctx.drawImage(copy,w-1,h-1,1,1,pad+w,pad+h,pad,pad);
    ctx.filter=`blur(${radius}px)`;ctx.drawImage(padded,-pad,-pad);ctx.filter='none';
  }else ctx.drawImage(copy,0,0);
  ctx.restore();
}
function renderBackgroundPreview(canvas,img,edit,dragging=false){
  const rect=canvas.getBoundingClientRect(),dpr=Math.min(2,window.devicePixelRatio||1);
  const width=Math.max(1,Math.round(rect.width*dpr)),height=Math.max(1,Math.round(rect.height*dpr));
  if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height}
  const ctx=canvas.getContext('2d');drawImageCover(ctx,img,width,height);
  applyBackgroundColorMatrix(canvas,edit);
  const blur=(Number(edit.blur)||0)*(dragging&&edit.mode==='blur'?.3:1)*dpr;
  blurBackgroundCanvas(canvas,blur);
}
function renderBackgroundFullImage(img,edit,maxDimension=2560,referenceWidth=1280){
  const iw=img.naturalWidth||img.width,ih=img.naturalHeight||img.height;
  const scale=Math.min(1,maxDimension/Math.max(iw,ih));
  const width=Math.max(1,Math.round(iw*scale)),height=Math.max(1,Math.round(ih*scale));
  const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
  canvas.getContext('2d').drawImage(img,0,0,width,height);
  applyBackgroundColorMatrix(canvas,edit);
  const scaledBlur=(Number(edit.blur)||0)*(width/Math.max(1,referenceWidth));
  blurBackgroundCanvas(canvas,scaledBlur);
  return canvas;
}
function encodeBackgroundCanvas(canvas){
  const maxChars=3800000;
  for(const quality of [.92,.86,.78]){
    const data=canvas.toDataURL('image/jpeg',quality);if(data.length<=maxChars)return data;
  }
  let current=canvas;
  for(const factor of [.82,.68]){
    const small=document.createElement('canvas');small.width=Math.max(1,Math.round(current.width*factor));small.height=Math.max(1,Math.round(current.height*factor));
    small.getContext('2d').drawImage(current,0,0,small.width,small.height);current=small;
    const data=current.toDataURL('image/jpeg',.82);if(data.length<=maxChars)return data;
  }
  return current.toDataURL('image/jpeg',.72);
}
function showBackgroundEditor(imageData){
  const edit={brightness:0,contrast:0,saturation:0,blur:0,preset:'NONE',mode:'brightness'};
  const page=document.createElement('div');page.className='backgroundEditor';
  page.innerHTML=`<canvas class="bgEditCanvas"></canvas>
    <div class="bgEditTop"><button class="bgEditTopBtn" data-bg-cancel>${icon('arrow_back')}</button><h2>Hintergrund anpassen</h2><button class="bgEditTopBtn" data-bg-save>${icon('check')}</button></div>
    <div class="bgEditBottom"><div class="bgEditControl"></div><div class="bgEditModes">
      <button class="bgEditMode active" data-bg-mode="brightness" aria-label="Helligkeit">${icon('brightness_high')}</button>
      <button class="bgEditMode" data-bg-mode="contrast" aria-label="Kontrast">${icon('contrast')}</button>
      <button class="bgEditMode" data-bg-mode="saturation" aria-label="Sättigung">${icon('palette')}</button>
      <button class="bgEditMode" data-bg-mode="blur" aria-label="Unschärfe">${icon('blur_on')}</button>
      <button class="bgEditMode" data-bg-mode="filters" aria-label="Filter">${icon('filter_hdr')}</button>
    </div></div>`;
  document.body.appendChild(page);
  const canvas=page.querySelector('.bgEditCanvas'),control=page.querySelector('.bgEditControl'),save=page.querySelector('[data-bg-save]');
  const img=new Image();let dragging=false,raf=0,ready=false;
  const redraw=()=>{if(!ready)return;cancelAnimationFrame(raf);raf=requestAnimationFrame(()=>renderBackgroundPreview(canvas,img,edit,dragging))};
  const modeInfo={brightness:['Helligkeit',-100,100,10],contrast:['Kontrast',-10,10,1],saturation:['Sättigung',-10,10,1],blur:['Unschärfe',0,20,1]};
  const drawControl=()=>{
    page.querySelectorAll('[data-bg-mode]').forEach(b=>b.classList.toggle('active',b.dataset.bgMode===edit.mode));
    if(edit.mode==='filters'){
      control.innerHTML=`<div class="bgEditPresets">${BG_FILTERS.map(([key,label])=>`<button class="bgEditPreset ${edit.preset===key?'active':''}" data-bg-filter="${key}">${label}</button>`).join('')}</div>`;
      control.querySelectorAll('[data-bg-filter]').forEach(b=>b.onclick=()=>{edit.preset=b.dataset.bgFilter;drawControl();const selected=control.querySelector(`[data-bg-filter="${edit.preset}"]`);selected?.scrollIntoView({behavior:'smooth',block:'nearest',inline:'center'});redraw()});
      return;
    }
    const [label,min,max,step]=modeInfo[edit.mode],value=Number(edit[edit.mode])||0;
    control.innerHTML=`<label class="bgEditSliderLabel">${label}</label><input class="bgEditRange" type="range" min="${min}" max="${max}" step="${step}" value="${value}" aria-label="${label}">`;
    const range=control.querySelector('input');
    range.addEventListener('pointerdown',()=>{dragging=true});
    range.addEventListener('input',()=>{edit[edit.mode]=Number(range.value);redraw()});
    const finish=()=>{dragging=false;redraw()};range.addEventListener('change',finish);range.addEventListener('pointerup',finish);range.addEventListener('pointercancel',finish);
  };
  page.querySelectorAll('[data-bg-mode]').forEach(b=>b.onclick=()=>{edit.mode=b.dataset.bgMode;drawControl();redraw()});
  const closeToSettings=()=>{cancelAnimationFrame(raf);page.remove();showSettingsDialog()};
  page.querySelector('[data-bg-cancel]').onclick=closeToSettings;
  save.onclick=async()=>{
    if(!ready||save.disabled)return;const previousBg=state.settings.bgData;save.disabled=true;save.classList.add('processing');save.innerHTML='<span class="bgEditSpinner"></span>';
    await new Promise(r=>setTimeout(r,30));
    try{
      const previewReferenceWidth=Math.min(1280,(img.naturalWidth||1280)*Math.min(1,1280/Math.max(img.naturalWidth||1,img.naturalHeight||1)));
      const finalCanvas=renderBackgroundFullImage(img,edit,2560,previewReferenceWidth);
      const data=encodeBackgroundCanvas(finalCanvas);
      state.settings.bgData=data;
      try{persist()}catch(err){
        // Ein zweiter, kleinerer Export hilft bei knappem localStorage-Speicher.
        const reduced=document.createElement('canvas'),factor=Math.min(1,1600/Math.max(finalCanvas.width,finalCanvas.height));
        reduced.width=Math.max(1,Math.round(finalCanvas.width*factor));reduced.height=Math.max(1,Math.round(finalCanvas.height*factor));reduced.getContext('2d').drawImage(finalCanvas,0,0,reduced.width,reduced.height);
        state.settings.bgData=reduced.toDataURL('image/jpeg',.76);persist();
      }
      page.remove();showSettingsDialog();toast('Hintergrund übernommen');
    }catch(_){state.settings.bgData=previousBg;applyBg();save.disabled=false;save.classList.remove('processing');save.innerHTML=icon('check');toast('Bild konnte nicht gespeichert werden')}
  };
  const resize=()=>redraw();window.addEventListener('resize',resize,{passive:true});
  const observer=new MutationObserver(()=>{if(!page.isConnected){window.removeEventListener('resize',resize);observer.disconnect()}});observer.observe(document.body,{childList:true});
  img.onload=()=>{ready=true;drawControl();redraw()};
  img.onerror=()=>{page.remove();showSettingsDialog();toast('Bild konnte nicht geladen werden')};
  img.src=imageData;
}

function showSettingsDialog(){
  ensureState();overlayOn();const layer=document.createElement('div');layer.className='dialogLayer show settingsLayer';
  const close=()=>{layer.remove();overlayOff();render()};
  layer.onclick=close;
  layer.innerHTML=`<div class="settingsDialog" onclick="event.stopPropagation()"><div class="settingsHead"><h2>Einstellungen</h2><button class="infoBtn" type="button" aria-label="App-Info öffnen" title="App-Info">${icon('info')}</button></div><div class="settingsRow"><span class="setIcon">${icon('vibration')}</span><span>Vibration</span><button class="switch ${state.settings.vib?'on':''}" data-setting="vib" aria-label="Vibration"><i></i></button></div><div class="settingsRow"><span class="setIcon">${icon('volume_up')}</span><span>Touch-Sound</span><button class="switch ${state.settings.sound?'on':''}" data-setting="sound" aria-label="Touch-Sound"><i></i></button></div><div class="settingsRow"><span class="setIcon">${icon('brightness_high')}</span><span>Wachbleiben</span><button class="switch ${state.settings.wake?'on':''}" data-setting="wake" aria-label="Wachbleiben"><i></i></button></div><div class="settingsRow"><span class="setIcon">${icon('fullscreen')}</span><span>Vollbildmodus</span><button class="switch ${state.settings.full?'on':''}" data-setting="full" aria-label="Vollbildmodus"><i></i></button></div><div class="settingsBg"><div class="settingsBgTitle"><span class="setIcon">${icon('photo')}</span><span>Hintergrundbild</span></div><div class="settingsBgActions">${state.settings.bgData?'<button class="chooseBg reset" id="resetBg">Zurücksetzen</button>':''}<button class="chooseBg" id="chooseBg">Bild wählen</button></div><input id="bgFile" type="file" accept="image/*" hidden></div><button class="doneBtn" id="settingsDone">${icon('check')}<span>Fertig</span></button></div>`;
  document.body.appendChild(layer);
  layer.querySelectorAll('[data-setting]').forEach(btn=>btn.onclick=async()=>{const key=btn.dataset.setting;state.settings[key]=!state.settings[key];btn.classList.toggle('on',!!state.settings[key]);persist();if(key==='full')toggleFullscreen(state.settings.full);if(key==='wake')await applyWakeLock()});
  layer.querySelector('#chooseBg').onclick=()=>layer.querySelector('#bgFile').click();
  layer.querySelector('#resetBg')?.addEventListener('click',()=>{state.settings.bgData='';persist();layer.remove();showSettingsDialog();toast('Standardbild gesetzt')});
  layer.querySelector('#bgFile').onchange=e=>{const file=e.target.files?.[0];if(!file)return;if(!file.type.startsWith('image/')){toast('Bitte ein Bild auswählen');return}const reader=new FileReader();reader.onload=()=>{layer.remove();showBackgroundEditor(reader.result)};reader.onerror=()=>toast('Bild konnte nicht geladen werden');reader.readAsDataURL(file)};
  layer.querySelector('#settingsDone').onclick=close;
  layer.querySelector('.infoBtn').onclick=()=>{layer.remove();infoDialog()};
}
async function applyWakeLock(){try{if(wakeLock){await wakeLock.release();wakeLock=null}if(state.settings.wake&&'wakeLock' in navigator){wakeLock=await navigator.wakeLock.request('screen');wakeLock.addEventListener('release',()=>wakeLock=null);toast('Wachbleiben aktiv')}}catch(_){toast('Wachbleiben nicht verfügbar')}}
function toggleFullscreen(on){try{if(on&&!document.fullscreenElement&&document.documentElement.requestFullscreen)document.documentElement.requestFullscreen().catch(()=>{});if(!on&&document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen().catch(()=>{})}catch(_){}}
async function infoDialog(){
  overlayOn();
  let changelogText='Der Changelog konnte momentan nicht geladen werden.';
  try{
    const response=await fetch('./changelog.json');
    if(response.ok){
      const data=await response.json();
      const releases=Array.isArray(data?.releases)?data.releases:[];
      if(releases.length){
        changelogText=releases.map(release=>{
          const version=release?.version?`PWA V${release.version}`:'PWA-Update';
          const date=release?.date?` · ${release.date}`:'';
          const title=release?.title?`\n${release.title}`:'';
          const changes=Array.isArray(release?.changes)?release.changes.filter(Boolean).map(change=>`• ${change}`).join('\n'):'';
          return `${version}${date}${title}${changes?`\n${changes}`:''}`;
        }).join('\n\n');
      }
    }
  }catch(_){ }
  const sections=[
    ['Changelog',changelogText],
    ['Impressum','Angaben gemäß § 5 DDG\n\nMiniGolf Punktekarte\n\nKontakt:\nE-Mail: bloodwick3d@gmail.com\n\nVerantwortlich für den Inhalt nach § 18 Abs. 2 MStV:\nPatrick Kempken'],
    ['Datenschutz','Diese App arbeitet lokal. Alle Spielstände und Notizen werden ausschließlich auf deinem Gerät gespeichert. Es werden keine persönlichen Daten erfasst, analysiert oder an Dritte weitergegeben. Eine Internetverbindung wird nur zum Laden und Aktualisieren der PWA verwendet.'],
    ['Lizenzen & Open Source','Diese PWA nutzt offene Webstandards und Browser-Schnittstellen, darunter Service Worker, Web Share, IndexedDB und die Web Audio API.\n\nEin besonderer Dank geht an die Open-Source-Community für die Bereitstellung dieser Werkzeuge.']
  ];
  const layer=document.createElement('div');layer.className='dialogLayer show';
  const close=()=>{layer.remove();overlayOff()};layer.onclick=close;
  layer.innerHTML=`<div class="infoDialog" onclick="event.stopPropagation()"><h2>App-Info</h2><div class="infoSections">${sections.map(([title,body])=>`<section class="infoSection"><button class="infoSectionButton"><span>${esc(title)}</span>${icon('keyboard_arrow_down')}</button><div class="infoSectionBody">${esc(body)}</div></section>`).join('')}</div><div class="infoVersion">App-Version: PWA V84</div><button class="infoClose">Schließen</button></div>`;
  document.body.appendChild(layer);
  layer.querySelectorAll('.infoSectionButton').forEach(button=>button.onclick=()=>button.closest('.infoSection').classList.toggle('open'));
  layer.querySelector('.infoClose').onclick=close;
}
function confirmDialog(title,text,onYes,yesLabel='Ja'){
  overlayOn();const layer=document.createElement('div');layer.className='dialogLayer show';
  const close=()=>{layer.remove();overlayOff()};layer.onclick=close;
  const destructive=/(löschen|beenden|deaktivieren|entfernen|verwerfen|zurücksetzen)/i.test(yesLabel),pureRed=/löschen/i.test(yesLabel);
  const yesClass=destructive?` danger${pureRed?' pureRed':''}`:'';
  layer.innerHTML=`<div class="confirmDialog" onclick="event.stopPropagation()"><h2>${esc(title)}</h2><p>${esc(text)}</p><div class="confirmActions"><button class="pillButton gray" id="cancelConfirm">${onYes?'Abbrechen':'Schließen'}</button>${onYes?`<button class="pillButton${yesClass}" id="yesConfirm">${esc(yesLabel)}</button>`:''}</div></div>`;
  document.body.appendChild(layer);layer.querySelector('#cancelConfirm').onclick=close;layer.querySelector('#yesConfirm')?.addEventListener('click',()=>{close();onYes&&onYes()})
}

function makeSwipeable(el,{threshold=70,onSwipe,onTap,maxTranslate=80}={}){
  let sx=0,sy=0,active=false,moved=false,pid=null,lastDx=0;
  const down=e=>{if(e.pointerType==='mouse'&&e.button!==0)return;if(e.target.closest('button,input,textarea,[data-no-swipe]'))return;if(e.cancelable)e.preventDefault();sx=e.clientX;sy=e.clientY;active=true;moved=false;pid=e.pointerId;lastDx=0;el.classList.add('swiping');try{el.setPointerCapture(e.pointerId)}catch(_){} };
  const move=e=>{if(!active||(pid!==null&&e.pointerId!==pid))return;if(e.cancelable)e.preventDefault();const dx=e.clientX-sx,dy=e.clientY-sy;lastDx=dx;if(Math.abs(dx)>6||Math.abs(dy)>6)moved=true;if(Math.abs(dx)>Math.abs(dy)*.8){el.style.transform=`translateX(${Math.max(-maxTranslate,Math.min(maxTranslate,dx*.45))}px)`}};
  const up=e=>{if(!active||(pid!==null&&e.pointerId!==pid))return;if(e.cancelable)e.preventDefault();active=false;el.classList.remove('swiping');el.style.transition='transform .16s ease';el.style.transform='';setTimeout(()=>el.style.transition='',180);const dx=e.clientX-sx,dy=e.clientY-sy;if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.1){onSwipe&&onSwipe(dx>0?1:-1,e);return}if(!moved&&onTap)onTap(e)};
  const cancel=()=>{active=false;el.classList.remove('swiping');el.style.transform=''};
  el.addEventListener('pointerdown',down,{passive:false});el.addEventListener('pointermove',move,{passive:false});el.addEventListener('pointerup',up,{passive:false});el.addEventListener('pointercancel',cancel,{passive:false});
}

// v49: score/dropdown activation restored from stable v41.
// Global PC/touch gesture protection.
document.addEventListener('contextmenu',e=>{if(!e.target.closest('input,textarea'))e.preventDefault()});
document.addEventListener('selectstart',e=>{if(!e.target.closest('input,textarea'))e.preventDefault()});
document.addEventListener('dragstart',e=>e.preventDefault());
document.addEventListener('pointermove',e=>{if(e.buttons&&e.target.closest('.gesture,.drawerOverlay,.pageScreen')&&!e.target.closest('#scoreViewport,.scoreViewport')){if(e.cancelable)e.preventDefault()}},{passive:false});

document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&state.settings?.wake)applyWakeLock()});


// v56 — Nativer Abschlusszustand: Gewinner-Badge und Ergebniskarte.
let winnerFireworkFrame=0;
let winnerTickerFrames=[];
function isCurrentGameComplete(){
  return !!(state.players?.length&&state.players.every(p=>Array.isArray(p.roundScores)&&p.roundScores.length&&p.roundScores.every(r=>Array.isArray(r)&&r.length===18&&r.every(v=>v!=null))));
}
function winnerScoreColor(total,rounds=1,defaultColor='#000'){
  if(total===0)return '#000';
  const average=total/Math.max(1,rounds);
  if(state.system.includes('Eternit'))return average<18?defaultColor:average<20?'#2196f3':average<25?'#4caf50':average<30?'#f44336':'#000';
  if(state.system.includes('Beton'))return average<18?defaultColor:average<25?'#2196f3':average<30?'#4caf50':average<36?'#f44336':'#000';
  return average<18?defaultColor:average<30?'#2196f3':average<36?'#4caf50':average<40?'#f44336':'#000';
}
function currentWinnerInfo(){
  const sorted=(state.players||[]).map((p,index)=>({p,index,total:playerTotal(p)})).sort((a,b)=>a.total-b.total||a.index-b.index);
  const minimum=sorted[0]?.total??0;
  return {sorted,winners:sorted.filter(x=>x.total===minimum)};
}
function winnerRoundsMarkup(p){
  const rounds=p.roundScores||[];
  if(rounds.length<=1)return '';
  const values=rounds.map(r=>r.reduce((a,b)=>a+(Number(b)||0),0));
  return `<div class="winnerRounds"><span>Runden: </span>${values.map((value,index)=>`<b style="color:${winnerScoreColor(value,1,'#444')}">${value}</b>${index<values.length-1?'<span> | </span>':''}`).join('')}</div>`;
}
function winnerRankRowsMarkup(info){
  const rounds=state.players[0]?.roundScores.length||1;
  return info.sorted.map((entry,index)=>`<div class="winnerRankRow" style="--winner-row-delay:${500+index*200}ms"><div class="winnerRankMain"><div class="winnerRankName" style="color:${esc(entry.p.color||'#000')}">${index+1}. ${esc(entry.p.name||`Spieler ${index+1}`)}</div>${winnerRoundsMarkup(entry.p)}</div><div class="winnerRankPoints" style="color:${winnerScoreColor(entry.total,rounds,'#000')}" data-winner-total="${entry.total}">0 Pkt.</div></div>`).join('');
}
function winnerCardMarkup(){
  const info=currentWinnerInfo();
  const rounds=state.players[0]?.roundScores.length||1;
  const winnerNames=info.winners.map(entry=>`<div class="winnerName" style="color:${esc(entry.p.color||'#000')}">${esc(entry.p.name||'Spieler')}</div>`).join('');
  return `<div class="winnerCard" role="dialog" aria-modal="true" aria-label="Ergebniskarte"><button class="winnerShare" type="button" aria-label="Teilen">${icon('share')}</button><div class="winnerTrophy"><span class="winnerTrophyShadow">${icon('emoji_events')}</span><span class="winnerTrophyGold">${icon('emoji_events')}</span></div><div class="winnerCongrats">Herzlichen Glückwunsch!</div><div class="winnerSystem">${esc(String(state.system||SYSTEMS[0]).replace(/\n/g,' '))}</div><div class="winnerNames">${winnerNames}</div><div class="winnerVerb">${info.winners.length>1?'haben gewonnen!':'hat gewonnen!'}</div><div class="winnerRankingTitle">Rangliste:</div><div class="winnerRanking">${winnerRankRowsMarkup(info)}</div><div class="winnerActions"><div class="winnerActionRow">${rounds<4?`<button class="winnerAction winnerNext" type="button">${icon('add_circle_outline')}<span>Nächste Runde</span></button>`:''}<button class="winnerAction winnerNew" type="button">${icon('add_circle')}<span>Neues Spiel</span></button></div><button class="winnerAction winnerEnd" type="button">${icon('stop')}<span>Spiel beenden</span></button></div></div>`;
}
function cancelWinnerAnimations(){
  if(winnerFireworkFrame)cancelAnimationFrame(winnerFireworkFrame);
  winnerFireworkFrame=0;
  winnerTickerFrames.forEach(id=>cancelAnimationFrame(id));
  winnerTickerFrames=[];
}
function startWinnerTickers(layer){
  layer.querySelectorAll('[data-winner-total]').forEach((el,index)=>{
    const target=Number(el.dataset.winnerTotal)||0;
    const delay=500+index*200;
    setTimeout(()=>{
      if(!el.isConnected)return;
      const started=performance.now();
      const step=now=>{
        if(!el.isConnected)return;
        const progress=Math.min(1,(now-started)/1000);
        el.textContent=`${Math.round(target*progress)} Pkt.`;
        if(progress<1){const id=requestAnimationFrame(step);winnerTickerFrames.push(id)}
      };
      const id=requestAnimationFrame(step);winnerTickerFrames.push(id);
    },delay);
  });
}
function startWinnerFireworks(layer){
  const canvas=layer.querySelector('.winnerFireworks');
  if(!canvas)return;
  const ctx=canvas.getContext('2d');
  if(!ctx)return;
  const rect=layer.getBoundingClientRect(),dpr=Math.min(2,window.devicePixelRatio||1);
  canvas.width=Math.max(1,Math.round(rect.width*dpr));canvas.height=Math.max(1,Math.round(rect.height*dpr));
  canvas.style.width=`${rect.width}px`;canvas.style.height=`${rect.height}px`;ctx.scale(dpr,dpr);
  const bursts=Array.from({length:6},()=>({x:Math.random(),y:Math.random()*.5+.1,hue:Math.random()*360,start:Math.random(),parts:Array.from({length:40},()=>({angle:Math.random()*Math.PI*2,speed:Math.random()*.6+.4}))}));
  const started=performance.now();
  const draw=now=>{
    if(!canvas.isConnected)return;
    const elapsed=now-started;
    ctx.clearRect(0,0,rect.width,rect.height);
    const global=(elapsed%3500)/3500;
    bursts.forEach(burst=>{
      let local=global-burst.start;if(local<0)local+=1;
      if(local>=.4)return;
      const t=local/.4,alpha=1-t,distanceBase=t*rect.width*.35;
      ctx.fillStyle=`hsla(${burst.hue},80%,55%,${alpha})`;
      burst.parts.forEach(part=>{
        const distance=distanceBase*part.speed;
        const x=burst.x*rect.width+Math.cos(part.angle)*distance;
        const y=burst.y*rect.height+Math.sin(part.angle)*distance+t*t*200;
        ctx.beginPath();ctx.arc(x,y,Math.max(1,3*(1-t*.5)),0,Math.PI*2);ctx.fill();
      });
    });
    if(elapsed<5000)winnerFireworkFrame=requestAnimationFrame(draw);else ctx.clearRect(0,0,rect.width,rect.height);
  };
  winnerFireworkFrame=requestAnimationFrame(draw);
}
function closeWinnerCard(restoreBadge=true){
  cancelWinnerAnimations();
  document.querySelector('.winnerOverlay')?.remove();
  overlayOff();
  if(restoreBadge)syncWinnerBadge();
}
function showWinnerCard(navToken=null){
  if(!isCurrentGameComplete()||document.querySelector('.winnerOverlay'))return;
  document.querySelector('.winnerBadge')?.remove();
  overlayOn();
  const layer=document.createElement('div');
  layer.className='winnerOverlay show';
  if(navToken)layer.dataset.mgNavToken=navToken;
  layer.innerHTML=`<canvas class="winnerFireworks" aria-hidden="true"></canvas><div class="winnerOutside"></div>${winnerCardMarkup()}`;
  document.body.appendChild(layer);
  const card=layer.querySelector('.winnerCard');
  layer.querySelector('.winnerOutside').addEventListener('click',()=>closeWinnerCard(true));
  card.addEventListener('click',e=>e.stopPropagation());
  layer.querySelector('.winnerShare').addEventListener('click',()=>{const game=gameSnapshot('Ergebnis');Promise.resolve(shareGame(game)).finally(()=>closeWinnerCard(true))});
  layer.querySelector('.winnerNext')?.addEventListener('click',()=>{closeWinnerCard(false);addRound()});
  layer.querySelector('.winnerNew').addEventListener('click',()=>{closeWinnerCard(false);autosaveActive(true);resetKeepingPlayers();toast('Neues Spiel')});
  layer.querySelector('.winnerEnd').addEventListener('click',()=>{closeWinnerCard(false);const saved=saveCurrentToHistory('Spiel beendet');resetToStandard();toast(saved?'Spiel beendet':'Tabelle geleert')});
  startWinnerTickers(layer);startWinnerFireworks(layer);
}
function startWinnerBadgeTransition(button){
  if(!button||button.classList.contains('transitioning'))return;
  haptic();
  window.mgNavRegister?.(button);
  button.disabled=true;
  main.classList.add('badgeBlurTransition');
  scrim.classList.add('badgeBlurTransition');
  overlayOn();
  // Compose starts from the already visible 50x70-dp Surface. Force that frame to
  // be painted before applying the 450-ms morph, avoiding the browser's odd jump.
  void button.getBoundingClientRect();
  button.classList.add('transitioning');
  let finished=false;
  const finish=()=>{
    if(finished)return;
    finished=true;
    main.classList.remove('badgeBlurTransition');
    scrim.classList.remove('badgeBlurTransition');
    const navToken=button.dataset.mgNavToken||null;
    if(navToken)delete button.dataset.mgNavToken;
    button.remove();
    showWinnerCard(navToken);
  };
  button.addEventListener('transitionend',event=>{
    if(event.propertyName==='width')finish();
  });
  setTimeout(finish,520);
}
function syncWinnerBadge(){
  const app=document.querySelector('.app');
  const existing=document.querySelector('.winnerBadge');
  if(!app||!isCurrentGameComplete()||document.querySelector('.winnerOverlay')){existing?.remove();return}
  if(existing)return;
  const badge=document.createElement('button');
  badge.type='button';badge.className='winnerBadge';badge.setAttribute('aria-label','Ergebnisse anzeigen');
  badge.innerHTML=`<span class="winnerBadgeShadow">${icon('emoji_events')}</span><span class="winnerBadgeIcon">${icon('emoji_events')}</span>`;
  badge.addEventListener('click',()=>startWinnerBadgeTransition(badge));
  app.appendChild(badge);
}


// V69: Android-Hardware-Zurück wie in der nativen App.
// Unterseiten und Dialoge erhalten nur dann einen History-Eintrag, wenn sie offen
// sind. Auf der Haupttabelle bleibt deshalb kein künstlicher Eintrag zurück und
// Android kann die installierte PWA dort wie die native App schließen.
function isStandalonePwa(){
  return !!(window.matchMedia?.('(display-mode: standalone)').matches||window.navigator.standalone===true);
}
function topVisible(selector){
  const all=[...document.querySelectorAll(selector)].filter(el=>el.isConnected&&getComputedStyle(el).display!=='none');
  return all[all.length-1]||null;
}
function closeTopUiForHardwareBack(){
  const preview=topVisible('.mediaPreviewOverlay');
  if(preview){(preview.querySelector('button')||preview).click();return true}

  const dialog=topVisible('.dialogLayer.show:not(.tournamentExitLayer)');
  if(dialog){
    const button=dialog.querySelector('#cancelConfirm,.infoClose,#settingsDone,[data-cancel]');
    if(button){button.click();return true}
    const dismissEvent=typeof PointerEvent==='function'
      ?new PointerEvent('pointerdown',{bubbles:true,cancelable:true})
      :new Event('pointerdown',{bubbles:true,cancelable:true});
    dialog.dispatchEvent(dismissEvent);
    if(dialog.isConnected)dialog.click();
    return true;
  }

  const score=topVisible('.nativeScoreOverlay,.overlay.show');
  if(score){closeScoreCycle();return true}

  const drawer=topVisible('.drawerOverlay.show');
  if(drawer){closeDrawer(drawer);return true}

  if(topVisible('.winnerOverlay')){closeWinnerCard(true);return true}

  const background=topVisible('.backgroundEditor');
  if(background){background.querySelector('[data-bg-cancel]')?.click();return true}

  const page=topVisible('.pageScreen');
  if(page){
    const back=page.querySelector('[data-back],#tourHistSearchBack,#editorBack,#viewBack,#tourHistBack,#tourBack,#historyBack,#activeBack,.gamesBack');
    if(back){back.click();return true}
    page.remove();
    document.body.classList.remove('mediaEditing');
    return true;
  }

  if(closeMainTransient())return true;
  return false;
}
function installHardwareBackNavigation(){
  if(!isStandalonePwa()||window.__mgHardwareBackInstalled)return;
  window.__mgHardwareBackInstalled=true;
  const navSelector=[
    '.pageScreen:not(.nativeTournamentEditor)',
    '.pageScreen.nativeTournamentEditor.tournamentView',
    '.backgroundEditor',
    '.dialogLayer.show:not(.tournamentExitLayer)',
    '.drawerOverlay.show',
    '.nativeScoreOverlay',
    '.winnerOverlay',
    '.mediaPreviewOverlay',
    '.systemMenu',
    '.roundMenu'
  ].join(',');
  let ignoreNextPop=false;
  let restoringForward=false;
  const token=()=>`mg-ui-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,7)}`;
  const register=el=>{
    if(!el||!el.isConnected)return null;
    if(el.dataset.mgNavToken)return el.dataset.mgNavToken;
    const id=token();
    el.dataset.mgNavToken=id;
    try{history.pushState({mgUiToken:id},'',location.href)}catch(_){delete el.dataset.mgNavToken;return null}
    return id;
  };
  window.mgNavRegister=register;

  const matchingFrom=node=>{
    if(!(node instanceof Element))return [];
    const out=[];
    if(node.matches(navSelector))out.push(node);
    out.push(...node.querySelectorAll(navSelector));
    return out;
  };
  const tokenElementsFrom=node=>{
    if(!(node instanceof Element))return [];
    const out=[];
    if(node.dataset.mgNavToken)out.push(node);
    out.push(...node.querySelectorAll('[data-mg-nav-token]'));
    return out;
  };
  const observer=new MutationObserver(records=>{
    const added=[];
    const removed=[];
    for(const record of records){
      record.addedNodes.forEach(node=>added.push(...matchingFrom(node)));
      record.removedNodes.forEach(node=>removed.push(...tokenElementsFrom(node)));
    }
    const uniqueAdded=[...new Set(added)].filter(el=>el.isConnected&&!el.dataset.mgNavToken);
    const removedTokens=[...new Set(removed.map(el=>el.dataset.mgNavToken).filter(Boolean))];
    const currentToken=history.state?.mgUiToken;

    // Native screen replacements (Einstellungen -> Info, Einstellungen ->
    // Hintergrundeditor usw.) retain one back step instead of adding two stale ones.
    if(currentToken&&removedTokens.includes(currentToken)&&uniqueAdded.length){
      const replacement=uniqueAdded.pop();
      replacement.dataset.mgNavToken=currentToken;
    }else if(currentToken&&removedTokens.includes(currentToken)&&!window.__mgIgnoreNextPopState){
      // When a transient child UI (image-source dialog, crop screen, gallery,
      // etc.) closes above the tournament editor, the matching history.back()
      // belongs to that child. It must not be interpreted as leaving the
      // still-open note editor.
      if(document.querySelector('.nativeTournamentEditor:not(.tournamentView)')){
        window.__mgIgnoreTournamentEditorPopState=true;
      }
      ignoreNextPop=true;
      try{history.back()}catch(_){
        ignoreNextPop=false;
        window.__mgIgnoreTournamentEditorPopState=false;
      }
    }
    uniqueAdded.forEach(register);
  });
  observer.observe(document.body,{childList:true,subtree:true});

  window.addEventListener('popstate',event=>{
    if(window.__mgIgnoreNextPopState){window.__mgIgnoreNextPopState=false;return}
    if(ignoreNextPop){ignoreNextPop=false;return}
    if(restoringForward){restoringForward=false;return}

    // Der Turniereditor besitzt weiterhin seine eigene History, damit geänderte
    // Notizen beim Hardware-Zurück den Speichern/Verwerfen-Dialog öffnen.
    const editor=document.querySelector('.nativeTournamentEditor:not(.tournamentView) #editorBack')?.closest('.nativeTournamentEditor');
    const trackedTop=topVisible('[data-mg-nav-token]');
    if(editor){
      const transientAfterEditor=!!(trackedTop&&(editor.compareDocumentPosition(trackedTop)&Node.DOCUMENT_POSITION_FOLLOWING));
      if(!transientAfterEditor)return;
      // Hardware back already targets the transient UI. stopImmediatePropagation
      // below keeps this same popstate away from the editor listener.
    }
    if(!trackedTop)return; // Haupttabelle: Android darf die PWA regulär schließen.

    // Compose ignoriert Zurück während der 450-ms-Badge-Morph-Animation.
    if(trackedTop.matches('.winnerBadge.transitioning')){
      event.stopImmediatePropagation?.();
      restoringForward=true;
      try{history.forward()}catch(_){restoringForward=false}
      return;
    }

    delete trackedTop.dataset.mgNavToken; // der zugehörige History-Eintrag wurde bereits gepoppt
    const handled=closeTopUiForHardwareBack();
    if(handled)event.stopImmediatePropagation?.();
  },true);
}
installHardwareBackNavigation();

installGolfFeedbackBridge();
ensureState();persist();applyWakeLock();render();bindDynamicClicks();


// v27: keep the right add-player cell clickable even with scroll/drag layers.
document.addEventListener('pointerdown', function(e){
  const add=e.target.closest && e.target.closest('#addPlayerTop');
  if(!add) return;
  if(e.cancelable) e.preventDefault();
  e.stopPropagation();
}, true);
document.addEventListener('click', function(e){
  const add=e.target.closest && e.target.closest('#addPlayerTop');
  if(!add) return;
  if(e.cancelable) e.preventDefault();
  e.stopPropagation();
  if(state.players.length<MAX_PLAYERS)showPlayerDialog(null);
}, true);


// v37: Native-style active and completed games screens with visible under-card swipe actions.
function gamePlayers(g){return Array.isArray(g?.players)?g.players:[]}
function playerRoundSums(p){return (p.roundScores||[]).map(r=>(r||[]).reduce((a,b)=>a+(b||0),0))}
function cardPlayerPreview(g){
  const ps=gamePlayers(g).slice(0,3).map(p=>`<span style="color:${esc(p.color||'#555')}">${esc(p.name||'Spieler')}</span>`).join(`<span style="color:#777">, </span>`);
  const rest=gamePlayers(g).length>3?`<span style="color:#777"> ...</span>`:'';
  return ps+rest;
}
function statusIconFor(g){return icon(isFullGame(g)?'check_circle':'block')}
function historyMeta(g){
  const loc=(g.location||'').trim();
  return `${loc?`<div class="histMetaLine">${icon('place')} <span>${esc(loc)}</span></div>`:''}<div class="histMetaLine">${icon('calendar_month')} <span>${esc(formatGameDate(g.date))}</span></div>`;
}
function swipeWrap(type, cardHtml, expanded=false){
  const isHistory=type==='history';
  const leftIcon=isHistory?icon('share'):icon('play_arrow');
  const rightIcon=isHistory?icon('delete'):icon('check');
  return `<div class="swipeWrap ${isHistory?'historySwipe':'activeSwipe'} ${expanded?'expanded':''}"><div class="swipeBg"><span class="swipeIcon left">${leftIcon}</span><span class="swipeIcon right">${rightIcon}</span></div>${cardHtml}</div>`;
}
function compactHistoryCard(g,i){
  const sys=String(g.system||SYSTEMS[0]).replace('\n',' ');
  const winners=winnersText(g);
  const card=`<div class="historyCard compact gesture" data-history-card="${i}"><div class="histLeftIcon" style="color:${isFullGame(g)?'#4caf50':'#f44336'}">${statusIconFor(g)}</div><div class="histMain"><div class="histTitle">${esc(sys)}</div>${historyMeta(g)}</div>${winners?`<div class="histWinners">${icon('emoji_events')}<span>${esc(winners)}</span></div>`:''}</div>`;
  return swipeWrap('history',card,false);
}
function expandedHistoryCard(g,i){
  const sorted=gamePlayers(g).slice().sort((a,b)=>gameTotal(g,a)-gameTotal(g,b));
  const min=sorted.length?gameTotal(g,sorted[0]):0;
  const winnerNames=sorted.filter(p=>gameTotal(g,p)===min).map(p=>p.name);
  const players=sorted.map(p=>{const isWin=winnerNames.includes(p.name);return `<div class="histPlayerRow" style="--pc:${esc(p.color||'#b02062')}"><div><div class="histPlayerName">${isWin?icon('emoji_events'):''} ${esc(p.name||'Spieler')}</div>${playerRoundSums(p).length>1?`<div class="histRounds">Runden: ${esc(playerRoundSums(p).join(' | '))}</div>`:''}</div><div class="histPoints">${gameTotal(g,p)} Pkt.</div></div>`}).join('');
  const sys=String(g.system||SYSTEMS[0]).replace('\n',' ');
  const card=`<div class="historyCard expanded gesture" data-history-card="${i}"><div class="histHeaderLine"><div class="histLeftIcon" style="color:${isFullGame(g)?'#4caf50':'#f44336'}">${statusIconFor(g)}</div><div class="histMain"><div class="histTitle">${esc(sys)}</div>${historyMeta(g)}</div></div><div class="histDivider"></div>${players}<div class="histDivider"></div>${previewGrid(g)}</div>`;
  return swipeWrap('history',card,true);
}
function activeCard(g,i,expanded=false){
  const sys=String(g.system||SYSTEMS[0]).replace('\n',' ');
  if(!expanded){
    const card=`<div class="activeCard compact gesture" data-active-card="${i}"><div class="histLeftIcon">${icon('play_circle_outline')}</div><div class="histMain"><div class="histTitle">${esc(sys)}</div>${historyMeta(g)}<div class="histPlayerPreview">${cardPlayerPreview(g)}</div></div><div class="histWinners">${esc(gamePlayers(g).length)}<br>Spieler</div></div>`;
    return swipeWrap('active',card,false);
  }
  const players=gamePlayers(g).map(p=>`<div class="histPlayerRow" style="--pc:${esc(p.color||'#b02062')}"><div><div class="histPlayerName">${esc(p.name||'Spieler')}</div>${playerRoundSums(p).length?`<div class="histRounds">Runden: ${esc(playerRoundSums(p).join(' | '))}</div>`:''}</div><div class="histPoints">${gameTotal(g,p)} Pkt.</div></div>`).join('');
  const card=`<div class="activeCard expanded gesture" data-active-card="${i}"><div class="histHeaderLine"><div class="histLeftIcon">${icon('play_circle_outline')}</div><div class="histMain"><div class="histTitle">${esc(sys)}</div>${historyMeta(g)}</div></div><div class="histDivider"></div>${players}</div>`;
  return swipeWrap('active',card,true);
}
function showHistoryPage(){
  let expanded=-1;
  const page=document.createElement('div');page.className='pageScreen historyPageScreen';
  function draw(){
    const rows=endedGames.length?endedGames.map((g,i)=>i===expanded?expandedHistoryCard(g,i):compactHistoryCard(g,i)).join(''):`<div class="historyEmpty">Noch keine Spiele gespeichert</div>`;
    page.innerHTML=`<div class="historyTop"><button class="pageIcon" id="historyBack">${icon('arrow_back')}</button><div class="pageTitle">Beendete Spiele</div><button class="pageIcon search" id="historySearch">${icon('search')}</button></div><div class="historyListPage">${rows}</div>`;
    page.querySelector('#historyBack').onclick=()=>page.remove();
    page.querySelector('#historySearch').onclick=()=>toast('Suche später');
    page.querySelectorAll('[data-history-card]').forEach(card=>{
      const i=Number(card.dataset.historyCard);
      makeSwipeable(card,{threshold:76,maxTranslate:150,onSwipe:(dir)=>{if(dir>0)shareGame(endedGames[i]);else confirmDialog('Spiel löschen','Dieses beendete Spiel löschen?',()=>{endedGames.splice(i,1);persistHistory();expanded=-1;draw()})},onTap:()=>{expanded=expanded===i?-1:i;draw()}})
    })
  }
  draw();document.body.appendChild(page)
}
function showActivePage(){
  if(hasAnyScore())autosaveActive();
  let expanded=-1;
  const page=document.createElement('div');page.className='pageScreen activePageScreen';
  function draw(){
    const rows=activeGames.length?activeGames.map((g,i)=>activeCard(g,i,i===expanded)).join(''):`<div class="historyEmpty">Keine aktiven Spiele gefunden</div>`;
    page.innerHTML=`<div class="activeTop"><button class="pageIcon" id="activeBack">${icon('arrow_back')}</button><div class="pageTitle">Aktive Spiele</div><button class="pageIcon search" id="activeSearch">${icon('search')}</button></div><div class="activeListPage">${rows}</div>`;
    page.querySelector('#activeBack').onclick=()=>page.remove();
    page.querySelector('#activeSearch').onclick=()=>toast('Suche später');
    page.querySelectorAll('[data-active-card]').forEach(card=>{
      const i=Number(card.dataset.activeCard);
      makeSwipeable(card,{threshold:76,maxTranslate:150,onSwipe:(dir)=>{if(dir>0){loadGame(activeGames[i]);page.remove()}else confirmDialog('Spiel beenden','Aktives Spiel in beendete Spiele verschieben?',()=>{finishActiveGame(activeGames[i]);expanded=-1;draw()})},onTap:()=>{expanded=expanded===i?-1:i;draw()}})
    })
  }
  draw();document.body.appendChild(page)
}
function makeSwipeable(el,{threshold=70,onSwipe,onTap,maxTranslate=120,swipeSound=false}={}){
  let sx=0,sy=0,active=false,moved=false,pid=null;
  const wrap=el.closest('.swipeWrap,.tourSwipeRow');
  const setDir=(dx)=>{if(!wrap)return;wrap.classList.toggle('swipeRight',dx>7);wrap.classList.toggle('swipeLeft',dx<-7)};
  const clearDir=()=>{if(!wrap)return;wrap.classList.remove('swipeRight','swipeLeft')};
  const down=e=>{if(e.pointerType==='mouse'&&e.button!==0)return;if(e.target.closest('button,input,textarea,[data-no-swipe]'))return;if(e.cancelable)e.preventDefault();sx=e.clientX;sy=e.clientY;active=true;moved=false;pid=e.pointerId;el.classList.add('swiping');try{el.setPointerCapture(e.pointerId)}catch(_){} };
  const move=e=>{if(!active||(pid!==null&&e.pointerId!==pid))return;if(e.cancelable)e.preventDefault();const dx=e.clientX-sx,dy=e.clientY-sy;if(Math.abs(dx)>6||Math.abs(dy)>6)moved=true;if(Math.abs(dx)>Math.abs(dy)*.75){const tx=Math.max(-maxTranslate,Math.min(maxTranslate,dx*.72));el.style.transform=`translateX(${tx}px)`;setDir(dx)}else clearDir()};
  const up=e=>{if(!active||(pid!==null&&e.pointerId!==pid))return;if(e.cancelable)e.preventDefault();active=false;el.classList.remove('swiping');const dx=e.clientX-sx,dy=e.clientY-sy;const fire=Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.05;el.style.transition='transform .16s ease';el.style.transform='';setTimeout(()=>{el.style.transition='';clearDir()},170);if(fire){swipeSound?golfFeedback():haptic();onSwipe&&onSwipe(dx>0?1:-1,e);return}if(!moved&&onTap){golfFeedback();onTap(e)}};
  const cancel=()=>{active=false;el.classList.remove('swiping');el.style.transform='';clearDir()};
  el.addEventListener('pointerdown',down,{passive:false});el.addEventListener('pointermove',move,{passive:false});el.addEventListener('pointerup',up,{passive:false});el.addEventListener('pointercancel',cancel,{passive:false});
}


// v40 — zentrale Compose-Metriken für Hauptseite und ScoreTable.
// Android-dp werden nicht über transform:scale emuliert, sondern als konkrete Layoutwerte gesetzt.
let mainMetricsRaf=0;
function applyMainNativeMetrics(){
  cancelAnimationFrame(mainMetricsRaf);
  mainMetricsRaf=requestAnimationFrame(()=>{
    const app=document.querySelector('.app');
    if(!app)return;
    const ar=app.getBoundingClientRect();
    if(!ar.width||!ar.height)return;

    const desktop=window.matchMedia?.('(hover:hover) and (pointer:fine) and (min-width:700px)').matches;
    // Die PC-Emulation entspricht dem nativen 360dp-Referenzgerät. Auf dem Handy
    // entspricht ein CSS-Pixel dem logischen Android-dp des Browser-Viewports.
    const layoutWidth=desktop?360:ar.width;
    const scale=layoutWidth/360;

    // Compose berechnet die Topbar aus der vollständigen Fensterhöhe, bevor
    // systemBarsPadding den eigentlichen Inhaltsbereich reduziert.
    let fullHeight;
    if(desktop){
      fullHeight=780;
    }else{
      const sh=Number(window.screen?.height)||0;
      fullHeight=sh>0?sh:ar.height;
      // Unplausible Desktop-/Foldable-Werte nicht ungebremst übernehmen.
      if(fullHeight<ar.height||fullHeight>ar.height+180)fullHeight=ar.height+Math.min(80,layoutWidth*.19);
    }
    const titleH=fullHeight*.08;
    const systemFont=titleH*.22;
    const root=document.documentElement.style;
    const px=(name,value)=>root.setProperty(name,`${value.toFixed(3)}px`);
    root.setProperty('--main-dp-scale',scale.toFixed(5));
    px('--main-layout-w',layoutWidth);
    px('--main-full-h',fullHeight);
    px('--main-title-h',titleH);
    px('--main-logo-size',titleH*.70);
    px('--main-system-font',systemFont);
    // TopAppBar.kt rounds the location font before applying adaptiveSp().
    px('--main-location-font',Math.round(systemFont*.85)*scale);
    px('--main-location-placeholder-font',Math.round(systemFont*.80)*scale);
    px('--main-radius',15*scale);

    // Nach dem festen Topbar-Layout verteilt Compose den verbleibenden Raum
    // mit 1 : 18 : footerFactor. Wir lesen deshalb die reale Zeilenhöhe aus.
    requestAnimationFrame(()=>{
      const header=document.querySelector('.mainLayer .scoreHeader');
      const footer=document.querySelector('.mainLayer .scoreFooter');
      const rowH=header?.getBoundingClientRect().height||32*scale;
      const footerH=footer?.getBoundingClientRect().height||rowH;
      px('--main-row-h',rowH);
      px('--main-header-icon',rowH*.60);
      px('--main-round-font',rowH*.40);
      px('--main-name-font',rowH*.45);
      px('--main-hole-font',rowH*.50);
      px('--main-score-font',rowH*.55);
      px('--main-score-old-font',rowH*.40);
      px('--main-plus-icon',rowH*.45);
      px('--main-footer-font',footerH*.55);
      px('--main-round-total-font',footerH*.37);
      px('--main-round-old-font',footerH*.27);
      px('--main-grand-font',footerH*.37);
    });
  });
}
window.addEventListener('resize',applyMainNativeMetrics,{passive:true});
window.addEventListener('orientationchange',()=>setTimeout(applyMainNativeMetrics,120),{passive:true});
if(window.ResizeObserver){
  const mainMetricObserver=new ResizeObserver(()=>applyMainNativeMetrics());
  const appForMetrics=document.querySelector('.app');
  if(appForMetrics)mainMetricObserver.observe(appForMetrics);
}

// v41 — Aktive Spiele / Beendete Spiele nach den nativen Compose-Screens.
function applyGamesNativeMetrics(page){
  if(!page)return;
  const update=()=>{
    const width=page.getBoundingClientRect().width||360;
    page.style.setProperty('--games-scale',String(width/360));
  };
  update();
  if(window.ResizeObserver){
    const ro=new ResizeObserver(update);ro.observe(page);page._gamesRO=ro;
  }
}
function cleanupGamesPage(page){try{page?._gamesRO?.disconnect()}catch(_){}page?.remove()}
function nativeGameSystem(g){return String(g?.system||SYSTEMS[0]).replace(/\n/g,' ')}
function nativeGamePlayers(g){return Array.isArray(g?.players)?g.players:[]}
function nativePlayerRounds(p){return (p?.roundScores||[]).map(r=>(r||[]).reduce((a,b)=>a+(Number(b)||0),0))}
function nativePlayedHoles(p){return (p?.roundScores||[]).flat().filter(v=>v!=null&&Number(v)>0).length}
function nativeGameScoreColor(g,p,total=gameTotal(g,p),rounds=Math.max(1,(p?.roundScores||[]).length),played=nativePlayedHoles(p)){
  if(!total)return '#000';
  const projected=played>0?total*(18*rounds/played):total;
  const avg=projected/rounds;
  const sys=nativeGameSystem(g);
  if(sys.includes('Eternit')){if(avg<18)return '#fff';if(avg<20)return '#2196f3';if(avg<25)return '#4caf50';if(avg<30)return '#f44336';return '#000'}
  if(sys.includes('Beton')){if(avg<18)return '#fff';if(avg<25)return '#2196f3';if(avg<30)return '#4caf50';if(avg<36)return '#f44336';return '#000'}
  if(avg<18)return '#fff';if(avg<30)return '#2196f3';if(avg<36)return '#4caf50';if(avg<40)return '#f44336';return '#000';
}
function nativeGameMeta(g){
  const loc=String(g?.location||'').trim();
  return `${loc?`<div class="nativeGameMeta">${icon('place')}<span>${esc(loc)}</span></div>`:''}<div class="nativeGameMeta">${icon('calendar_month')}<span>${esc(formatGameDate(g?.date))}</span></div>`;
}
function nativePlayerPreview(g){
  const ps=nativeGamePlayers(g);let out='';
  ps.slice(0,3).forEach((p,i)=>{if(i)out+='<span style="color:#777">, </span>';out+=`<span style="color:${esc(p.color||'#555')}">${esc(p.name||'Spieler')}</span>`});
  if(ps.length>3)out+='<span style="color:#777"> ...</span>';
  return out;
}
function nativeWinnerInfo(g){
  const ps=nativeGamePlayers(g);if(!ps.length)return {names:[],min:0};
  const vals=ps.map(p=>({name:p.name||'Spieler',total:gameTotal(g,p)}));const min=Math.min(...vals.map(v=>v.total));
  return {names:vals.filter(v=>v.total===min).map(v=>v.name),min};
}
function nativeRoundsHtml(g,p,history=false){
  const sums=nativePlayerRounds(p);if(!sums.length||(history&&sums.length<=1))return '';
  const pieces=sums.map((sum,i)=>{
    const rs=p.roundScores?.[i]||[];const played=rs.filter(v=>v!=null&&Number(v)>0).length;
    const color=nativeGameScoreColor(g,p,sum,1,played);
    return `<span style="color:${color};font-weight:700">${sum}</span>`;
  }).join('<span> | </span>');
  return `<div class="nativeRounds" style="--round-indent:${history?'calc(18px * var(--games-scale))':'0px'}">Runden: ${pieces}</div>`;
}
function nativePlayersDetails(g,history=false){
  let ps=nativeGamePlayers(g).slice();const win=nativeWinnerInfo(g);
  if(history)ps.sort((a,b)=>gameTotal(g,a)-gameTotal(g,b));
  return ps.map(p=>{
    const total=gameTotal(g,p), winner=history&&win.names.includes(p.name||'Spieler');
    const totalColor=nativeGameScoreColor(g,p,total);
    return `<div class="nativePlayerBlock"><div class="nativePlayerLine"><div class="nativePlayerName" style="--player-color:${esc(p.color||'#333')}">${winner?icon('emoji_events'):''}<span>${esc(p.name||'Spieler')}</span></div><div class="nativePlayerPoints" style="color:${totalColor}">${total} Pkt.</div></div>${nativeRoundsHtml(g,p,history)}</div>`;
  }).join('');
}
function nativeHistoryHeader(g,showWinner=true){
  const full=isFullGame(g),w=nativeWinnerInfo(g),stats=!!g?.hasStats;
  return `<div class="nativeGameHeader"><div class="nativeGameInfo"><div class="nativeGameTitleRow"><span class="nativeStatusIcon" style="color:${full?'#4caf50':'#f44336'}">${icon(full?'check_circle':'block')}</span><span class="nativeGameTitle">${esc(nativeGameSystem(g))}</span>${stats?`<span class="nativeStatsIcon">${icon('bar_chart')}</span>`:''}</div>${nativeGameMeta(g)}</div>${showWinner&&w.names.length?`<div class="nativeWinner">${icon('emoji_events')}<span>${esc(w.names.join(', '))}</span></div>`:''}</div>`;
}
function nativeActiveHeader(g){return `<div class="nativeGameHeader"><div class="nativeGameInfo"><div class="nativeGameTitleRow"><span class="nativeGameTitle">${esc(nativeGameSystem(g))}</span></div>${nativeGameMeta(g)}</div></div>`}
function nativeStatsPreview(){return `<div class="scorePreview nativeStatsPreview" style="background:#f4f4f4;display:grid;place-items:center;color:#4caf50"><div style="display:grid;place-items:center;gap:8px;font-size:13px;color:#666;text-shadow:var(--g-shadow)">${icon('bar_chart')}<span>Bahnstatistik</span></div></div>`}
function nativeHistoryCard(g,index,expanded){
  const bitmapPreview=expanded?`<div class="nativeGameDivider"></div><div class="nativeBitmapHost">${window.bmSpinnerMarkup?window.bmSpinnerMarkup():'<div class="bitmapLoading"><span class="bitmapSpinner"></span><span>Ergebnisbild wird erstellt …</span></div>'}</div>`:'';
  const body=expanded?`${nativeHistoryHeader(g,false)}<div class="nativeGameDivider"></div>${nativePlayersDetails(g,true)}${bitmapPreview}`:nativeHistoryHeader(g,true);
  return nativeSwipeWrap('history',`<div class="nativeGameCard nativeHistoryCard" data-history-card="${index}">${body}</div>`);
}
function nativeActiveCard(g,index,expanded){
  const body=expanded?`${nativeActiveHeader(g)}<div class="nativeGameDivider"></div>${nativePlayersDetails(g,false)}`:`${nativeActiveHeader(g)}<div class="nativeActivePlayers">${nativePlayerPreview(g)}</div>`;
  return nativeSwipeWrap('active',`<div class="nativeGameCard nativeActiveCard" data-active-card="${index}">${body}</div>`);
}
function nativeSwipeWrap(type,card){
  const history=type==='history';
  return `<div class="gamesSwipeWrap ${history?'historySwipe':'activeSwipe'}"><div class="gamesSwipeBg"><span class="gamesSwipeAction left">${icon(history?'share':'play_arrow')}</span><span class="gamesSwipeAction right">${icon(history?'delete':'check')}</span></div>${card}</div>`;
}
function nativeGamesFilter(list,q){
  const needle=String(q||'').trim().toLowerCase();if(!needle)return list.map((g,index)=>({g,index}));
  return list.map((g,index)=>({g,index})).filter(({g})=>{
    const names=nativeGamePlayers(g).map(p=>p.name||'').join(' ');
    return `${nativeGameSystem(g)} ${g.location||''} ${formatGameDate(g.date)} ${names}`.toLowerCase().includes(needle);
  });
}
function nativeGameSuggestions(list,q){
  const needle=String(q||'').toLowerCase();const values=[];
  list.forEach(g=>{if(g.location)values.push(g.location);nativeGamePlayers(g).forEach(p=>p.name&&values.push(p.name))});
  return [...new Set(values)].filter(v=>v.toLowerCase().includes(needle)&&v.toLowerCase()!==needle).slice(0,8);
}
function nativeGamesHeader(type,searchExpanded,query,list){
  const title=type==='history'?'Beendete Spiele':'Aktive Spiele';
  if(!searchExpanded)return `<div class="gamesHeaderSurface"><div class="gamesHeaderRow"><button class="gamesIconButton" data-games-back>${icon('arrow_back')}</button><div class="gamesHeaderTitle">${title}</div><button class="gamesIconButton" data-games-search>${icon('search')}</button></div></div>`;
  const suggestions=nativeGameSuggestions(list,query);
  return `<div class="gamesHeaderSurface"><div class="gamesHeaderRow"><button class="gamesIconButton" data-games-search-back>${icon('arrow_back')}</button><label class="gamesSearchField"><input data-games-query value="${esc(query)}" placeholder="Suchen..." autocomplete="off"><button type="button" class="gamesIconButton" data-games-search-close>${icon('close')}</button></label></div>${suggestions.length?`<div class="gamesSuggestions">${suggestions.map(v=>`<button class="gamesSuggestionChip" data-games-suggestion="${esc(v)}">${esc(v)}</button>`).join('')}</div>`:''}</div>`;
}
function nativeGamesEmpty(type,query){
  const noResults=String(query||'').trim();const history=type==='history';
  return `<div class="gamesEmpty"><div class="gamesEmptyInner"><div class="gamesEmptyIcon"><span class="shadowIcon">${icon(history?'history':'play_circle_outline')}</span>${icon(history?'history':'play_circle_outline')}</div><div class="gamesEmptyText">${noResults?'Keine Ergebnisse':history?'Noch keine Spiele gespeichert':'Keine aktiven Spiele gefunden'}</div></div></div>`;
}
function bindNativeGameSwipe(card,{onRight,onLeft,onTap}){
  const wrap=card.closest('.gamesSwipeWrap');let sx=0,sy=0,pid=null,tracking=false,horizontal=false,moved=false,lastDx=0;
  const clear=()=>{wrap?.classList.remove('swipeRight','swipeLeft');card.classList.remove('swiping');card.style.transition='transform .16s ease';card.style.transform='';setTimeout(()=>card.style.transition='',170)};
  card.addEventListener('pointerdown',e=>{if(e.pointerType==='mouse'&&e.button!==0)return;if(e.target.closest('button,input,textarea,select'))return;sx=e.clientX;sy=e.clientY;pid=e.pointerId;tracking=true;horizontal=false;moved=false;lastDx=0;try{card.setPointerCapture(pid)}catch(_){}},{passive:true});
  card.addEventListener('pointermove',e=>{if(!tracking||e.pointerId!==pid)return;const dx=e.clientX-sx,dy=e.clientY-sy;if(Math.abs(dx)>5||Math.abs(dy)>5)moved=true;if(!horizontal&&Math.abs(dx)>8&&Math.abs(dx)>Math.abs(dy)*1.05)horizontal=true;if(!horizontal)return;if(e.cancelable)e.preventDefault();lastDx=dx;const width=card.getBoundingClientRect().width;const tx=Math.max(-width,Math.min(width,dx));card.classList.add('swiping');card.style.transform=`translateX(${tx}px)`;wrap?.classList.toggle('swipeRight',tx>0);wrap?.classList.toggle('swipeLeft',tx<0)},{passive:false});
  const end=e=>{if(!tracking||e.pointerId!==pid)return;tracking=false;const dx=e.clientX-sx,dy=e.clientY-sy;const width=card.getBoundingClientRect().width;const fire=horizontal&&Math.abs(dx)>=width*.5&&Math.abs(dx)>Math.abs(dy);clear();if(fire){golfFeedback();setTimeout(()=>dx>0?onRight?.():onLeft?.(),20)}else if(!moved){golfFeedback();onTap?.()}};
  card.addEventListener('pointerup',end,{passive:true});card.addEventListener('pointercancel',()=>{tracking=false;clear()},{passive:true});
}
function showNativeGamesPage(type){
  const history=type==='history';if(!history&&hasAnyScore())autosaveActive();
  const source=()=>history?endedGames:activeGames;let expandedId=null,searchExpanded=false,query='';
  const page=document.createElement('div');page.className=`pageScreen gamesPage ${history?'nativeHistoryPage':'nativeActivePage'}`;applyGamesNativeMetrics(page);
  function draw(){
    const list=source(),filtered=nativeGamesFilter(list,query);
    const rows=filtered.length?filtered.map(({g,index})=>history?nativeHistoryCard(g,index,expandedId===(g.id||index)):nativeActiveCard(g,index,expandedId===(g.id||index))).join(''):nativeGamesEmpty(type,query);
    page.innerHTML=`${nativeGamesHeader(type,searchExpanded,query,list)}<div class="gamesList">${rows}</div>`;
    applyGamesNativeMetrics(page);
    page.querySelector('[data-games-back]')?.addEventListener('click',()=>cleanupGamesPage(page));
    page.querySelector('[data-games-search]')?.addEventListener('click',()=>{searchExpanded=true;draw()});
    page.querySelector('[data-games-search-back]')?.addEventListener('click',()=>{searchExpanded=false;query='';draw()});
    page.querySelector('[data-games-search-close]')?.addEventListener('click',e=>{e.preventDefault();if(query){query='';draw()}else{searchExpanded=false;draw()}});
    const input=page.querySelector('[data-games-query]');
    if(input){input.addEventListener('input',e=>{query=e.target.value;draw()});requestAnimationFrame(()=>{const i=page.querySelector('[data-games-query]');i?.focus();try{i?.setSelectionRange(i.value.length,i.value.length)}catch(_){}})}
    page.querySelectorAll('[data-games-suggestion]').forEach(b=>b.addEventListener('click',()=>{query=b.dataset.gamesSuggestion||'';draw()}));
    if(history){
      page.querySelectorAll('[data-history-card]').forEach(card=>{
        const i=Number(card.dataset.historyCard),g=endedGames[i];if(!g)return;
        const id=g.id||i;
        bindNativeGameSwipe(card,{onRight:()=>shareGame(g),onLeft:()=>confirmDialog('Spiel löschen?',`Möchtest du dieses Spiel wirklich unwiderruflich löschen?`,()=>{endedGames.splice(i,1);persistHistory();expandedId=null;draw()}),onTap:()=>{expandedId=expandedId===id?null:id;draw()}});
        if(expandedId===id)window.hydrateHistoryBitmapPreview?.(card,g);
      });
      if(!expandedId&&endedGames[0])window.prewarmGameBitmap?.(endedGames[0]);
    }else{
      page.querySelectorAll('[data-active-card]').forEach(card=>{const i=Number(card.dataset.activeCard),g=activeGames[i];if(!g)return;bindNativeGameSwipe(card,{onRight:()=>{loadGame(g);cleanupGamesPage(page)},onLeft:()=>confirmDialog('Spiel beenden?',`Möchtest du dieses aktive Spiel beenden? Es wird danach in den beendeten Spielen angezeigt.`,()=>{finishActiveGame(g);expandedId=null;draw()}),onTap:()=>{const id=g.id||i;expandedId=expandedId===id?null:id;draw()}})})
    }
  }
  document.body.appendChild(page);draw();
}
function showHistoryPage(){showNativeGamesPage('history')}
function showActivePage(){showNativeGamesPage('active')}


// v79 — Native Edge-Swipe-Geste zum Öffnen des Seitenmenüs.
// MainActivity.kt verwendet auf der Hauptansicht eine unsichtbare, 30 dp breite
// Randzone und öffnet das Menü bei einer horizontalen Bewegung > 10 dp nach rechts.
function drawerEdgeSwipeIsAvailable(){
  if(document.querySelector('.drawerOverlay'))return false;
  if(document.querySelector('.overlay.show,.dialogLayer.show,.pageScreen,.winnerOverlay,.systemMenu,.roundMenu'))return false;
  if(document.querySelector('.mediaSourceLayer,.cropLayer,.drawLayer,.galleryLayer,.imagePreviewOverlay,.backgroundEditorPage'))return false;
  return true;
}
function installNativeDrawerEdgeSwipe(){
  const app=document.querySelector('.app');
  if(!app||app.querySelector('.drawerEdgeSwipeZone'))return;
  const edge=document.createElement('div');
  edge.className='drawerEdgeSwipeZone';
  edge.setAttribute('aria-hidden','true');
  app.appendChild(edge);

  let tracking=false,triggered=false,pointerId=null,startX=0,startY=0;
  const reset=()=>{tracking=false;triggered=false;pointerId=null};
  edge.addEventListener('pointerdown',event=>{
    if(event.pointerType==='mouse'&&event.button!==0)return;
    if(!drawerEdgeSwipeIsAvailable())return;
    tracking=true;triggered=false;pointerId=event.pointerId;
    startX=event.clientX;startY=event.clientY;
    try{edge.setPointerCapture(pointerId)}catch(_){}
  },{passive:true});
  edge.addEventListener('pointermove',event=>{
    if(!tracking||triggered||event.pointerId!==pointerId)return;
    const dx=event.clientX-startX,dy=event.clientY-startY;
    // Entspricht detectHorizontalDragGestures + dragAmount > 10f der nativen App.
    if(dx>10&&Math.abs(dx)>Math.abs(dy)){
      triggered=true;tracking=false;
      if(event.cancelable)event.preventDefault();
      event.stopPropagation();
      try{edge.releasePointerCapture(pointerId)}catch(_){}
      pointerId=null;
      if(drawerEdgeSwipeIsAvailable())showDrawer();
      return;
    }
    // Eine deutliche Bewegung nach links oder vertikale Scrollgeste nicht kapern.
    if(dx<-6||Math.abs(dy)>18&&Math.abs(dy)>Math.abs(dx)*1.25){
      try{edge.releasePointerCapture(pointerId)}catch(_){}
      reset();
    }
  },{passive:false});
  edge.addEventListener('pointerup',reset,{passive:true});
  edge.addEventListener('pointercancel',reset,{passive:true});
}
installNativeDrawerEdgeSwipe();
