
Les instructions pour construire les jars :

1) ouvrir un terminal dans le répertoire du projet
2) tapper 'ant' ou 'ant jar' 
=> les jars serons contruit dans un répertoire jar


Voici la liste des commandes disponibles pour utiliser les clients :

To send a message to all : 3 message
To send a private message to someone : 5 login_dst message

To share files : 8 list_files_share
To unshare files : 9 list_files_unshare
To change the sharing status of files to 'Always Accept' : 50 list_file
To change the sharing status of files to 'Always Refuse' : 51 list_file

To request the list of all logins on the server : 10
To request the list of all files available for download : 12
To request the list of files you shared : 28

To download a file in open mode : 14 filename_you_want_to_download
To download a file in hidden mode : 19 filename_you_want_to_download


Il y a un fichier run.sh qui permet de lancer directement le serveur et 3 clients. Pour lancer le fichier il faut utiliser la commande suivante :
sh run.sh
ou 
./run.sh
