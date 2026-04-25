const token = localStorage.getItem('token');
const userId = localStorage.getItem('userId');
const params = new URLSearchParams(window.location.search);
const bookId = params.get('bookId');

// ==============================
// Initialisation
// ==============================
document.addEventListener('DOMContentLoaded', async function() {
    await chargerNotes();
});

// ==============================
// Charger les notes
// ==============================
async function chargerNotes() {
    try {
        const url = bookId
            ? `/api/notes/user/${userId}/book/${bookId}`
            : `/api/notes/user/${userId}`;

        const reponse = await fetch(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (reponse.ok) {
            const notes = await reponse.json();
            afficherNotes(notes);
        }
    } catch (erreur) {
        console.error('Erreur chargement notes:', erreur);
    }
}

// ==============================
// Afficher les notes
// ==============================
function afficherNotes(notes) {
    const liste = document.getElementById('listeNotes');
    const aucune = document.getElementById('aucuneNote');
    liste.innerHTML = '';

    if (notes.length === 0) {
        aucune.style.display = 'block';
        return;
    }

    aucune.style.display = 'none';
    notes.forEach(note => {
        const carte = document.createElement('div');
        carte.className = 'carte-note';
        carte.innerHTML = `
            <div class="entete-note">
                <div class="titre-note">${note.title || 'Sans titre'}</div>
                <div class="actions-note">
                    <button class="btn-note btn-modifier"
                            onclick="modifierNote(${note.id}, '${note.title}', \`${note.content}\`)">
                        ✏️ Modifier
                    </button>
                    <button class="btn-note btn-supprimer"
                            onclick="supprimerNote(${note.id})">
                        🗑️ Supprimer
                    </button>
                </div>
            </div>
            <div class="contenu-note">${note.content}</div>
            <div class="date-note">
                ${new Date(note.createdAt).toLocaleDateString('fr-FR', {
                    day: 'numeric', month: 'long', year: 'numeric'
                })}
            </div>
        `;
        liste.appendChild(carte);
    });
}

// ==============================
// Ouvrir le formulaire
// ==============================
function ouvrirFormulaire() {
    document.getElementById('titreFormulaire').textContent = 'Nouvelle note';
    document.getElementById('noteId').value = '';
    document.getElementById('noteTitre').value = '';
    document.getElementById('noteContenu').value = '';
    document.getElementById('formulaireNote').style.display = 'block';
    document.getElementById('noteTitre').focus();
}

// ==============================
// Fermer le formulaire
// ==============================
function fermerFormulaire() {
    document.getElementById('formulaireNote').style.display = 'none';
}

// ==============================
// Sauvegarder une note
// ==============================
async function sauvegarderNote() {
    const noteId = document.getElementById('noteId').value;
    const titre = document.getElementById('noteTitre').value;
    const contenu = document.getElementById('noteContenu').value;

    if (!contenu.trim()) {
        alert('Le contenu est obligatoire.');
        return;
    }

    try {
        let reponse;
        if (noteId) {
            reponse = await fetch(`/api/notes/${noteId}/user/${userId}`, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ title: titre, content: contenu })
            });
        } else {
            const url = bookId
                ? `/api/notes/user/${userId}?bookId=${bookId}`
                : `/api/notes/user/${userId}`;

            reponse = await fetch(url, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ title: titre, content: contenu })
            });
        }

        if (reponse.ok) {
            fermerFormulaire();
            await chargerNotes();
        }
    } catch (erreur) {
        console.error('Erreur sauvegarde note:', erreur);
    }
}

// ==============================
// Modifier une note
// ==============================
function modifierNote(id, titre, contenu) {
    document.getElementById('titreFormulaire').textContent = 'Modifier la note';
    document.getElementById('noteId').value = id;
    document.getElementById('noteTitre').value = titre;
    document.getElementById('noteContenu').value = contenu;
    document.getElementById('formulaireNote').style.display = 'block';
    window.scrollTo(0, 0);
}

// ==============================
// Supprimer une note
// ==============================
async function supprimerNote(id) {
    if (!confirm('Voulez-vous vraiment supprimer cette note ?')) return;

    try {
        const reponse = await fetch(`/api/notes/${id}/user/${userId}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (reponse.ok) {
            await chargerNotes();
        }
    } catch (erreur) {
        console.error('Erreur suppression note:', erreur);
    }
}