package fr.uge.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import fr.uge.tools.*;
import fr.uge.tools.object.OpcodeReader;
import fr.uge.tools.Reader.ProcessStatus;
import fr.uge.tools.trames.*;
import fr.uge.tools.trames.MsgToOneReader.MsgToOne;
import fr.uge.tools.trames.OpenRequestDeniedReader.OpenRequestDenied;
import fr.uge.tools.trames.OpenRequestReader.OpenRequest;
import fr.uge.tools.trames.OpenResponseReader.OpenResponse;
import fr.uge.tools.trames.ProxyReader.Proxy;
import fr.uge.tools.trames.TryConnectionReader.TryConnection;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.ListReader.X;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;
import fr.uge.tools.object.PairListeningAddressTokenReader.PairListeningAddressToken;
import fr.uge.tools.trames.AvailableFilesReader.AvailableFiles;
import fr.uge.tools.trames.AvailablePseudosReader.AvailablePseudos;
import fr.uge.tools.trames.HidenRequestDeniedReader.HidenRequestDenied;
import fr.uge.tools.trames.HidenRequestReader.HidenRequest;
import fr.uge.tools.trames.HidenResponseReader.HidenResponse;
import fr.uge.tools.trames.MsgReceivedReader.MsgReceived;
import fr.uge.tools.trames.PseudoNotFoundReader.PseudoNotFound;
import fr.uge.tools.trames.TokenReader.Token;

public class ClientChadow {

  private final HashMap<FILE, FileInformation> mapFilesToDownload = new HashMap<>();
  private final ArrayDeque<FILE> queueFile = new ArrayDeque<>();
  private final HashMap<InetSocketAddress, Context> mapIsaContext = new HashMap<>();

  private final HashMap<FILE, String> mapFilePathname = new HashMap<>();
  
  private final HashMap<Integer, PairListeningAddressToken> routingTable = new HashMap<>();
  private final Set<Integer> terminalTokens = new HashSet<>();
  private final HashMap<Integer, Context> mapTokenContext = new HashMap<>();
  private final Set<Integer> initialTokens = new HashSet<>();
  
  
  
  private void sendTrameByIsa(InetSocketAddress isa, ByteBuffer trame) throws IOException {
    var context = mapIsaContext.get(isa);
    if (context == null) {
      var sc = SocketChannel.open();
      sc.configureBlocking(false);
      var key = sc.register(selector, SelectionKey.OP_CONNECT);
      context = new Context(this, key);
      key.attach(context);
      sc.connect(isa);
      mapIsaContext.put(isa, context);
      context.queue.add(trame);
      context.processOut();
    }
    else {
     context.queueBb(trame);
    }
  }

  
  private OptionalInt getTokenSrc(int tokenDst) {
    for (var entry : routingTable.entrySet()) {
      var token = entry.getKey();
      var pair = entry.getValue();
      
      if (pair.token() == tokenDst) {
        return OptionalInt.of(token);
      }
    }
    return OptionalInt.empty();
  }
  
  
  static private class Context {
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private boolean closed = false;
    private final ClientChadow client;
    
    
    private OpcodeReader opcodeReader = new OpcodeReader();
    
    private enum State {
      WAITING_FOR_OPCODE, DONE
    };

    private State state = State.WAITING_FOR_OPCODE;
    private byte opcode;


    private enum AuthenticationState {
      ACCEPTED, REFUSED
    };

    private AuthenticationState authState = AuthenticationState.REFUSED;

    private boolean isLogin() {
      return authState == AuthenticationState.ACCEPTED;
    }

    
    
    

    private Context(ClientChadow client, SelectionKey key) {
      this.key = key;
      this.sc = (SocketChannel) key.channel();
      this.client = client;
    }

    public boolean statusIsDone(ProcessStatus status) {
      return switch (status) {
        case DONE -> true;
        case REFILL -> false;
        case ERROR -> {
          closed = true;
          yield false;
          }
      };
    }

