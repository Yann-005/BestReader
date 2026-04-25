// ══════════════════════════════════════════════════════════════
//  annotations.js — BestReader
// ══════════════════════════════════════════════════════════════

const token  = localStorage.getItem('token');
const userId = localStorage.getItem('userId');
let annotations  = [];
let filtreActuel = '';

// ── Initialisation ────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    verifierConnexion();
    await chargerAnnotations();
    afficherAnnotations();
});

// ── Vérifier la connexion ─────────────────────────────────────
function verifierConnexion() {
    if (!token || !userId) {
        window.location.href = '/auth/Login';
    }
}

// ── Charger toutes les annotations de l'utilisateur ──────────
async function chargerAnnotations() {
    try {
        const res = await fetch(`/api/books/user/${userId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (!res.ok) return;

        const livres = await res.json();
        const toutes = [];

        for (const livre of livres) {
            try {
                const resA = await fetch(
                    `/api/annotations/book/${livre.id}/user/${userId}`,
                    { headers: { 'Authorization': `Bearer ${token}` } }
                );
                if (resA.ok) {
                    const annots = await resA.json();
                    annots.forEach(a => {
                        a.bookTitle = livre.title;
                        a.bookId    = livre.id;
                    });
                    toutes.push(...annots);
                }
            } catch (e) {
                console.error('Erreur livre', livre.id, e);
            }
        }

        // Trier par date décroissante
        annotations = toutes.sort(
            (a, b) => new Date(b.createdAt) - new Date(a.createdAt)
        );
    } catch (e) {
        console.error('Erreur chargement annotations:', e);
    }
}

// ── Afficher les annotations ──────────────────────────────────
function afficherAnnotations() {
    const liste  = document.getElementById('listeAnnotations');
    const vide   = document.getElementById('aucuneAnnotation');
    const compteur = document.getElementById('compteurAnnotations');
    liste.innerHTML = '';

    const filtrees = filtreActuel
        ? annotations.filter(a => a.color === filtreActuel)
        : annotations;

    // Mise à jour du compteur
    compteur.textContent = filtrees.length === 0
        ? 'Aucune annotation'
        : `${filtrees.length} annotation${filtrees.length > 1 ? 's' : ''}`;

    if (filtrees.length === 0) {
        vide.style.display = 'block';
        return;
    }

    vide.style.display = 'none';
    filtrees.forEach(a => liste.appendChild(creerCarteAnnotation(a)));
}

// ── Créer une carte annotation ────────────────────────────────
function creerCarteAnnotation(annotation) {
    const couleurMap = {
        YELLOW: '#FFE066', GREEN:  '#85E89D', BLUE:   '#79B8FF',
        PINK:   '#F9A8D4', ORANGE: '#FDB462', RED:    '#e05252',
        PURPLE: '#B392F0', BROWN:  '#D4A574'
    };

    const couleurHex = couleurMap[annotation.color] || '#FFE066';

    const date = annotation.createdAt
        ? new Date(annotation.createdAt).toLocaleDateString('fr-FR', {
              day: 'numeric', month: 'long', year: 'numeric',
              hour: '2-digit', minute: '2-digit'
          })
        : '';

    const carte = document.createElement('div');
    carte.className = `carte-annotation ${annotation.color}`;
    carte.innerHTML = `
        <div class="entete-annotation">
            <span class="badge-couleur" style="background:${couleurHex};"></span>
            <div class="info-livre-annotation">
                <div class="titre-livre-annotation">📖 ${escapeHtml(annotation.bookTitle || 'Livre inconnu')}</div>
                <div class="meta-annotation">Page ${annotation.pageNumber || '?'} ${date ? '• ' + date : ''}</div>
            </div>
        </div>
        <div class="texte-annotation">${escapeHtml(annotation.highlightedText)}</div>
        <div class="actions-annotation">
            <button class="btn-aller-livre"
                    onclick="allerAuLivre(${annotation.bookId}, ${annotation.pageNumber})">
                📖 Aller au livre
            </button>
            <button class="btn-supprimer-annotation"
                    onclick="supprimerAnnotation(${annotation.id})">
                🗑️ Supprimer
            </button>
        </div>
    `;
    return carte;
}

// ── Supprimer une annotation ──────────────────────────────────
async function supprimerAnnotation(annotationId) {
    if (!confirm('Supprimer cette annotation ?')) return;
    try {
        const res = await fetch(`/api/annotations/${annotationId}/user/${userId}`, {
            method:  'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) {
            annotations = annotations.filter(a => a.id !== annotationId);
            afficherAnnotations();
        }
    } catch (e) {
        console.error('Erreur suppression:', e);
    }
}

// ── Filtrer par couleur ───────────────────────────────────────
function filtrerCouleur(couleur, event) {
    filtreActuel = couleur;

    document.querySelectorAll('.filtre-btn').forEach(btn => {
        btn.classList.remove('actif');
    });
    if (event && event.target) event.target.classList.add('actif');

    afficherAnnotations();
}

// ── Navigation ────────────────────────────────────────────────
function allerAuLivre(bookId, pageNumber) {
    window.location.href = `/Reader/${bookId}?page=${pageNumber}`;
}

function seDeconnecter() {
    localStorage.clear();
    window.location.href = '/auth/Login';
}

// ── Utilitaire ────────────────────────────────────────────────
function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
