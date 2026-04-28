// ==============================
// Variables globales
// ==============================
const token = localStorage.getItem('token');
const userId = localStorage.getItem('userId');
const nomUtilisateur = localStorage.getItem('nomUtilisateur');
let livreSelectionne = null;
let ongletActuel = 'bibliotheque';
let triActuel = 'alphabetique';
let pageActuelle = 1;
let dispositionActuelle = 'grille'; 
let recherche = '';

// ==============================
// Initialisation
// ==============================
document.addEventListener('DOMContentLoaded', async function() {
    verifierConnexion();
    afficherProfil();
    await chargerLivres();
    configurerDragAndDrop();
    chargerPreferences();
});

// ==============================
// Vérifier la connexion
// ==============================
function verifierConnexion() {
    if (!token || !userId) {
        window.location.href = '/auth/login';
    }
}

// ==============================
// Afficher le profil
// ==============================
function afficherProfil() {
    const nom = nomUtilisateur || 'Utilisateur';
    document.getElementById('nomUtilisateur').textContent = nom;
    document.getElementById('avatarInitiale').textContent =
        nom.charAt(0).toUpperCase();
}

// ==============================
// Barre de recherche
// ==============================
function rechercherLivres(valeur) {
    recherche = valeur; 
    chargerLivres();    
}