    /**
     * Process the content of bufferIn
     *
     * The convention is that bufferIn is in write-mode before the call to process
     * and after the call
     * @throws IOException
     *
     */
    private void processIn() throws IOException {
      for (;;) {
        if (state == State.WAITING_FOR_OPCODE) {
          var status = opcodeReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          
          opcode = opcodeReader.get();
          state = State.DONE;
          opcodeReader.reset();
        }
        
        
        ProcessStatus status;
        TrameReader trameReader = new TrameReader(opcode);
        
        // Get trame reader
        trameReader.reset();
        status = trameReader.process(bufferIn); // bufferIn is never used in process
        if (!statusIsDone(status)) { return; } // statusIsDone is always true here

        
        switch(opcode) {
        case Codes.PSEUDO_VALIDATED:
          System.out.println("Welcome " + client.login + " !");
          authState = AuthenticationState.ACCEPTED;
          state = State.WAITING_FOR_OPCODE;
          client.showMenu();
          break;

        case Codes.PSEUDO_REFUSED:
          System.out.println("Pseudo already used ! Choose another one");
          state = State.WAITING_FOR_OPCODE;
          break;

        case Codes.MSG_RECEIVED:
          // Get trame reader
          MsgReceivedReader msgReceivedReader = (MsgReceivedReader) trameReader.get();
          
          // Get trame record
          status = msgReceivedReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          MsgReceived msgReceived = msgReceivedReader.get();
          
          System.out.println(msgReceived.pseudoSrc() + " : " + msgReceived.message());

          state = State.WAITING_FOR_OPCODE;
          msgReceivedReader.reset();
          break;

        case Codes.PSEUDO_NOT_FOUND:
          // Get trame reader
          PseudoNotFoundReader pseudoNotFoundReader = (PseudoNotFoundReader) trameReader.get();
          
          // Get trame record
          status = pseudoNotFoundReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          PseudoNotFound pseudoNotFound = pseudoNotFoundReader.get();

          System.out.println(pseudoNotFound.pseudoDst() + " was not found !");

          state = State.WAITING_FOR_OPCODE;
          pseudoNotFoundReader.reset();
          break;

          
        case Codes.PSEUDO_FOUND:
          System.out.println("Message well sent !");
          state = State.WAITING_FOR_OPCODE;
          break;

          
        case Codes.AVAILABLE_PSEUDOS:
          // Get trame reader
          AvailablePseudosReader availablePseudosReader = (AvailablePseudosReader) trameReader.get();
          
          // Get trame record
          status = availablePseudosReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          AvailablePseudos availablePseudos = availablePseudosReader.get();
          
          var listPseudos = availablePseudos.pseudos();
          var concatPseudos = listPseudos.stream().collect(Collectors.joining(", "));
          System.out.println("All logins on the server : " + concatPseudos);
          
          state = State.WAITING_FOR_OPCODE;
          availablePseudosReader.reset();
          break;

          
        case Codes.AVAILABLE_FILES:
          // Get trame reader
          AvailableFilesReader availableFilesReader = (AvailableFilesReader) trameReader.get();
          
          // Get trame record
          status = availableFilesReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          AvailableFiles availableFiles = availableFilesReader.get();
          
  
          var listAvailableFiles = availableFiles.files();
          var listFilesName = listAvailableFiles.stream().map(FILE::filename).toList();
          
          var process = client.requestListAvailableFilesState == RequestListAvailableFilesState.PROCESS;
          if (listFilesName.isEmpty()) {
            if (!process) {
              System.out.println("There are no files available for download");
            }
            client.listAvailableFiles.clear();
          }
          else {
            if (!process) {
              System.out.println("All files available for download : ");
              var index = 0;
              for (var filename : listFilesName) {
                System.out.println(index++ + " " + filename);
              }
              System.out.println("(For command 14, you can either enter the index at the beginning or the file name)");
            }
            client.listAvailableFiles = new ArrayList<>(listAvailableFiles);
          }
         
          if (process) {
            try {
              client.sendCommand(client.resendOpcode + " " + client.str);
              client.requestListAvailableFilesState = RequestListAvailableFilesState.DONE;
            } catch (InterruptedException e) {
              return; // thread main
            }
          }
          
          
          state = State.WAITING_FOR_OPCODE;
          availableFilesReader.reset();
          break;
          
                    
        
        case Codes.LIST_SHARER:
        case Codes.LIST_PROXY:
          
          ArrayList<X> list;
          
          if (opcode == Codes.LIST_SHARER) {
            // Get trame reader
            ListSharerReader listSharerReader = (ListSharerReader) trameReader.get();
            
            // Get trame record
            status = listSharerReader.process(bufferIn);
            if (!statusIsDone(status)) { return; }
            list = new ArrayList<X>(listSharerReader.get().listeningAddressList());
            listSharerReader.reset();
          }
          else { // Codes.LIST_PROXY
            // Get trame reader
            ListProxyReader listProxyReader = (ListProxyReader) trameReader.get();
            
            // Get trame record
            status = listProxyReader.process(bufferIn);
            if (!statusIsDone(status)) { return; }
            
            list = new ArrayList<X>(listProxyReader.get().list());
            listProxyReader.reset();
          }
                    
          
          var fileToDownload = client.queueFile.poll();
          
          if (fileToDownload.size() == 0) { // si le fichier est vide, on crée simplement un fichier vide
            var filename = fileToDownload.filename();
            FileInformation.createEmptyFile(filename);
            System.out.println("The file '" + filename + "' is downloaded");
          }
          else {
            client.mapFilesToDownload.put(fileToDownload, new FileInformation(fileToDownload, list.size()));
            var fileInfo = client.mapFilesToDownload.get(fileToDownload);
            
            for (var x: list) {    
              
              InetSocketAddress isa;
              ByteBuffer trame;
              
              if (opcode == Codes.LIST_SHARER) {
                isa = ((LISTENING_ADDRESS) x).asInetSocketAddress();
                var chunkNumber = fileInfo.nextChunkNumber();
                var bb = new OpenRequest(fileToDownload, chunkNumber).asBuffer();
                trame = new TrameBytes(Codes.FIRST_OPEN_REQUEST, bb).asBuffer();
              }
              else { // Codes.LIST_PROXY
                var pair = (PairListeningAddressToken) x;
                isa = pair.address().asInetSocketAddress();
                var token = pair.token();
                client.initialTokens.add(token);
                
                var chunkNumber = fileInfo.nextChunkNumber();
                var bb = new HidenRequest(fileToDownload, chunkNumber, token).asBuffer();
                trame = new TrameBytes(Codes.FIRST_HIDEN_REQUEST, bb).asBuffer();
              }
              
              client.sendTrameByIsa(isa, trame);
            }
          }

          state = State.WAITING_FOR_OPCODE;
          break;
          
          
          
        case Codes.FIRST_HIDEN_REQUEST:
        case Codes.HIDEN_REQUEST:
          // Get trame reader
          HidenRequestReader hidenRequestReader = (HidenRequestReader) trameReader.get();

          // Get trame record
          status = hidenRequestReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          HidenRequest hidenRequest = hidenRequestReader.get();
          
          
          var token = hidenRequest.token();
          var pair = client.routingTable.get(token);
          var inTerminalTokens = client.terminalTokens.contains(token);
          
          if (pair == null && !inTerminalTokens) {
            silentlyClose();
          }
          else if (pair != null) {
            client.mapTokenContext.put(token, this);
            var isa = pair.address().asInetSocketAddress();
            var tokenDst = pair.token();
            var bb = new HidenRequest(hidenRequest.file(), hidenRequest.chunkNumber(), tokenDst).asBuffer();
            var trame = new TrameBytes(opcode, bb).asBuffer();
            client.sendTrameByIsa(isa, trame);
          }
          else if (inTerminalTokens) {
            var file = hidenRequest.file();
            
            var sharingState = client.mapFileSharingState.get(file);
            var firstHR = Codes.FIRST_HIDEN_REQUEST;
            
            if (opcode == firstHR && sharingState == SharingState.CHOICE) {
//              System.out.println("Someone wants to download your file '" + file.filename() + "', do you want to share it ?");
            }
            if (opcode == firstHR && sharingState == SharingState.ALWAYS_REFUSE) {
              var trame = new TrameBytes(Codes.HIDEN_REQUEST_DENIED, hidenRequest.asBuffer()).asBuffer();
              queueBb(trame);
            }
            if (opcode == Codes.HIDEN_REQUEST || (opcode == firstHR && sharingState == SharingState.ALWAYS_ACCEPT)) {
//              System.out.println("TEST");
              var chunkNumber = hidenRequest.chunkNumber();
              var pathname = client.mapFilePathname.get(file);
              var bb = new HidenResponse(file, chunkNumber, FileInformation.getChunkInFile(pathname, chunkNumber), token).asBuffer();
              var trame = new TrameBytes(Codes.HIDEN_RESPONSE, bb).asBuffer();
              queueBb(trame);
            } 
          }
          
          state = State.WAITING_FOR_OPCODE;
          hidenRequestReader.reset();
          break;
          
          
          
        case Codes.HIDEN_REQUEST_DENIED:
          HidenRequestDeniedReader hidenRequestDeniedReader = (HidenRequestDeniedReader) trameReader.get();

          // Get trame record
          status = hidenRequestDeniedReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          HidenRequestDenied hidenRequestDenied = hidenRequestDeniedReader.get();
          
          token = hidenRequestDenied.token();
          if (client.initialTokens.contains(token)) {
            var file = hidenRequestDenied.file();
            var fileInfo = client.mapFilesToDownload.get(file);
            
            fileInfo.getARefusal();
            if (fileInfo.everyoneRefusedToShareTheFile()) {
              System.out.println("The file '" + file.filename() + "' could not be downloaded !");
              client.mapFilesToDownload.remove(file);
              
//              client.initialTokens.remove(token);
            }
          }
          else {
            var tokenSrcOpt = client.getTokenSrc(token);
            if (tokenSrcOpt.isEmpty()) {
              silentlyClose();
            }
            else {
              var tokenSrc = tokenSrcOpt.orElseThrow();
              var context = client.mapTokenContext.get(tokenSrc);
              var bb = new HidenRequestDenied(hidenRequestDenied.file(), hidenRequestDenied.chunkNumber(), tokenSrc).asBuffer();
              var trame = new TrameBytes(Codes.HIDEN_REQUEST_DENIED, bb).asBuffer();
              context.queueBb(trame);
            }
          }
          
          state = State.WAITING_FOR_OPCODE;
          hidenRequestDeniedReader.reset();
          break;
          
          
        case Codes.HIDEN_RESPONSE:
          HidenResponseReader hidenResponseReader = (HidenResponseReader) trameReader.get();

          // Get trame record
          status = hidenResponseReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          HidenResponse hidenResponse = hidenResponseReader.get();
          
          token = hidenResponse.token();
          if (client.initialTokens.contains(token)) {
            var file = hidenResponse.file();
 
            var fileInfo = client.mapFilesToDownload.get(file);
//            if (fileInfo == null) System.out.println("NULL");
            if (fileInfo != null) {
              var chunkNumber = hidenResponse.chunkNumber();
              var chunk = hidenResponse.chunk();
              
              fileInfo.addChunk(chunkNumber, chunk);
              
              if (fileInfo.canMergeChunksIntoFile()) {
                fileInfo.mergeChunksIntoFile();
                client.mapFilesToDownload.remove(file);
                System.out.println("The file '" + file.filename() + "' is downloaded");
                
//              client.initialTokens.remove(token);
              }
              else {
                var bb = new HidenRequest(file, fileInfo.nextChunkNumber(), token).asBuffer();
                var trame = new TrameBytes(Codes.HIDEN_REQUEST, bb).asBuffer();
                queueBb(trame);
              }
            }
          }
          else {
            var tokenSrcOpt = client.getTokenSrc(token);
            if (tokenSrcOpt.isEmpty()) {
              silentlyClose();
            }
            else {
              var tokenSrc = tokenSrcOpt.orElseThrow();
              var context = client.mapTokenContext.get(tokenSrc);
              var bb = new HidenResponse(hidenResponse.file(), hidenResponse.chunkNumber(), hidenResponse.chunk(), tokenSrc).asBuffer();
              var trame = new TrameBytes(Codes.HIDEN_RESPONSE, bb).asBuffer();
              context.queueBb(trame);
            }
          }
          
          
          state = State.WAITING_FOR_OPCODE;
          hidenResponseReader.reset();
          break;
          
          
          
          
        case Codes.FIRST_OPEN_REQUEST:  
        case Codes.OPEN_REQUEST:
          // Get trame reader
          OpenRequestReader openRequestReader = (OpenRequestReader) trameReader.get();

          // Get trame record
          status = openRequestReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          OpenRequest openRequest = openRequestReader.get();

          var file = openRequest.file();
          
          var sharingState = client.mapFileSharingState.get(file);
          var firstOR = Codes.FIRST_OPEN_REQUEST;
          
          if (opcode == firstOR && sharingState == SharingState.CHOICE) {
//            System.out.println("Someone wants to download your file '" + file.filename() + "', do you want to share it ?");
          }
          if (opcode == firstOR && sharingState == SharingState.ALWAYS_REFUSE) {
            var trame = new TrameBytes(Codes.OPEN_REQUEST_DENIED, openRequest.asBuffer()).asBuffer();
            queueBb(trame);
          }
          if (opcode == Codes.OPEN_REQUEST || (opcode == firstOR && sharingState == SharingState.ALWAYS_ACCEPT)) {
//            System.out.println("TEST");
            var chunkNumber = openRequest.chunkNumber();
            var pathname = client.mapFilePathname.get(file);
            var bb = new OpenResponse(file, chunkNumber, FileInformation.getChunkInFile(pathname, chunkNumber)).asBuffer();
            var trame = new TrameBytes(Codes.OPEN_RESPONSE, bb).asBuffer();
            queueBb(trame);
          }

          state = State.WAITING_FOR_OPCODE;
          openRequestReader.reset();
          break;

          
        case Codes.OPEN_REQUEST_DENIED:
          OpenRequestDeniedReader openRequestDeniedReader = (OpenRequestDeniedReader) trameReader.get();

          // Get trame record
          status = openRequestDeniedReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          OpenRequestDenied openRequestDenied = openRequestDeniedReader.get();
          
          file = openRequestDenied.file();
          var fileInfo = client.mapFilesToDownload.get(file);
          
          fileInfo.getARefusal();
          if (fileInfo.everyoneRefusedToShareTheFile()) {
            System.out.println("The file '" + file.filename() + "' could not be downloaded !");
            client.mapFilesToDownload.remove(file);
          }
          
          state = State.WAITING_FOR_OPCODE;
          openRequestDeniedReader.reset();
          break;
          
          
        case Codes.OPEN_RESPONSE:
          OpenResponseReader openResponseReader = (OpenResponseReader) trameReader.get();

          // Get trame record
          status = openResponseReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          OpenResponse openResponse = openResponseReader.get();
                    
          file = openResponse.file();
          
          fileInfo = client.mapFilesToDownload.get(file);
//          if (fileInfo == null) System.out.println("NULL");
          if (fileInfo != null) {
            var chunkNumber = openResponse.chunkNumber();
            var chunk = openResponse.chunk();
            
            fileInfo.addChunk(chunkNumber, chunk);
            
            if (fileInfo.canMergeChunksIntoFile()) {
              fileInfo.mergeChunksIntoFile();
              client.mapFilesToDownload.remove(file);
              System.out.println("The file '" + file.filename() + "' is downloaded");
            }
            else {
              var bb = new OpenRequest(file, fileInfo.nextChunkNumber()).asBuffer();
              var trame = new TrameBytes(Codes.OPEN_REQUEST, bb).asBuffer();
              queueBb(trame);
            }
          }
          
          state = State.WAITING_FOR_OPCODE;
          openResponseReader.reset();
          break;
          
          
          
        case Codes.PROXY:
          ProxyReader proxyReader = (ProxyReader) trameReader.get();

          // Get trame record
          status = proxyReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          Proxy proxy = proxyReader.get();
          
          var tokenSrc = proxy.tokenSrc();
          var dst = proxy.dst();
          var tokenDst = proxy.tokenDst();
          client.routingTable.put(tokenSrc, new PairListeningAddressToken(dst, tokenDst));
          
          var bb = new Token(tokenSrc).asBuffer();
          var trame = new TrameBytes(Codes.PROXY_OK, bb).asBuffer();
          queueBb(trame);
          
          state = State.WAITING_FOR_OPCODE;
          proxyReader.reset();
          break;
          
        case Codes.TERMINAL:
          // Get trame reader 
          TokenReader tokenReader = (TokenReader) trameReader.get();
         
          // Get trame record
          status = tokenReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
//          Token token = tokenReader.get();
          
          tokenSrc = tokenReader.get().tokenSrc();
          client.terminalTokens.add(tokenSrc);
          
          bb = new Token(tokenSrc).asBuffer();
          trame = new TrameBytes(Codes.TERMINAL_OK, bb).asBuffer();
          queueBb(trame);
          
          state = State.WAITING_FOR_OPCODE;
          tokenReader.reset();
          break;
        }
      }
    }

