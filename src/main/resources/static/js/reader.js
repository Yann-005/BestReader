// Best Reader — lecteur universel robuste
pdfjsLib.GlobalWorkerOptions.workerSrc = "/js/pdf/pdf.worker.min.js";

const token = localStorage.getItem('token');
const userId = localStorage.getItem('userId');
const bookId = window.location.pathname.split('/').pop();

let livreActuel = null;
let formatActuel = null;
let pageActuelle = 1;
let totalPages = 1;
let taillePolice = Number(localStorage.getItem('readerFontSize') || 16);
let themeActuel = normaliserTheme(localStorage.getItem('readerTheme') || 'clair');
let couleurMarqueur = 'YELLOW';
let pdfDoc = null;
let epubBook = null;
let epubRendition = null;
let annotations = [];
let dictionaryCache = {};
let txtPages = [];
let docxPages = [];
let scrollTimer = null;

const pageDocument = () => document.getElementById('pageDocument');
const contenuLecture = () => document.getElementById('contenuLecture');
const fileUrl = () => `/api/books/${bookId}/file`;

function headersAuth(extra = {}) {
    return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
}
async function fetchAvecAuth(url, options = {}) {
    return fetch(url, { ...options, headers: headersAuth(options.headers || {}) });
}

document.addEventListener('DOMContentLoaded', async () => {
    chargerDictionnaireLocal();
    appliquerBoutonsPreferences();
    await chargerLivre();
    await chargerPreferencesServeur();
    await chargerProgression();
    await chargerContenu();
    configurerSelectionTexte();
    configurerFermetureMenus();
    configurerNavigationParClicPage();
});

async function chargerLivre() {
    const res = await fetchAvecAuth(`/api/books/${bookId}?userId=${userId || ''}`);
    if (!res.ok) throw new Error('Livre introuvable');
    livreActuel = await res.json();
    formatActuel = livreActuel.format;
    totalPages = Math.max(1, Number(livreActuel.totalPages || 1));
    document.getElementById('titreLivre').textContent = livreActuel.title || 'Sans titre';
    document.title = `Best Reader — ${livreActuel.title || 'Lecture'}`;
}

async function chargerContenu() {
    const el = pageDocument();
    el.className = 'page-document';
    el.style.fontSize = `${taillePolice}px`;
    contenuLecture().scrollTop = 0;

    if (formatActuel === 'PDF') await chargerPDF();
    else if (formatActuel === 'WORD') await chargerDOCX();
    else if (formatActuel === 'EPUB') await chargerEPUB();
    else if (formatActuel === 'TXT') await chargerTXT();
    else el.textContent = 'Format non supporté.';

    await chargerAnnotations();
    appliquerAnnotations();
    appliquerThemeDocument();
    mettreAJourNavigation();
    mettreAJourProgression(Math.round((pageActuelle / totalPages) * 100));
}

// PDF
async function chargerPDF() {
    pageDocument().classList.add('pdf-page');
    if (!pdfDoc) {
        pdfDoc = await pdfjsLib.getDocument({ url: fileUrl(), httpHeaders: headersAuth() }).promise;
        totalPages = pdfDoc.numPages;
    }
    pageActuelle = clamp(pageActuelle, 1, totalPages);
    await afficherPagePDF(pageActuelle);
}

async function afficherPagePDF(num) {
    const page = await pdfDoc.getPage(num);
    const viewportBase = page.getViewport({ scale: 1 });
    const largeurMax = Math.min(window.innerWidth - 32, 1100);
    const baseScale = Math.max(0.7, Math.min(2.5, largeurMax / viewportBase.width));
    const scale = baseScale * (taillePolice / 16);
    const viewport = page.getViewport({ scale });

    const wrapper = document.createElement('div');
    wrapper.className = 'pdf-wrapper';
    wrapper.style.width = `${viewport.width}px`;
    wrapper.style.height = `${viewport.height}px`;

    const canvas = document.createElement('canvas');
    canvas.id = 'pdfCanvas';
    canvas.width = Math.floor(viewport.width);
    canvas.height = Math.floor(viewport.height);

    const textLayer = document.createElement('div');
    textLayer.className = 'pdf-text-layer';
    const highlightLayer = document.createElement('div');
    highlightLayer.className = 'pdf-highlight-layer';

    wrapper.append(canvas, highlightLayer, textLayer);
    pageDocument().innerHTML = '';
    pageDocument().appendChild(wrapper);

    await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise;
    await afficherTextePDF(page, viewport, textLayer);
}

