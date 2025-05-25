package fr.uge.tools;

import java.util.Set;

public class Codes {

    /* 0. Connexion */
    public static final byte TRY_CONNECTION = 0;
    public static final byte PSEUDO_VALIDATED = 1;
    public static final byte PSEUDO_REFUSED = 2;
    
    /* 1. Envoi de message */
    /* 1.1 À tous les clients */
    public static final byte MSG_TO_ALL = 3;
    public static final byte MSG_RECEIVED = 4;
    
    /* 1.2. À un seul autre client */
    public static final byte MSG_TO_ONE = 5;
    public static final byte PSEUDO_NOT_FOUND = 6;
    public static final byte PSEUDO_FOUND = 7;

    /* 2. Annonce au serveur */
    /* 2.1 Proposition de fichiers */
    public static final byte SHARE_FILES = 8;
    /* 2.2 L'arrêt de partage de fichiers */
    public static final byte STOP_SHARE_FILES = 9;
    
    /* Changer l'état de partage de fichiers */
    public static final byte CHANGE_SHARING_STATUS_TO_ALWAYS_ACCEPT = 50;
    public static final byte CHANGE_SHARING_STATUS_TO_ALWAYS_REFUSE = 51;
    
    /* 3 Demande au serveur */
    /* 3.1 Liste pseudos */
    public static final byte REQUEST_LIST_AVAILABLE_PSEUDOS = 10;
    public static final byte AVAILABLE_PSEUDOS = 11;

    /* 3.2 Liste fichiers */
    /* 3.2.1 Partagés par tout les clients */
    public static final byte REQUEST_LIST_AVAILABLE_FILES = 12;
    public static final byte AVAILABLE_FILES = 13;
    /* 3.2.2 Partagés par lui même */
    public static final byte REQUEST_LIST_FILES_SHARED_BY_ME = 28;
//    public static final byte FILES_SHARED_BY_ME = 29;

    /* 3.3 Télécharger fichier */
    /* 3.3.1 Mode ouvert */
    public static final byte REQUEST_LIST_SHARER = 14;
    public static final byte LIST_SHARER = 15;
    public static final byte FIRST_OPEN_REQUEST = 30;
    public static final byte OPEN_REQUEST = 16;
    public static final byte OPEN_REQUEST_DENIED = 17;
    public static final byte OPEN_RESPONSE = 18;
    
    /* 3.3.2 Mode caché */
    public static final byte REQUEST_LIST_PROXY = 19;
    public static final byte PROXY = 20;
    public static final byte PROXY_OK = 21;
    public static final byte TERMINAL = 22;
    public static final byte TERMINAL_OK = 23;
    public static final byte LIST_PROXY = 24;
    public static final byte FIRST_HIDEN_REQUEST = 31;
    public static final byte HIDEN_REQUEST = 25;
    public static final byte HIDEN_REQUEST_DENIED = 26;
    public static final byte HIDEN_RESPONSE = 27;

//    public static final Set<Byte> codeValid = Set.of(TRY_CONNECTION, MSG_TO_ALL, MSG_TO_ONE,
//            SHARE_FILES, STOP_SHARE_FILES,
//            REQUEST_LIST_AVAILABLE_PSEUDOS, REQUEST_LIST_AVAILABLE_FILES, REQUEST_LIST_FILES_SHARED_BY_ME);

    public static byte responseOfRequestList(byte code) {
        return switch(code) {
          case REQUEST_LIST_AVAILABLE_PSEUDOS -> AVAILABLE_PSEUDOS;
          case REQUEST_LIST_AVAILABLE_FILES -> AVAILABLE_FILES;
          case REQUEST_LIST_SHARER -> LIST_SHARER;
          case REQUEST_LIST_PROXY -> LIST_PROXY;
          default->throw new AssertionError("this code don't have response");
        };
    }
}