    /**
     * Add a ByteBuffer to the ByteBuffer queue, tries to fill bufferOut and updateInterestOps
     *
     * @param bb
     */
    private void queueBb(ByteBuffer bb) {
      // TODO
      queue.add(bb);
      processOut();
      updateInterestOps();
    }

    /**
     * Try to fill bufferOut from the message queue
     *
     */
    private void processOut() {
      // TODO
      if (!queue.isEmpty()) {
        var bb = queue.peek();

        if (bufferOut.remaining() >= bb.remaining()) {
          bufferOut.put(bb);
          queue.poll();
          return;
        }
      }
    }

    /**
     * Update the interestOps of the key looking only at values of the boolean
     * closed and of both ByteBuffers.
     *
     * The convention is that both buffers are in write-mode before the call to
     * updateInterestOps and after the call. Also it is assumed that process has
     * been be called just before updateInterestOps.
     */

    private void updateInterestOps() {
      var newInterestOps = 0;
      if (bufferOut.position() != 0) {
        newInterestOps = newInterestOps | SelectionKey.OP_WRITE;
      }
      if (bufferIn.hasRemaining() && !closed) {
        newInterestOps = newInterestOps | SelectionKey.OP_READ;
      }

      if (newInterestOps == 0) { // si la connexion a été coupée et il n'y pas plus de données dans buffer / si c'est closed et le buffer est vide
        silentlyClose();
        return;
      }
      key.interestOps(newInterestOps);
    }