async function afficherTextePDF(page, viewport, textLayer) {
    const textContent = await page.getTextContent();
    const styles = textContent.styles || {};
    for (const item of textContent.items) {
        if (!item.str || !item.str.trim()) continue;
        const span = document.createElement('span');
        span.textContent = item.str;
        span.className = 'pdf-text-item';
        const tx = pdfjsLib.Util.transform(viewport.transform, item.transform);
        const fontHeight = Math.hypot(tx[2], tx[3]) || 12;
        const style = styles[item.fontName] || {};
        span.style.left = `${tx[4]}px`;
        span.style.top = `${tx[5] - fontHeight}px`;
        span.style.fontSize = `${fontHeight}px`;
        span.style.fontFamily = style.fontFamily || 'sans-serif';
        span.style.transformOrigin = '0 0';
        textLayer.appendChild(span);
    }
}

function dessinerSurlignagePDFDepuisSelection() {
    const selection = window.getSelection();
    if (!selection.rangeCount) return false;
    const range = selection.getRangeAt(0);
    const layer = document.querySelector('.pdf-highlight-layer');
    const wrapper = document.querySelector('.pdf-wrapper');
    if (!layer || !wrapper) return false;
    const base = wrapper.getBoundingClientRect();
    for (const rect of range.getClientRects()) {
        if (rect.width < 2 || rect.height < 2) continue;
        const mark = document.createElement('span');
        mark.className = 'pdf-selection-highlight';
        mark.style.left = `${rect.left - base.left}px`;
        mark.style.top = `${rect.top - base.top}px`;
        mark.style.width = `${rect.width}px`;
        mark.style.height = `${rect.height}px`;
        mark.style.backgroundColor = getCouleurHex(couleurMarqueur);
        layer.appendChild(mark);
    }
    return true;
}

// DOCX
async function chargerDOCX() {
    pageDocument().classList.add('docx-page');
    if (window.docx && typeof window.docx.renderAsync === 'function') {
        const res = await fetchAvecAuth(fileUrl());
        const blob = await res.blob();
        pageDocument().innerHTML = '<div id="docxHost" class="docx-host"></div>';
        const host = document.getElementById('docxHost');
        await window.docx.renderAsync(blob, host, null, {
            className: 'docx', inWrapper: true, ignoreWidth: false, ignoreHeight: false,
            ignoreFonts: false, breakPages: false, useBase64URL: true,
            renderHeaders: true, renderFooters: true, renderFootnotes: true, renderEndnotes: true, experimental: true
        });
        docxPages = paginerHtmlDocx(host);
        totalPages = Math.max(1, docxPages.length || livreActuel.totalPages || 1);
        pageActuelle = clamp(pageActuelle, 1, totalPages);
        afficherPageDOCX(pageActuelle);
    } else {
        await chargerDOCXFallbackServeur();
    }
}

function paginerHtmlDocx(host) {
    const source = host.querySelector('.docx-wrapper') || host.querySelector('section.docx') || host;
    const elements = Array.from(source.querySelectorAll('p, h1, h2, h3, h4, h5, h6, table, img, ul, ol, blockquote'))
        .filter(el => (el.textContent || '').trim() || el.tagName === 'IMG' || el.querySelector('img'));
    if (!elements.length) return Array.from(host.querySelectorAll('section.docx'));
    const paged = document.createElement('div');
    paged.id = 'docxPagedHost';
    paged.className = 'docx-paged-host';
    host.innerHTML = '';
    host.appendChild(paged);
    const pages = [];
    let page = creerPageFlow('docx-generated-page');
    let paragraphes = 0;
    let lignes = 0;
    for (const el of elements) {
        const clone = el.cloneNode(true);
        const isParagraphLike = /^(P|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE)$/.test(el.tagName);
        const estimatedLines = Math.max(1, Math.ceil(((el.textContent || '').trim().length || 40) / 85));
        if ((paragraphes >= 4 || lignes >= 30) && page.childNodes.length) {
            pages.push(page);
            page = creerPageFlow('docx-generated-page');
            paragraphes = 0;
            lignes = 0;
        }
        page.appendChild(clone);
        if (isParagraphLike) paragraphes++;
        lignes += estimatedLines;
    }
    if (page.childNodes.length) pages.push(page);
    pages.forEach(pg => paged.appendChild(pg));
    return pages;
}

