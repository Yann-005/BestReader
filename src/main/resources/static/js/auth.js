// ==============================
// Connexion
// ==============================
const formulaireConnexion = document.getElementById('formulaireConnexion');

if (formulaireConnexion) {
    formulaireConnexion.addEventListener('submit', async function(e) {
        e.preventDefault();

        const identifiant = document.getElementById('identifiant').value;
        const motDePasse = document.getElementById('motDePasse').value;
        const messageErreur = document.getElementById('messageErreur');

        if (messageErreur) messageErreur.style.display = 'none';

        try {
            const reponse = await fetch('/auth/Login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    identifier: identifiant,
                    password: motDePasse
                })
            });

            if (reponse.ok) {
                const donnees = await reponse.json();
                localStorage.setItem('token', donnees.token);
                localStorage.setItem('userId', donnees.userId);
                localStorage.setItem('nomUtilisateur', donnees.username);
                window.location.href = '/Library';
            } else {
                if (messageErreur) messageErreur.style.display = 'block';
            }
        } catch (erreur) {
            if (messageErreur) messageErreur.style.display = 'block';
        }
    });
}

// ==============================
// Inscription
// ==============================
const formulaireInscription = document.getElementById('formulaireInscription');

if (formulaireInscription) {
    formulaireInscription.addEventListener('submit', async function(e) {
        e.preventDefault();

        const nomComplet = document.getElementById('nomComplet').value;
        const nomUtilisateur = document.getElementById('nomUtilisateur').value;
        const email = document.getElementById('email').value;
        const motDePasse = document.getElementById('motDePasse').value;
        const confirmerMotDePasse = document.getElementById('confirmerMotDePasse').value;
        const messageErreur = document.getElementById('messageErreur');
        const messageSucces = document.getElementById('messageSucces');

        messageErreur.style.display = 'none';
        messageSucces.style.display = 'none';

        if (motDePasse !== confirmerMotDePasse) {
            messageErreur.textContent = 'Les mots de passe ne correspondent pas.';
            messageErreur.style.display = 'block';
            return;
        }

        try {
            const reponse = await fetch('/auth/Register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    fullName: nomComplet,
                    username: nomUtilisateur,
                    email: email,
                    password: motDePasse
                })
            });

            if (reponse.ok) {
                const donnees = await reponse.json();
                localStorage.setItem('token', donnees.token);
                localStorage.setItem('userId', donnees.userId);
                localStorage.setItem('nomUtilisateur', donnees.username);
                window.location.href = '/Library';
            } else {
                const erreur = await reponse.json();
                messageErreur.textContent = erreur.message || 'Une erreur est survenue.';
                messageErreur.style.display = 'block';
            }
        } catch (erreur) {
            messageErreur.textContent = 'Une erreur est survenue.';
            messageErreur.style.display = 'block';
        }
    });
}

// ==============================
// Indicateur force mot de passe
// ==============================
const champMotDePasse = document.getElementById('motDePasse');
if (champMotDePasse) {
    champMotDePasse.addEventListener('input', function() {
        const mdp = this.value;
        let force = 0;
        if (mdp.length >= 8) force++;
        if (/[A-Z]/.test(mdp)) force++;
        if (/[0-9]/.test(mdp)) force++;
        if (/[^A-Za-z0-9]/.test(mdp)) force++;

        const barre = document.getElementById('forceMdp');
        const texte = document.getElementById('texteForceMdp');
        if (!barre) return;

        if (force <= 1) {
            barre.className = 'force-mdp faible';
            texte.textContent = 'Mot de passe faible';
        } else if (force === 2 || force === 3) {
            barre.className = 'force-mdp moyen';
            texte.textContent = 'Mot de passe moyen';
        } else {
            barre.className = 'force-mdp fort';
            texte.textContent = 'Mot de passe fort';
        }
    });
}