    private void silentlyClose() {
      try {
        sc.close();
      } catch (IOException e) {
        // ignore exception
      }
    }

    /**
     * Performs the read action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doRead and after the call
     *
     * @throws IOException
     */
    private void doRead() throws IOException {
      if (sc.read(bufferIn) == -1) {
        closed = true;
      }
      processIn();
      updateInterestOps();
    }

    /**
     * Performs the write action on sc
     *
     * The convention is that both buffers are in write-mode before the call to
     * doWrite and after the call
     *
     * @throws IOException
     */

    private void doWrite() throws IOException {
      bufferOut.flip();
      sc.write(bufferOut);
      bufferOut.compact();
      processOut();
      updateInterestOps();
    }

    public void doConnect() throws IOException {
      if (!sc.finishConnect()) {
        return; // the selector gave a bad hint
      }
      key.interestOps(SelectionKey.OP_READ);
      if (client.firstConnectState == FirstConnectState.WAITING) {
        client.firstConnectState = FirstConnectState.DONE;
        return;
      }
      updateInterestOps();
    }
  }
  
  
  private enum FirstConnectState {
    DONE, WAITING
  };
  
  private FirstConnectState firstConnectState = FirstConnectState.WAITING;


  private enum SharingState {
    ALWAYS_ACCEPT, ALWAYS_REFUSE, CHOICE
  };
  