function creerPageFlow(extraClass = '') {
    const div = document.createElement('div');
    div.className = ('flow-reader-page ' + extraClass).trim();
    return div;
}

function afficherPageDOCX(page) {
    const host = document.getElementById('docxHost');
    if (!host || !docxPages.length) return;
    docxPages.forEach((s, i) => s.style.display = (i === page - 1 ? 'block' : 'none'));
    appliquerTailleDOCX();
}

async function chargerDOCXFallbackServeur() {
    const res = await fetchAvecAuth('/api/books/' + bookId + '/content?page=' + pageActuelle);
    const data = await res.json();
    totalPages = Math.max(1, Number(data.totalPages || 1));
    pageDocument().innerHTML = '<div id="docxHost" class="docx-fallback page-flow"><div class="flow-reader-page">' + (data.content || '') + '</div></div>';
    docxPages = Array.from(pageDocument().querySelectorAll('.flow-reader-page'));
    appliquerTailleDOCX();
}

function appliquerTailleDOCX() {
    const host = document.getElementById('docxHost');
    if (!host) return;
    const zoom = taillePolice / 16;
    host.style.setProperty('--reader-zoom', zoom);
    host.style.fontSize = taillePolice + 'px';
}

// EPUB
async function chargerEPUB() {
    pageDocument().classList.add('epub-page');
    if (typeof window.ePub !== 'function') {
        await chargerEPUBFallbackServeur();
        return;
    }
    pageDocument().innerHTML = '<div id="epubViewer"></div>';
    if (epubRendition) { try { epubRendition.destroy(); } catch(_) {} }
    if (epubBook) { try { epubBook.destroy(); } catch(_) {} }

    const res = await fetchAvecAuth(fileUrl());
    const buffer = await res.arrayBuffer();
    epubBook = window.ePub(buffer);
    epubRendition = epubBook.renderTo('epubViewer', {
        width: '100%',
        height: '100%',
        spread: 'none',
        flow: 'paginated',
        manager: 'default',
        allowScriptedContent: false
    });
    registerEpubThemes();
    epubRendition.themes.select(themeActuel);
    epubRendition.themes.fontSize(`${taillePolice}px`);
    epubRendition.on('relocated', (loc) => {
        const pct = loc?.start?.percentage ? Math.round(loc.start.percentage * 100) : pageActuelle;
        pageActuelle = clamp(pct || 1, 1, 100);
        totalPages = 100;
        mettreAJourProgression(pct);
        sauvegarderProgression();
    });
    totalPages = 100;
    await epubRendition.display();
}

function registerEpubThemes() {
    if (!epubRendition) return;
    epubRendition.themes.register('clair', { body: { color: '#222', background: '#fff' }, '*': { 'font-size': `${taillePolice}px !important` } });
    epubRendition.themes.register('sepia', { body: { color: '#3E1F00', background: '#f4ecd8' }, '*': { 'font-size': `${taillePolice}px !important` } });
    epubRendition.themes.register('sombre', { body: { color: '#eee', background: '#1f1f1f' }, '*': { 'font-size': `${taillePolice}px !important` } });
}

async function chargerEPUBFallbackServeur() {
    const res = await fetchAvecAuth(`/api/books/${bookId}/content?page=${pageActuelle}`);
    const data = await res.json();
    totalPages = Math.max(1, Number(data.totalPages || 1));
    pageDocument().innerHTML = `<div id="epubFallback" class="epub-fallback page-flow">${data.content || ''}</div>`;
}