// ==============================
// Charger les livres
// ==============================
async function chargerLivres() {
   try {
        let url;
        
        if (recherche.trim()) {
            url = `/api/books/user/${userId}/search?query=${encodeURIComponent(recherche)}`;
        } else {
                 switch (triActuel) {
                case 'alphabetique': url = `/api/books/user/${userId}/alphabetical`;
                    break;
                case 'recent': url = `/api/books/user/${userId}/last-read`;
                    break;
                case 'upload': url = `/api/books/user/${userId}/recent`;
                    break;
                default:
                    url = `/api/books/user/${userId}`;
            }
        }

        const reponse = await fetch(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (reponse.ok) {
            const livres = await reponse.json();
            afficherLivres(livres);
        }
    } catch (erreur) {
        console.error('Erreur chargement livres:', erreur);
    }
}

// ==============================
// Afficher les livres
// ==============================
function afficherLivres(livres) {
    const grille = document.getElementById('grilleLivres');
    const vide = document.getElementById('bibliothequeVide');
    grille.innerHTML = '';

     if (dispositionActuelle === 'liste') {
        grille.classList.add('liste-livres');
        
    } else {
        grille.classList.remove('liste-livres');
    }

    if (livres.length === 0) {
        vide.style.display = 'block';
        return;
    }

    vide.style.display = 'none';
    livres.forEach(livre => {
        const carte = creerCarteLivre(livre);
        grille.appendChild(carte);
    });
}

// ==============================
// Créer une carte livre
// ==============================
function creerCarteLivre(livre) {
    const icones = { PDF: "📄", WORD: "📝", TXT: "📃", EPUB: "📚" };
    const icone = icones[livre.format] || "📖";
    const progression = Number(livre.progressPercentage ?? livre.progress?.progressPercentage ?? 0);
    const currentPage = Number(livre.currentPage ?? livre.progress?.currentPage ?? 1);
    const cover = livre.coverUrl
        ? `<img class="cover-image" src="${livre.coverUrl}" alt="Couverture">`
        : icone;

    const carte = document.createElement("div");
    carte.className = "carte-livre";
    carte.innerHTML = `
        <div class="couverture">
            ${cover}
            <span class="format-badge">${livre.format}</span>
            <button class="favori-btn" onclick="event.stopPropagation(); basculerFavori(${livre.id})">
                ${livre.favorite ? "⭐" : "☆"}
            </button>
        </div>
        <div class="info-livre">
            <div class="titre-livre">${escapeHtml(livre.title)}</div>
            <div class="auteur-livre">${escapeHtml(livre.author || "Auteur inconnu")}</div>
            <div class="progression-livre"><div class="barre" style="width: ${progression}%"></div></div>
            <div class="pct-progression">${progression}% lu · page ${currentPage}</div>
        </div>
        <div class="actions-livre">
            <button class="action-btn" onclick="lireLivre(${livre.id})">📖 Lire</button>
            <button class="action-btn" onclick="event.stopPropagation(); afficherMenuContextuel(event, ${livre.id})">⋯ Plus</button>
        </div>`;
    return carte;
}

// ==============================
// Lire un livre
// ==============================
function lireLivre(bookId) {
    document.getElementById('menuContextuel').classList.remove('visible');
    window.location.href = `/Reader/${bookId}`;
}

// ==============================
// Basculer favori
// ==============================
async function basculerFavori(bookId) {
    try {
        await fetch(`/api/books/${bookId}/favorite/${userId}`, {
            method: 'PUT',
            headers: { 'Authorization': `Bearer ${token}` }
        });
        await chargerLivres();
    } catch (erreur) {
        console.error('Erreur favori:', erreur);
    }
}

// ==============================
// Toggle favori depuis menu contextuel
// ==============================
async function toggleFavori() {
    if (!livreSelectionne) return;
    await basculerFavori(livreSelectionne);
    document.getElementById('menuContextuel').classList.remove('visible');
}

// ==============================
// Uploader un livre
// ==============================
async function uploaderLivre(event) {
    const fichier = event.target.files[0];
    if (!fichier) return;

    const formData = new FormData();
    formData.append('file', fichier);
    formData.append('title', fichier.name.replace(/\.[^/.]+$/, ''));
    formData.append('author', '');
    formData.append('userId', userId);

    try {
        const reponse = await fetch('/api/books/upload', {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });

        if (reponse.ok) {
            await chargerLivres();
        }
    } catch (erreur) {
        console.error('Erreur upload:', erreur);
    }
}


async function afficherAnnotations(page) {
    const res = await fetch(`/api/annotations/book/${livreSelectionne}/user/${userId}`);
    const annotations = await res.json();
    
    annotations
        .filter(a => a.pageNumber === page)
        .forEach(a => dessinerAnnotation(a));
}



// ==============================
// Changer d'onglet
// ==============================
async function changerOnglet(onglet) {
    ongletActuel = onglet;
    const titres = {
        bibliotheque: 'Ma Bibliothèque',
        recents: 'Récemment lus',
        favoris: 'Mes Favoris',
        notes: 'Mes Notes'
    };

    document.getElementById('titreOnglet').textContent = titres[onglet];
    document.querySelectorAll('.nav-lien').forEach(btn => {
        btn.classList.remove('actif');
    });
    if (event) {
        document.querySelectorAll('.nav-lien').forEach(btn => btn.classList.remove('actif'));
        if (typeof event !== 'undefined' && event?.target) event.target.classList.add('actif');
    }

     if (onglet === 'annotations') {
        window.location.href = '/Annotations';
        return;
    }

    if (onglet === 'notes') {
        window.location.href = '/notes';
        return;
    }

    if (onglet === 'recents') {
        triActuel = 'recent';
        recherche = '';
        document.getElementById('barreRecherche').value = '';
        await chargerLivres();
        return;
    }

    if (onglet === 'bibliotheque') {
        triActuel = 'alphabetique';
    }

    if (onglet === 'favoris') {
        const reponse = await fetch(
            `/api/books/user/${userId}/favorites`,
            { headers: { 'Authorization': `Bearer ${token}` } }
        );
        if (reponse.ok) {
            afficherLivres(await reponse.json());
        }
        return;
    }

    await chargerLivres();
}

// ==============================
// Trier les livres
// ==============================
function trierPar(tri) {
    triActuel = tri;
     recherche = '';
    document.getElementById('barreRecherche').value = '';
    document.querySelectorAll('.btn-tri').forEach(btn => {
        btn.classList.remove('actif');
    });
    if (typeof event !== 'undefined' && event?.target) event.target.classList.add('actif');
    chargerLivres();
}

// ==============================
// Basculer la disposition
// ==============================
async function basculerDisposition() {
    dispositionActuelle = dispositionActuelle === 'grille' ? 'liste' : 'grille';

    const txt = document.getElementById('textDisposition');
    if (txt) txt.textContent = dispositionActuelle === 'grille'
        ? '⊞ Passer en liste'
        : '📋 Passer en grille';

    localStorage.setItem('dispositionLivres', dispositionActuelle);
    await chargerLivres();
}

// ==============================
// Afficher menu contextuel
// ==============================
function afficherMenuContextuel(event, bookId) {
    event.preventDefault(); // Empêche le menu clic-droit du navigateur
    event.stopPropagation();
    
    livreSelectionne = bookId;
    const menu = document.getElementById('menuContextuel');
    
    // 1. Afficher temporairement pour calculer la taille
    menu.style.visibility = "hidden";
    menu.classList.add('visible');
    
    const menuWidth = menu.offsetWidth;
    const menuHeight = menu.offsetHeight;
    const windowWidth = window.innerWidth;
    const windowHeight = window.innerHeight;

    let posX = event.clientX;
    if (posX + menuWidth > windowWidth) {
        posX = windowWidth - menuWidth - 10; 
    }

      let posY = event.clientY;
    if (posY + menuHeight > windowHeight) {
        posY = windowHeight - menuHeight - 10; 
    }

    menu.style.top = `${posY}px`;
    menu.style.left = `${posX}px`;
    menu.style.visibility = "visible";
}

// ==============================
// Retirer de la bibliothèque
// ==============================
async function retirerBibliotheque() {
    if (!livreSelectionne) return;
    const supprimerTout = confirm('Voulez-vous supprimer ce document de la bibliothèque ET de la base/fichier disque ?\n\nOK = suppression complète\nAnnuler = retirer seulement de votre bibliothèque');
    const url = supprimerTout
        ? `/api/books/${livreSelectionne}/user/${userId}/complete`
        : `/api/books/${livreSelectionne}/user/${userId}`;

    try {
        const reponse = await fetch(url, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!reponse.ok) {
            const message = await reponse.text();
            throw new Error(message || `Erreur HTTP ${reponse.status}`);
        }

        document.getElementById('menuContextuel').classList.remove('visible');
        await chargerLivres();
    } catch (erreur) {
        console.error('Erreur suppression:', erreur);
        alert('Suppression impossible : ' + erreur.message);
    }
}

// ==============================
// Partager un livre
// ==============================
async function partagerLivre() {
    if (!livreSelectionne) return;
    try {
        const reponse = await fetch(
            `/api/books/${livreSelectionne}`,
            { headers: { 'Authorization': `Bearer ${token}` } }
        );
        if (reponse.ok) {
            const livre = await reponse.json();
            const lien = `${window.location.origin}/books/share?token=${livre.shareToken}`;
            
            // Menu de partage
            afficherMenuPartage(lien, livre.title);
        }
    } catch (erreur) {
        console.error('Erreur partage:', erreur);
    }
    document.getElementById('menuContextuel').classList.remove('visible');
}

// ==============================
// Menu de partage
// ==============================
function afficherMenuPartage(lien, titre) {
    const menu = document.createElement('div');
    menu.style.cssText = `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        background: white;
        border-radius: 16px;
        box-shadow: 0 20px 60px rgba(0,0,0,0.25);
        padding: 25px;
        z-index: 1001;
        min-width: 350px;
    `;
    
    const whatsappUrl = `https://wa.me/?text=${encodeURIComponent(`Découvrez "${titre}" sur Best Reader: ${lien}`)}`;
    const telegramUrl = `https://t.me/share/url?url=${encodeURIComponent(lien)}&text=${encodeURIComponent(`Découvrez "${titre}" sur Best Reader`)}`;
    
    menu.innerHTML = `
        <h3 style="margin-bottom: 15px; color: #3E1F00;">Partager "${titre}"</h3>
        <div style="margin-bottom: 15px;">
            <p style="font-size: 0.9rem; color: #7D5A4F; margin-bottom: 8px;">Lien :</p>
            <div style="display: flex; gap: 8px;">
                <input type="text" value="${lien}" readonly style="flex: 1; padding: 10px; border: 2px solid #E8D5C4; border-radius: 8px; font-size: 0.85rem;">
                <button onclick="copierLien('${lien}')" style="padding: 10px 15px; background: #3E1F00; color: white; border: none; border-radius: 8px; cursor: pointer;">
                    📋 Copier
                </button>
            </div>
        </div>
        <div style="display: flex; flex-direction: column; gap: 10px;">
            <a href="${whatsappUrl}" target="_blank" style="padding: 12px; background: #25D366; color: white; text-align: center; border-radius: 8px; text-decoration: none; font-weight: 600;">
                💬 WhatsApp
            </a>
            <a href="${telegramUrl}" target="_blank" style="padding: 12px; background: #0088cc; color: white; text-align: center; border-radius: 8px; text-decoration: none; font-weight: 600;">
                ✈️ Telegram
            </a>
            <button onclick="partagerBluetooth('${lien}')" style="padding: 12px; background: #8B4513; color: white; border: none; border-radius: 8px; cursor: pointer; font-weight: 600;">
                📱 Bluetooth
            </button>
            <button onclick="this.parentElement.parentElement.remove();" style="padding: 12px; background: #f0f0f0; color: #333; border: none; border-radius: 8px; cursor: pointer;">
                ✕ Fermer
            </button>
        </div>
    `;
    
    document.body.appendChild(menu);
    
    // Overlay
    const overlay = document.createElement('div');
    overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.3);
        z-index: 1000;
    `;
    overlay.onclick = () => {
        overlay.remove();
        menu.remove();
    };
    document.body.appendChild(overlay);
}

// ==============================
// Copier le lien
// ==============================
function copierLien(lien) {
    navigator.clipboard.writeText(lien).then(() => {
        alert('Lien copié dans le presse-papiers !');
    });
}

// ==============================
// Partager via Bluetooth
// ==============================
function partagerBluetooth(lien) {
    if (navigator.share) {
        navigator.share({
            title: 'Best Reader',
            text: `Découvrez ce livre sur Best Reader: ${lien}`,
            url: lien
        }).catch(err => console.log('Erreur partage:', err));
    } else {
        alert('Le partage Bluetooth n\'est pas disponible sur votre navigateur.');
    }
}

// ==============================
// Voir les annotations d'un livre
// ==============================
function voirAnnotations() {
    if (!livreSelectionne) return;
    window.location.href = `/Annotations?bookId=${livreSelectionne}`;
    document.getElementById('menuContextuel').classList.remove('visible');
}

// ==============================
// Voir les notes d'un livre
// ==============================
function voirNotes() {
    if (!livreSelectionne) return;
    window.location.href = `/notes?bookId=${livreSelectionne}`;
    document.getElementById('menuContextuel').classList.remove('visible');
}

// ==============================
// Déconnexion
// ==============================
function seDeconnecter() {
    localStorage.clear();
    window.location.href = '/auth/Login';
}


// ==============================
// Charger les préférences
// ==============================
function chargerPreferences() {
    const disposition = localStorage.getItem('dispositionLivres');
    if (disposition) {
        dispositionActuelle = disposition;
        const btn = document.getElementById('btnDisposition');
        if (btn) {
            btn.textContent = dispositionActuelle === 'grille' ? '📋 Liste' : '⊞ Grille';
        }
    }
}

// ==============================
// Fermer le menu contextuel
// ==============================

document.addEventListener('click', function() {
    document.getElementById('menuContextuel').classList.remove('visible');
});

// ==============================
// Drag & Drop
// ==============================
function configurerDragAndDrop() {
    const zone = document.getElementById('zoneUpload');
    if (!zone) {
        console.warn("Élément 'zoneUpload' introuvable sur cette page.");
        return;
    }

    zone.addEventListener('dragover', function(e) {
        e.preventDefault();
        zone.style.borderColor = 'var(--brun)';
        zone.style.background = 'var(--ivoire)';
    });

    zone.addEventListener('dragleave', function() {
        zone.style.borderColor = 'var(--bordure)';
        zone.style.background = 'white';
    });

    zone.addEventListener('drop', function(e) {
        e.preventDefault();
        zone.style.borderColor = 'var(--bordure)';
        zone.style.background = 'white';
        const fichier = e.dataTransfer.files[0];
        if (fichier) {
            uploaderLivre({ target: { files: [fichier] } });
        }
    });
}
function escapeHtml(text) {
    return String(text ?? '').replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m]));
}