  private final HashMap<FILE, SharingState> mapFileSharingState = new HashMap<>();
  
  

  private static final int BUFFER_SIZE = 10_000;
  private static Logger logger = Logger.getLogger(ClientChadow.class.getName());

  private final SocketChannel sc;
  private final Selector selector;
  private final InetSocketAddress serverAddress;
  private final Thread console;
  private Context uniqueContext;


  private String login;
  private final ServerSocketChannel listenSocket;
  private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
  private ArrayList<FILE> listAvailableFiles = new ArrayList<>();
  private Set<FILE> filesSharedByMe = new HashSet<>();
  


  public ClientChadow(InetSocketAddress serverAddress) throws IOException {
    this.serverAddress = serverAddress;
    this.sc = SocketChannel.open();
    this.selector = Selector.open();
    this.console = Thread.ofPlatform().unstarted(this::consoleRun);

    listenSocket = ServerSocketChannel.open();
    listenSocket.bind(null);
  }

  private void consoleRun() {
    try {
      try (var scanner = new Scanner(System.in)) {
        showMenu();
        while (scanner.hasNextLine()) {
          var cmd = scanner.nextLine();
          sendCommand(cmd);
        }
      }
      logger.info("Console thread stopping");
    } catch (InterruptedException e) {
      logger.info("Console thread has been interrupted");
    }
  }

