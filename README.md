# Projet **Chadow**

#### *Date : 2024*
#### *Niveau : M1 S2*
#### *Membres : Hakim AOUDIA, Sylvain TRAN*

## Présentation

**Chadow** est un protocole au dessus de TCP pour un serveur de discussion (type IRC) qui ajoute le partage de fichiers.
L’originalité du protocole est le **mode caché** : un client peut télécharger un fichier sans révéler son adresse IP au client qui le fournit, en passant par un ou plusieurs autres clients agissant comme **proxys**.

- **Mode ouvert :** téléchargement direct depuis tous les clients possédant le fichier.  
- **Mode caché :** téléchargement indirect en chaînant un ou plusieurs proxys (A → C → B).

---

## Capture d'écran
<p align="center">
  <img src="https://github.com/user-attachments/assets/510cb061-9e42-4268-adc0-9386c1908951" alt="Screenshot from 2025-05-25 23-12-08" width="600"/>
</p>

---

## Fonctionnalités principales

- Chat public et messages privés.
- Annonce / retrait de fichiers à partager.
- Liste des utilisateurs et des fichiers disponibles.
- Téléchargement **ouvert** (multi-source) ou **caché** (avec proxys).
- Refus ou acceptation automatique d’un partage.
- Plusieurs clients peuvent être lancés sur la **même machine**.


---

## Construction du projet

> **Prérequis :** JDK 21+ et **Ant**.

```bash
# Dans la racine du projet
ant        # ou ant jar
```
Le script démarre 1 serveur et 3 clients.

| Action                  | Commande                    |
| ----------------------- | --------------------------- |
| Message global          | `3  <message>`              |
| Message privé           | `5  <login_dest> <message>` |
| Partager                | `8  <file1> [file2 …]`      |
| Retirer                 | `9  <file1> [file2 …]`      |
| Toujours accepter       | `50 <file1>`                |
| Toujours refuser        | `51 <file1>`                |
| Liste utilisateurs      | `10`                        |
| Liste fichiers dispo    | `12`                        |
| Liste fichiers partagés | `28`                        |
| Télécharger (ouvert)    | `14 <fichier>`              |
| Télécharger (caché)     | `19 <fichier>`              |