// TXT
async function chargerTXT() {
    pageDocument().classList.add('txt-page');
    const res = await fetchAvecAuth(fileUrl());
    const texte = await res.text();
    txtPages = paginerTexte(texte);
    totalPages = Math.max(1, txtPages.length);
    pageActuelle = clamp(pageActuelle, 1, totalPages);
    afficherPageTXT(pageActuelle);
}

function paginerTexte(texte) {
    const lignesBrutes = String(texte || '').replace(/\r\n/g, '\n').split('\n');
    const pages = [];
    let current = [];
    let paragraphes = 0;
    let lignes = 0;
    let dansParagraphe = false;
    function pousserPage() {
        const txt = current.join('\n').trim();
        if (txt) pages.push(txt);
        current = [];
        paragraphes = 0;
        lignes = 0;
        dansParagraphe = false;
    }
    for (const ligne of lignesBrutes) {
        const vide = !ligne.trim();
        if (!vide && !dansParagraphe) { paragraphes++; dansParagraphe = true; }
        if (vide) dansParagraphe = false;
        current.push(ligne);
        lignes++;
        if ((paragraphes >= 4 || lignes >= 30) && current.join('\n').trim()) pousserPage();
    }
    pousserPage();
    return pages.length ? pages : [''];
}

function afficherPageTXT(page) {
    pageDocument().innerHTML = '<div class="flow-reader-page txt-flow-page"><pre class="txt-content">' + escapeHtml(txtPages[page - 1] || '') + '</pre></div>';
}

// Navigation
async function changerPage(direction) {
    if (formatActuel === 'EPUB' && epubRendition) {
        direction > 0 ? await epubRendition.next() : await epubRendition.prev();
        return;
    }
    const next = pageActuelle + direction;
    if (next < 1 || next > totalPages) return;
    pageActuelle = next;

    if (formatActuel === 'PDF') await afficherPagePDF(pageActuelle);
    else if (formatActuel === 'TXT') afficherPageTXT(pageActuelle);
    else if (formatActuel === 'WORD') {
        if (docxPages.length) afficherPageDOCX(pageActuelle); else await chargerDOCXFallbackServeur();
    } else if (formatActuel === 'EPUB') await chargerEPUBFallbackServeur();

    await chargerAnnotations();
    appliquerAnnotations();
    appliquerThemeDocument();
    mettreAJourNavigation();
    mettreAJourProgression(Math.round((pageActuelle / totalPages) * 100));
    await sauvegarderProgression();
    contenuLecture().scrollTo({ top: 0, behavior: 'smooth' });
}

function mettreAJourNavigation() {
    document.getElementById('infoPage').textContent = formatActuel === 'EPUB' && epubRendition ? `EPUB ${Math.round(pageActuelle)}%` : `Page ${pageActuelle} sur ${totalPages}`;
    document.getElementById('btnPrecedent').disabled = pageActuelle <= 1;
    document.getElementById('btnSuivant').disabled = pageActuelle >= totalPages;
}

function mettreAJourProgression(pct) {
    pct = clamp(Number(pct) || 0, 0, 100);
    document.getElementById('barreProgression').style.width = `${pct}%`;
    document.getElementById('texteProgression').textContent = `${pct}%`;
}

async function chargerProgression() {
    try {
        const res = await fetchAvecAuth(`/api/progress/book/${bookId}?userId=${userId || ''}`);
        if (res.ok) {
            const p = await res.json();
            pageActuelle = Math.max(1, Number(p.currentPage || 1));
            mettreAJourProgression(p.progressPercentage || 0);
        }
    } catch (_) {}
}

async function sauvegarderProgression() {
    if (!userId) return;
    try { await fetchAvecAuth(`/api/progress/book/${bookId}/page/${Math.round(pageActuelle)}?userId=${userId}`, { method: 'POST' }); }
    catch (e) { console.error('Erreur sauvegarde progression', e); }
}
setInterval(sauvegarderProgression, 60000);
window.addEventListener('beforeunload', sauvegarderProgression);