  /**
   * Send instructions to the selector via a BlockingQueue and wake it up
   *
   * @param cmd
   * @throws InterruptedException
   */

  private void sendCommand(String cmd) throws InterruptedException {
    queue.put(cmd);
    selector.wakeup();
  }

  /**
   * Processes the command from the BlockingQueue 
   * @throws IOException 
   * @throws NoSuchAlgorithmException 
   * @throws InterruptedException 
   */

  private void processCommands() throws IOException, NoSuchAlgorithmException {
    if (!queue.isEmpty()) {
      var cmd = queue.poll();

      if (cmd.matches("\\s*")) {
        // si cmd est vide ou ne contient que des espaces, rien ne se passe
        return;
      }

      var regex = "(0|3|5|8|9|10|12|28|14|50|51|19)";
      var array = cmd.split(" ");
      var firstStr = array[0];
      // teste si la commande commence par un code valide
      if (!firstStr.matches(regex)) {
        System.out.println("Invalid Command");
        return;
      }

      var opcode = Byte.parseByte(firstStr);


      if (opcode != Codes.TRY_CONNECTION && !uniqueContext.isLogin()) {
        System.out.println("You can't use the chat server, you are not login yet");
        return;
      }
      if (opcode == Codes.TRY_CONNECTION && uniqueContext.isLogin()) {
        // déjà connecté
        return;
      }


      switch(opcode) {
      case Codes.TRY_CONNECTION:
        if (array.length != 2) {
          System.out.println("Invalid Format : 0 login");
          return;
        }
        
        login = array[1];
        var listeningAddress = Tools.sscToListeningAddress(listenSocket);
        var bb = new TryConnection(login, listeningAddress).asBuffer();
        var trame = new TrameBytes(opcode, bb).asBuffer();
        uniqueContext.queueBb(trame);
        break;

      case Codes.MSG_TO_ALL:
        array = cmd.split(" ", 2);
        if (array.length != 2) {
          System.out.println("Invalid Format : 3 message");
          return;
        }
        
        var msg = array[1];
        bb = Tools.stringToBuffer(msg);
        trame = new TrameBytes(opcode, bb).asBuffer();
        uniqueContext.queueBb(trame);
        break;

      case Codes.MSG_TO_ONE:
        array = cmd.split(" ", 3);
        if (array.length != 3) {
          System.out.println("Invalid Format : 5 pseudoDst message");
          return;
        }

        var loginDst = array[1];
        msg = array[2];
        bb = new MsgToOne(loginDst, msg).asBuffer();
        trame = new TrameBytes(opcode, bb).asBuffer();
        uniqueContext.queueBb(trame);
        break;


       
        
      case Codes.SHARE_FILES:
      case Codes.STOP_SHARE_FILES:
        
//        if (!Security.getProvider("MD5").isConfigured()) {
//          System.out.println("The algorithm MD5 is not configured in this Java environment");
//          return;
//        }
        
        array = cmd.split(" ");
        if (array.length < 2) {
          System.out.println("Invalid Format : " + opcode + " file1 ... fileN");
          return;
        }

        if (opcode == Codes.STOP_SHARE_FILES && filesSharedByMe.isEmpty()) {
          return;
        }
        
        var nbFiles = array.length - 1;
        var list = new ArrayList<X>();
        var cmpt = 0;
        
        regex = "[0-" + (filesSharedByMe.size()-1) + "]";
        var toArray = filesSharedByMe.toArray(FILE[]::new);
        
        for (var i = 0; i < nbFiles; i++) {
          var pathName = array[i + 1];
          FILE file;
          
          if (opcode == Codes.STOP_SHARE_FILES && pathName.matches(regex)) {
              var index = Integer.parseInt(pathName);
              file = toArray[index];
          }
          else {
            if (!pathIsValid(pathName)) {cmpt++; continue;}
            file = getFile(pathName);  
          }
          
          list.add(file);
          
          switch(opcode) {
            case Codes.SHARE_FILES -> {
              filesSharedByMe.add(file);
              mapFilePathname.put(file, pathName);
              mapFileSharingState.put(file, SharingState.ALWAYS_ACCEPT);
            }
            case Codes.STOP_SHARE_FILES -> {
              filesSharedByMe.remove(file);
              mapFilePathname.remove(file);
              mapFileSharingState.remove(file);
            }
          }
        }

        bb = new LIST(nbFiles - cmpt, list).asBuffer();
        trame = new TrameBytes(opcode, bb).asBuffer();
        uniqueContext.queueBb(trame);
        break;
        
        
        
        
      case Codes.CHANGE_SHARING_STATUS_TO_ALWAYS_ACCEPT:
      case Codes.CHANGE_SHARING_STATUS_TO_ALWAYS_REFUSE:
        array = cmd.split(" ");
        if (array.length < 2) {
          System.out.println("Invalid Format : " + opcode + " file1 ... fileN");
          return;
        }

        if (filesSharedByMe.isEmpty()) {
          return;
        }
        
        nbFiles = array.length - 1;
        regex = "[0-" + (filesSharedByMe.size()-1) + "]";
        toArray = filesSharedByMe.toArray(FILE[]::new);
        
        for (var i = 0; i < nbFiles; i++) {
          var pathName = array[i + 1];
          FILE file;
          
          if (pathName.matches(regex)) {
              var index = Integer.parseInt(pathName);
              file = toArray[index];
          }
          else {
            if (!pathIsValid(pathName)) {continue;}
            file = getFile(pathName);  
          }
          
          switch(opcode) {
            case Codes.CHANGE_SHARING_STATUS_TO_ALWAYS_ACCEPT -> mapFileSharingState.put(file, SharingState.ALWAYS_ACCEPT);
            case Codes.CHANGE_SHARING_STATUS_TO_ALWAYS_REFUSE -> mapFileSharingState.put(file, SharingState.ALWAYS_REFUSE);
          }
        }
        break;
      
        
        
      case Codes.REQUEST_LIST_AVAILABLE_PSEUDOS:
      case Codes.REQUEST_LIST_AVAILABLE_FILES:
        uniqueContext.queueBb(Tools.byteToBuffer(opcode));
        break;
          
      case Codes.REQUEST_LIST_FILES_SHARED_BY_ME:
        if (filesSharedByMe.isEmpty()) {
          System.out.println("You have not shared any files previously");
        }
        else {
          System.out.println("All files shared by you previously : ");
          var index = 0;
          for (var file : filesSharedByMe) {
            System.out.println(index++ + " " + file.filename() + " --> " + mapFilePathname.get(file));
          } 
        }
        break;
                
        
      case Codes.REQUEST_LIST_SHARER:
      case Codes.REQUEST_LIST_PROXY:
        if (array.length != 2) {
          System.out.println("Invalid Format : " + opcode + " filename_you_want_to_download");
          return;
        }
        
        str = array[1];
        
        if (requestListAvailableFilesState == RequestListAvailableFilesState.WAITING) {
          resendOpcode = opcode;
          requestListAvailableFilesState = RequestListAvailableFilesState.PROCESS;
          bb = Tools.byteToBuffer(Codes.REQUEST_LIST_AVAILABLE_FILES);
          uniqueContext.queueBb(bb);
          return;
        }
        
        try {
          if (listAvailableFiles.isEmpty()) {
            System.out.println("No one shares the file '" + str + "'");
            return;
          }

          var names = listAvailableFiles.stream().map(FILE::filename).toList();
          
          regex = "[0-" + (names.size()-1) + "]";
          FILE fileToDownload;
          if (str.matches(regex)) {
            var number = Integer.parseInt(str);
            fileToDownload = listAvailableFiles.get(number);
          }
          else if (names.contains(str)) {
            fileToDownload = listAvailableFiles.stream().filter(f -> f.filename().equals(str)).findFirst().orElseThrow();
          }
          else {
            System.out.println("No one shares the file '" + str + "'");
            return;
          }
         
          queueFile.add(fileToDownload);
          bb = fileToDownload.asBuffer();
          trame = new TrameBytes(opcode, bb).asBuffer();
          uniqueContext.queueBb(trame);
          
        } finally {
          requestListAvailableFilesState = RequestListAvailableFilesState.WAITING;
        }
        break;
        
      }
    }
  }
  