// Préférences
function normaliserTheme(theme) {
    if (theme === 'dark') return 'sombre';
    if (theme === 'light') return 'clair';
    return ['clair', 'sepia', 'sombre'].includes(theme) ? theme : 'clair';
}
async function chargerPreferencesServeur() {
    if (!userId) return;
    try {
        const res = await fetchAvecAuth(`/api/preferences/user/${userId}`);
        if (!res.ok) return;
        const pref = await res.json();
        themeActuel = normaliserTheme(pref.theme || themeActuel);
        taillePolice = Number(pref.fontSize || taillePolice);
        localStorage.setItem('readerTheme', themeActuel);
        localStorage.setItem('readerFontSize', String(taillePolice));
        appliquerBoutonsPreferences();
    } catch(_) {}
}
function appliquerBoutonsPreferences() {
    document.body.classList.remove('theme-sepia', 'theme-sombre');
    if (themeActuel !== 'clair') document.body.classList.add(`theme-${themeActuel}`);
    document.querySelectorAll('.theme-btn').forEach(b => b.classList.remove('actif'));
    document.querySelector(`.theme-btn.${themeActuel}`)?.classList.add('actif');
    const t = document.getElementById('taillePoliceTxt');
    if (t) t.textContent = `${taillePolice}px`;
}
function changerTheme(theme, event) {
    if (event) event.stopPropagation();
    themeActuel = normaliserTheme(theme);
    appliquerBoutonsPreferences();
    appliquerThemeDocument();
    sauvegarderPreferences();
}
function appliquerThemeDocument() {
    const canvas = document.getElementById('pdfCanvas');
    if (canvas) canvas.style.filter = themeActuel === 'sombre' ? 'invert(90%) hue-rotate(180deg) brightness(1.1) contrast(.9)' : themeActuel === 'sepia' ? 'sepia(.8) contrast(.9) brightness(1.08)' : 'none';
    const host = document.getElementById('docxHost');
    if (host) host.dataset.theme = themeActuel;
    if (epubRendition) { registerEpubThemes(); epubRendition.themes.select(themeActuel); epubRendition.themes.fontSize(`${taillePolice}px`); }
}
async function changerTaille(direction) {
    taillePolice = clamp(taillePolice + direction, 10, 36);
    localStorage.setItem('readerFontSize', String(taillePolice));
    appliquerBoutonsPreferences();
    if (formatActuel === 'PDF') await afficherPagePDF(pageActuelle);
    else if (formatActuel === 'TXT') { pageActuelle = 1; await chargerTXT(); }
    else if (formatActuel === 'WORD') appliquerTailleDOCX();
    else if (epubRendition) epubRendition.themes.fontSize(`${taillePolice}px`);
    sauvegarderPreferences();
}
function changerCouleur(couleur, event) {
    if (event) event.stopPropagation();
    couleurMarqueur = couleur;
    document.querySelectorAll('.couleur-btn').forEach(b => b.classList.remove('actif'));
    event?.target?.classList.add('actif');
}
async function sauvegarderPreferences() {
    localStorage.setItem('readerTheme', themeActuel);
    localStorage.setItem('readerFontSize', String(taillePolice));
    if (!userId) return;
    try { await fetchAvecAuth(`/api/preferences/user/${userId}?theme=${themeActuel}&fontSize=${taillePolice}`, { method: 'PUT' }); } catch(_) {}
}