  private enum RequestListAvailableFilesState {
    DONE, WAITING, PROCESS
  };
  
  private RequestListAvailableFilesState requestListAvailableFilesState = RequestListAvailableFilesState.WAITING;
  private String str;
  private byte resendOpcode;

  
  
  
  
  private boolean pathIsValid(String pathName) {
    var path = Path.of(pathName);
    
    if (!Files.exists(path)) {
      System.out.println("The file '" + pathName + "' does not exist");
      return false;
    }
    if (!Files.isRegularFile(path)) {
      System.out.println(pathName +  " is not a file");
      return false;
    }
    return true;
  }
  
  // par défaut, le répertoire courant c'est celui à la racine du projet
  public FILE getFile(String pathName) throws IOException, NoSuchAlgorithmException {
    var path = Path.of(pathName);
    
    var data = Files.readAllBytes(path);
    var hash = MessageDigest.getInstance("MD5").digest(data);

    var Id = ByteBuffer.allocate(16);
    Id.put(hash);
    Id.flip();

    var size = data.length;
    var filename = path.getFileName().toString();
    return new FILE(filename, Id, size);
  }



//  public boolean testValidCmd(String cmd) {
//    return false;
//  }

  
  public void showMenu() {
    System.out.println("---------\nMENU :");
    System.out.println("Action : Command to enter");
    if (!uniqueContext.isLogin()) {
      System.out.println("To login : 0 login");
      return;
    }

    System.out.println("To send a message to all : 3 message");
    System.out.println("To send a private message to someone : 5 login_dst message");
  
    System.out.println("To share files : 8 list_files_share");
    System.out.println("To unshare files : 9 list_files_unshare");
    System.out.println("To change the sharing status of files to 'Always Accept' : 50 list_file");
    System.out.println("To change the sharing status of files to 'Always Refuse' : 51 list_file");
 
    System.out.println("To request the list of all logins on the server : 10");
    System.out.println("To request the list of all files available for download : 12");
    System.out.println("To request the list of files you shared : 28");
    
    System.out.println("To download a file in open mode : 14 filename_you_want_to_download");
    System.out.println("To download a file in hidden mode : 19 filename_you_want_to_download"); 
  }






  public void launch() throws IOException, NoSuchAlgorithmException {
    listenSocket.configureBlocking(false);
    listenSocket.register(selector, SelectionKey.OP_ACCEPT);
    
    sc.configureBlocking(false);
    var key = sc.register(selector, SelectionKey.OP_CONNECT);
    uniqueContext = new Context(this, key);
    key.attach(uniqueContext);
    sc.connect(serverAddress);

    console.start();

    while (!Thread.interrupted()) {
      try {
        selector.select(this::treatKey);
        processCommands();
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
    }
  }
  
  private void treatKey(SelectionKey key) {
    try {
      if (key.isValid() && key.isAcceptable()) {
        doAccept(key);
      }
    } catch (IOException ioe) {
      // lambda call in select requires to tunnel IOException
      throw new UncheckedIOException(ioe);
    }
    try {
      if (key.isValid() && key.isConnectable()) {
        ((Context) key.attachment()).doConnect();
      }
      if (key.isValid() && key.isWritable()) {
        ((Context) key.attachment()).doWrite();
      }
      if (key.isValid() && key.isReadable()) {
        ((Context) key.attachment()).doRead();
      }
    } catch (IOException e) {
      silentlyClose(key);
    }
  }
  
  
  private void doAccept(SelectionKey key) throws IOException {
    // TODO
    var client = listenSocket.accept();
    if (client == null) {
      logger.warning("le selecteur a menti");
      return;
    }
    client.configureBlocking(false);
    var clientKey = client.register(selector, SelectionKey.OP_READ);
    clientKey.attach(new Context(this, clientKey));
  }
  
  

  private void silentlyClose(SelectionKey key) {
    Channel sc = (Channel) key.channel();
    try {
      sc.close();
    } catch (IOException e) {
      // ignore exception
    }
  }

  public static void main(String[] args) throws NumberFormatException, IOException, NoSuchAlgorithmException {
    if (args.length != 2) {
      usage();
      return;
    }
    new ClientChadow(new InetSocketAddress(args[0], Integer.parseInt(args[1]))).launch();
  }

  private static void usage() {
    System.out.println("Usage : ClientChadow hostname port");
  }
}