// Sélection, annotations, dictionnaire
async function chargerAnnotations() {
    if (!userId) return;
    try {
        const res = await fetchAvecAuth(`/api/annotations/book/${bookId}/user/${userId}`);
        annotations = res.ok ? await res.json() : [];
    } catch (_) { annotations = []; }
}
function configurerSelectionTexte() {
    pageDocument().addEventListener('mouseup', () => {
        const texte = window.getSelection().toString().trim();
        if (texte) afficherMenuSelection(texte);
    });
}
function afficherMenuSelection(texte) {
    fermerMenuSelection(false);
    const div = document.createElement('div');
    div.id = 'menuSelection';
    div.className = 'menu-selection';
    div.innerHTML = `<p class="menu-selection-title">Action</p><p class="menu-selection-text">"${escapeHtml(texte.slice(0, 80))}"</p><div class="menu-selection-actions"><button id="btnMarkSel">🖊️ Marquer</button><button id="btnDicSel">📖 Dictionnaire</button><button onclick="fermerMenuSelection();">✕</button></div>`;
    document.body.appendChild(div);
    document.getElementById('btnMarkSel').onclick = () => { marquerTexte(texte); fermerMenuSelection(); };
    document.getElementById('btnDicSel').onclick = () => { ouvrirDictionnaire(texte.split(/\s+/)[0]); fermerMenuSelection(false); };
    document.getElementById('overlay').classList.add('visible');
}
function fermerMenuSelection(clearOverlay = true) {
    document.getElementById('menuSelection')?.remove();
    if (clearOverlay) document.getElementById('overlay').classList.remove('visible');
}
async function marquerTexte(texte) {
    const selection = window.getSelection();
    if (formatActuel === 'PDF') dessinerSurlignagePDFDepuisSelection();
    else if (selection.rangeCount) {
        const range = selection.getRangeAt(0);
        const mark = document.createElement('mark');
        mark.className = 'selection-highlight';
        mark.style.backgroundColor = getCouleurHex(couleurMarqueur);
        try { mark.appendChild(range.extractContents()); range.insertNode(mark); } catch (_) {}
    }
    selection.removeAllRanges();
    await creerAnnotation(texte);
}
async function creerAnnotation(texte) {
    if (!userId || !texte) return;
    try {
        const res = await fetchAvecAuth(`/api/annotations/book/${bookId}/user/${userId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ highlightedText: texte, color: couleurMarqueur, pageNumber: Math.round(pageActuelle) })
        });
        if (res.ok) annotations.push(await res.json());
    } catch (e) { console.error(e); }
}
function appliquerAnnotations() {
    if (!annotations.length || formatActuel === 'PDF') return;
    const list = annotations.filter(a => a.pageNumber === Math.round(pageActuelle));
    if (!list.length) return;
    let html = pageDocument().innerHTML;
    for (const a of list.sort((x,y) => y.highlightedText.length - x.highlightedText.length)) {
        const text = escapeHtml(a.highlightedText);
        html = html.replace(new RegExp(escapeRegExp(text), 'g'), `<mark style="background-color:${getCouleurHex(a.color)}">${text}</mark>`);
    }
    pageDocument().innerHTML = html;
}

async function ouvrirDictionnaire(mot) {
    const motNettoye = nettoyerMotDictionnaire(mot);
    if (!motNettoye) return;

    let d = dictionaryCache[motNettoye];
    if (!definitionValide(d)) {
        // Plus d'appel direct à dictionaryapi.dev depuis le navigateur.
        // Cela supprime les erreurs ERR_INTERNET_DISCONNECTED/404 dans la console.
        d = chercherDefinitionLocale(motNettoye) || await chercherDefinitionServeur(motNettoye);
    }

    if (!definitionValide(d)) {
        d = definitionGenerique(motNettoye);
    }

    d.word = nettoyerMotDictionnaire(d.word || motNettoye) || motNettoye;
    d.language = d.language || 'fr';
    d.synonyms = limiterSynonymes(d.synonyms);

    dictionaryCache[motNettoye] = d;
    localStorage.setItem('dictionaryCache', JSON.stringify(dictionaryCache));

    // On ne sauvegarde pas les faux résultats “introuvable”.
    if (definitionValide(d) && !estDefinitionIntrouvable(d)) {
        await sauvegarderMotDictionnaire(d);
    }

    afficherPopupDictionnaire(d, motNettoye);
}

function nettoyerMotDictionnaire(mot) {
    return String(mot || '')
        .replace(/[’']/g, '-')
        .replace(/[^a-zA-ZÀ-ÿ\-]/g, '')
        .replace(/^-+|-+$/g, '')
        .toLowerCase()
        .trim();
}

function definitionValide(d) {
    return !!(d && typeof d === 'object' && String(d.definition || '').trim());
}

function estDefinitionIntrouvable(d) {
    const txt = `${d.shortDefinition || ''} ${d.definition || ''}`.toLowerCase();
    return txt.includes('définition introuvable') || txt.includes('aucune définition trouvée');
}

function afficherPopupDictionnaire(d, mot) {
    document.getElementById('dicMot').textContent = d.word || mot;
    document.getElementById('dicDefinitionCourte').textContent = d.shortDefinition || 'Définition';
    document.getElementById('dicDefinition').textContent = d.definition || 'Définition non disponible.';
    const synEl = document.getElementById('dicSynonymes');
    synEl.innerHTML = '';

    const synonymes = limiterSynonymes(d.synonyms).split(',').map(s => s.trim()).filter(Boolean);
    if (!synonymes.length) {
        const span = document.createElement('span');
        span.className = 'synonyme synonyme-vide';
        span.textContent = 'Aucun synonyme enregistré';
        synEl.appendChild(span);
    } else {
        synonymes.forEach(s => {
            const span = document.createElement('span');
            span.className = 'synonyme';
            span.textContent = s;
            span.onclick = () => ouvrirDictionnaire(s);
            synEl.appendChild(span);
        });
    }

    document.getElementById('popupDictionnaire').classList.add('visible');
    document.getElementById('overlay').classList.add('visible');
}

function chercherDefinitionLocale(mot) {
    const dico = window.BEST_READER_DICTIONARY_FR || {};
    const sansAccent = mot.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    const candidats = [mot, sansAccent, ...formesProbablesFrancais(mot), ...formesProbablesFrancais(sansAccent)];
    for (const candidat of [...new Set(candidats)]) {
        const d = dico[candidat];
        if (d && d.definition) {
            return {
                word: d.word || candidat,
                language: d.language || 'fr',
                shortDefinition: d.shortDefinition || 'Définition locale',
                definition: d.definition,
                synonyms: limiterSynonymes(d.synonyms || '')
            };
        }
    }
    return null;
}

function formesProbablesFrancais(mot) {
    const formes = [];
    if (mot.endsWith('s') && mot.length > 3) formes.push(mot.slice(0, -1));
    if (mot.endsWith('es') && mot.length > 4) formes.push(mot.slice(0, -2));
    if (mot.endsWith('ant') && mot.length > 5) formes.push(mot.slice(0, -3) + 'er');
    if (mot.endsWith('ant') && mot.length > 5) formes.push(mot.slice(0, -3) + 're');
    return formes.filter(Boolean);
}

async function chercherDefinitionServeur(mot) {
    const langues = langueProbable(mot) === 'en' ? ['en', 'fr'] : ['fr', 'en'];
    for (const lang of langues) {
        try {
            const res = await fetchAvecAuth('/api/dictionary/search?word=' + encodeURIComponent(mot) + '&language=' + lang, {
                headers: { 'Accept': 'application/json' }
            });
            if (!res.ok) continue;
            const d = await res.json();
            if (definitionValide(d) && !estDefinitionIntrouvable(d)) {
                d.language = d.language || lang;
                d.synonyms = limiterSynonymes(d.synonyms);
                return d;
            }
        } catch (err) {
            console.warn('Dictionnaire serveur indisponible:', err?.message || err);
        }
    }
    return null;
}

function langueProbable(mot) {
    const m = String(mot || '').toLowerCase();
    if (/[àâäçéèêëîïôöùûüÿœæ]/.test(m)) return 'fr';
    const motsFrancaisCourants = new Set(['dans','avec','pour','sans','maintenant','bouche','riant','crachant','livre','page','texte','mot','auteur','document']);
    return motsFrancaisCourants.has(m) ? 'fr' : 'en';
}

async function sauvegarderMotDictionnaire(d) {
    try {
        const res = await fetchAvecAuth('/api/dictionary/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({
                word: d.word,
                language: d.language || langueProbable(d.word || ''),
                shortDefinition: d.shortDefinition || '',
                definition: d.definition || '',
                synonyms: limiterSynonymes(d.synonyms || '')
            })
        });
        if (!res.ok) console.warn('Sauvegarde dictionnaire ignorée:', res.status);
    } catch (err) {
        console.warn('Sauvegarde dictionnaire indisponible:', err?.message || err);
    }
}

function definitionGenerique(mot) {
    return {
        word: mot,
        language: 'fr',
        shortDefinition: 'Mot non trouvé',
        definition: `Aucune définition précise n’a été trouvée pour « ${mot} ». Connecte-toi à Internet une fois ou ajoute ce mot dans la base pour l’utiliser hors connexion ensuite.`,
        synonyms: ''
    };
}

function limiterSynonymes(s) {
    const vus = new Set();
    return String(s || '')
        .split(',')
        .map(x => x.trim())
        .filter(x => x && !vus.has(x.toLowerCase()) && vus.add(x.toLowerCase()))
        .slice(0, 2)
        .join(',');
}

function chargerDictionnaireLocal() {
    try { dictionaryCache = JSON.parse(localStorage.getItem('dictionaryCache') || '{}'); }
    catch(_) { dictionaryCache = {}; }
}

function fermerDictionnaire() {
    document.getElementById('popupDictionnaire').classList.remove('visible');
    document.getElementById('overlay').classList.remove('visible');
}

window.ouvrirDictionnaire = ouvrirDictionnaire;
window.chercherDefinitionServeur = chercherDefinitionServeur;
window.chercherDefinitionLocale = chercherDefinitionLocale;
window.fermerDictionnaire = fermerDictionnaire;

function configurerNavigationParClicPage() {
    const zone = contenuLecture();
    if (!zone) return;
    zone.addEventListener('click', async (e) => {
        if (e.target.closest('button, a, input, textarea, select, .menu-selection, .popup-dictionnaire, #menuDeroulant, #parametresPanel')) return;
        const selection = window.getSelection();
        if (selection && String(selection).trim()) return;
        if (!e.target.closest('#pageDocument')) return;
        const rect = zone.getBoundingClientRect();
        const x = e.clientX - rect.left;
        await changerPage(x > rect.width / 2 ? 1 : -1);
    });
}

// Menus
function toggleMenu(event) {
    event?.stopPropagation();
    const menu = document.getElementById('menuDeroulant');
    const open = menu.classList.toggle('ouvert');
    document.getElementById('overlay').classList.toggle('visible', open);
}
function fermerTout() {
    document.getElementById('menuDeroulant')?.classList.remove('ouvert');
    document.getElementById('menuHamburgerReader')?.classList.remove('ouvert');
    document.getElementById('popupDictionnaire')?.classList.remove('visible');
    document.getElementById('menuSelection')?.remove();
    document.getElementById('overlay')?.classList.remove('visible');
}
function configurerFermetureMenus() {
    document.addEventListener('click', (e) => {
        const menu = document.getElementById('menuDeroulant');
        const hamburger = document.getElementById('menuHamburgerReader');
        const popup = document.getElementById('popupDictionnaire');
        if (e.target.closest('.menu-deroulant,.menu-hamburger-reader,.popup-dictionnaire,.menu-selection,.btn-hamburger')) return;
        if (menu?.classList.contains('ouvert') || hamburger?.classList.contains('ouvert')) fermerTout();
        if (popup?.classList.contains('visible') && !e.target.closest('.popup-dictionnaire')) fermerDictionnaire();
    });
}
function ouvrirAnnotations() { window.location.href = `/Annotations?bookId=${bookId}`; }
function ouvrirNotes() { window.location.href = `/Notes?bookId=${bookId}`; }

// Utils
function getCouleurHex(c) { return { YELLOW:'#FFE066', GREEN:'#85E89D', BLUE:'#79B8FF', PINK:'#F9A8D4', ORANGE:'#FDB462', RED:'#de1313b7', PURPLE:'#D8B4FE', BROWN:'#D6A16A' }[c] || '#FFE066'; }
function escapeHtml(t) { return String(t ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m])); }
function escapeRegExp(t) { return String(t).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
function clamp(n,min,max){ return Math.max(min, Math.min(max, n)); }
