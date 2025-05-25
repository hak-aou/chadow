package fr.uge.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.uge.tools.Codes;
import fr.uge.tools.Tools;
import fr.uge.tools.TrameBytes;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.ListReader.X;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;
import fr.uge.tools.object.MessageReader.Message;
import fr.uge.tools.object.OpcodeReader;
import fr.uge.tools.object.PairListeningAddressTokenReader.PairListeningAddressToken;
import fr.uge.tools.Reader.ProcessStatus;
import fr.uge.tools.object.StringReader.StringBytes;
import fr.uge.tools.trames.*;
import fr.uge.tools.trames.HidenRequestDeniedReader.HidenRequestDenied;
import fr.uge.tools.trames.HidenRequestReader.HidenRequest;
import fr.uge.tools.trames.HidenResponseReader.HidenResponse;
import fr.uge.tools.trames.TryConnectionReader.TryConnection;
import fr.uge.tools.trames.MsgToAllReader.MsgToAll;
import fr.uge.tools.trames.MsgToOneReader.MsgToOne;
import fr.uge.tools.trames.ProxyReader.Proxy;
import fr.uge.tools.trames.ShareFilesReader.ShareFiles;
import fr.uge.tools.trames.StopShareFilesReader.StopShareFiles;
import fr.uge.tools.trames.TokenReader.Token;
import fr.uge.tools.trames.RequestListSharerOrProxyReader.RequestListSharerOrProxy;


public class ServerChadow {

  static private class Context {
    private final SelectionKey key;
    private final SocketChannel sc;
    private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
    private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private final ServerChadow server; // we could also have Context as an instance class, which would naturally
                                       // give access to ServerChadow.this
    private boolean closed = false;

    private LISTENING_ADDRESS listenAddress;
    private String login;
    private Set<FILE> sharedFiles = new HashSet<>();



    private OpcodeReader opcodeReader = new OpcodeReader();

    private enum State {
      WAITING_FOR_OPCODE, DONE
    };

    private State state = State.WAITING_FOR_OPCODE;
    private byte opcode;



    private Context(ServerChadow server, SelectionKey key) {
      this.key = key;
      this.sc = (SocketChannel) key.channel();
      this.server = server;
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
     * The convention is that bufferIn is in write-mode before the call to process and
     * after the call
     * @throws IOException 
     *
     */
    private void processIn() throws IOException {
      // TODO
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
        case Codes.TRY_CONNECTION:
          // Get trame reader
          TryConnectionReader tryConnectionReader = (TryConnectionReader) trameReader.get();
          
          // Get trame record
          status = tryConnectionReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          TryConnection tryConnection = tryConnectionReader.get();
          
          
          login = tryConnection.login();
          if (server.listLogins.contains(login)) {
            var bb = Tools.byteToBuffer(Codes.PSEUDO_REFUSED);
            queueBb(bb);
            login = null;
          }
          else {
            server.listLogins.add(login);
            var bb = Tools.byteToBuffer(Codes.PSEUDO_VALIDATED);
            queueBb(bb);
            listenAddress = tryConnection.address();
            server.mapLaContext.put(listenAddress, this);
            server.loginAndContextMap.put(login, this);
            server.listContext.add(this);
          }


          state = State.WAITING_FOR_OPCODE;
          tryConnectionReader.reset();
          break;

        case Codes.MSG_TO_ALL:
          // Get trame reader
          MsgToAllReader msgToAllReader = (MsgToAllReader) trameReader.get();
          
          // Get trame record
          status = msgToAllReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          MsgToAll msgToAll = msgToAllReader.get();
          
          var bb = new Message(login, msgToAll.message()).asBuffer();
          var trame = new TrameBytes(Codes.MSG_RECEIVED, bb).asBuffer();
          server.broadcast(trame);

          state = State.WAITING_FOR_OPCODE;
          msgToAllReader.reset();
          break;

        case Codes.MSG_TO_ONE:
          // Get trame reader
          MsgToOneReader msgToOneReader = (MsgToOneReader) trameReader.get();
          
          // Get trame record
          status = msgToOneReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          MsgToOne msgToOne = msgToOneReader.get();
          
          var loginDst = msgToOne.pseudoDst();
          var message = msgToOne.message();
      
          if (!server.listLogins.contains(loginDst)) {
            // pseudo du client destinataire non trouvé
            bb = msgToOne.asBuffer();
            trame = new TrameBytes(Codes.PSEUDO_NOT_FOUND, bb).asBuffer();
            queueBb(trame);
          }
          else {
            bb = new Message(login, message).asBuffer();

            trame = new TrameBytes(Codes.MSG_RECEIVED, bb).asBuffer();

            // serveur transmet le message au client destinataire
            server.sendPrivateMsgToOne(loginDst, trame);

            // serveur envoie une trame au client émetteur pour confirmer l'envoie du message
            var bb2 = Tools.byteToBuffer(Codes.PSEUDO_FOUND);
            queueBb(bb2);
          }

          state = State.WAITING_FOR_OPCODE;
          msgToOneReader.reset();
          break;

          
        case Codes.SHARE_FILES:
          // Get trame reader
          ShareFilesReader shareFilesReader = (ShareFilesReader) trameReader.get();
          
          // Get trame record
          status = shareFilesReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          ShareFiles shareFiles = shareFilesReader.get();
          
          for (FILE file : shareFiles.filesShared()) {
            var added = sharedFiles.add(file);
            
            var filename = file.filename();
            if (added) {
              logger.info("The file '" + filename + "' is now shared");
              server.setFiles.add(file);
              server.mapFileNb.merge(file, 1, Integer::sum);
            }
            else {
              logger.info("The file '" + filename + "' is already shared");
            }
          }

          state = State.WAITING_FOR_OPCODE;
          shareFilesReader.reset();
          break;
          
          
        case Codes.STOP_SHARE_FILES:
          // Get trame reader
          StopShareFilesReader stopShareFilesReader = (StopShareFilesReader) trameReader.get();
          
          // Get trame record
          status = stopShareFilesReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          StopShareFiles stopShareFiles = stopShareFilesReader.get();
          
          for (FILE file : stopShareFiles.filesUnshared()) {
            var removed = sharedFiles.remove(file);
            
            var filename = file.filename();
            if (removed) {
              logger.info("The file '" + filename + "' is no longer shared");
              var updatedNb = server.mapFileNb.merge(file, -1, Integer::sum);
              if (updatedNb <= 0){
                server.setFiles.remove(file);
              }
            }
            else {
              logger.info("The file '" + filename + "' was never shared");
            }
          }

          state = State.WAITING_FOR_OPCODE;
          stopShareFilesReader.reset();
          break;
          
          
          
        // Send to client response in this format : byte, LIST(X)
        case Codes.REQUEST_LIST_AVAILABLE_PSEUDOS:
        case Codes.REQUEST_LIST_AVAILABLE_FILES:
          var listX = new ArrayList<X>();
                    
          switch(opcode) {
            case Codes.REQUEST_LIST_AVAILABLE_PSEUDOS -> listX.addAll(server.listLogins.stream().map(login -> new StringBytes(login)).toList());
            case Codes.REQUEST_LIST_AVAILABLE_FILES -> listX.addAll(server.setFiles);
          }
          
          bb = new LIST(listX.size(), listX).asBuffer();
          trame = new TrameBytes(Codes.responseOfRequestList(opcode), bb).asBuffer();
          queueBb(trame);
          state = State.WAITING_FOR_OPCODE;
          break;
          
          
          
        case Codes.REQUEST_LIST_SHARER:
          // Get trame reader
          RequestListSharerOrProxyReader requestListSharerReader = (RequestListSharerOrProxyReader) trameReader.get();
          
          // Get trame record
          status = requestListSharerReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          RequestListSharerOrProxy requestListSharer = requestListSharerReader.get();
          
          var file = requestListSharer.file();
          var list = new ArrayList<X>(server.getListSharer(file));
          
          bb = new LIST(list.size(), list).asBuffer();
          trame = new TrameBytes(Codes.LIST_SHARER, bb).asBuffer();
          queueBb(trame);
          
          state = State.WAITING_FOR_OPCODE;
          requestListSharerReader.reset();
          break;
          
          
        case Codes.REQUEST_LIST_PROXY:
          // Get trame reader
          RequestListSharerOrProxyReader requestListProxyReader = (RequestListSharerOrProxyReader) trameReader.get();
          
          // Get trame record
          status = requestListProxyReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          RequestListSharerOrProxy requestListProxy = requestListProxyReader.get();
          
          
          file = requestListProxy.file();
          var listSharer = server.getListSharer(file);
          var size = listSharer.size();
          
          var nbClients = server.listLogins.size();
          if (nbClients <= 2) {
            
            var classForListProxy = new ClassForListProxy(this, size);
            
            var la = Tools.sscToListeningAddress(server.listenSocket);
            for (LISTENING_ADDRESS dst : listSharer) {
              var tokenSrc = server.nextToken();
              var tokenDst = server.nextToken();
              
              classForListProxy.add(new PairListeningAddressToken(la, tokenSrc));
              server.routingTable.put(tokenSrc, new PairListeningAddressToken(dst, tokenDst));
              
              var contextTerminal = server.mapLaContext.get(dst);
              bb = new Token(tokenDst).asBuffer();
              trame = new TrameBytes(Codes.TERMINAL, bb).asBuffer();
              contextTerminal.queueBb(trame);
              server.mapTokenClassForListProxy.put(tokenDst, classForListProxy);
            }
          }
          else{
            var classForListProxy = new ClassForListProxy(this, size * 2);
            
            for (LISTENING_ADDRESS dst : listSharer) {
              var tokenSrc = server.nextToken();
              var tokenDst = server.nextToken();

              var contextTerminal = server.mapLaContext.get(dst);
              var contextProxy = server.getRandomContextProxy(this, contextTerminal);
              classForListProxy.add(new PairListeningAddressToken(contextProxy.listenAddress, tokenSrc));

              bb = new Proxy(tokenSrc, contextTerminal.listenAddress, tokenDst).asBuffer();
              trame = new TrameBytes(Codes.PROXY, bb).asBuffer();
              contextProxy.queueBb(trame);
              server.mapTokenClassForListProxy.put(tokenSrc, classForListProxy);
              
              bb = new Token(tokenDst).asBuffer();
              trame = new TrameBytes(Codes.TERMINAL, bb).asBuffer();
              contextTerminal.queueBb(trame);
              server.mapTokenClassForListProxy.put(tokenDst, classForListProxy);
            }
          }

          state = State.WAITING_FOR_OPCODE;
          requestListProxyReader.reset();
          break;

          
        case Codes.PROXY_OK:
        case Codes.TERMINAL_OK:
          // Get trame reader 
           TokenReader tokenReader = (TokenReader) trameReader.get();
          
          // Get trame record
          status = tokenReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
//          Token token = tokenReader.get();
          
          var tokenSrc = tokenReader.get().tokenSrc();
          var classForListProxy = server.mapTokenClassForListProxy.get(tokenSrc);
          classForListProxy.getARes();
          if (classForListProxy.HaveAllRes()) {
            classForListProxy.sendCommandListProxy();
          }
          server.mapTokenClassForListProxy.remove(tokenSrc);
          
          state = State.WAITING_FOR_OPCODE;
          tokenReader.reset();
          break;
          
          
          
        case Codes.FIRST_HIDEN_REQUEST:
        case Codes.HIDEN_REQUEST:
          // Get trame reader
          HidenRequestReader hidenRequestReader = (HidenRequestReader) trameReader.get();

          logger.info("HERE OR WHAT");
          // Get trame record
          status = hidenRequestReader.process(bufferIn);
          if (!statusIsDone(status)) { return; }
          HidenRequest hidenRequest = hidenRequestReader.get();
          
          
          var token = hidenRequest.token();
          var pair = server.routingTable.get(token);
          if (pair == null) {
            silentlyClose();
          }
          else {
            server.mapTokenContext.put(token, this);
            var isa = pair.address().asInetSocketAddress();
            var tokenDst = pair.token();
            bb = new HidenRequest(hidenRequest.file(), hidenRequest.chunkNumber(), tokenDst).asBuffer();
            trame = new TrameBytes(opcode, bb).asBuffer();
            server.sendTrameByIsa(isa, trame);
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
          
          var tokenSrcOpt = server.getTokenSrc(token);
          if (tokenSrcOpt.isEmpty()) {
            silentlyClose();
          }
          else {
            tokenSrc = tokenSrcOpt.orElseThrow();
            var context = server.mapTokenContext.get(tokenSrc);
            bb = new HidenRequestDenied(hidenRequestDenied.file(), hidenRequestDenied.chunkNumber(), tokenSrc).asBuffer();
            trame = new TrameBytes(Codes.HIDEN_REQUEST_DENIED, bb).asBuffer();
            context.queueBb(trame);
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
          
          tokenSrcOpt = server.getTokenSrc(token);
          if (tokenSrcOpt.isEmpty()) {
            silentlyClose();
          }
          else {
            tokenSrc = tokenSrcOpt.orElseThrow();
            var context = server.mapTokenContext.get(tokenSrc);
            bb = new HidenResponse(hidenResponse.file(), hidenResponse.chunkNumber(), hidenResponse.chunk(), tokenSrc).asBuffer();
            trame = new TrameBytes(Codes.HIDEN_RESPONSE, bb).asBuffer();
            context.queueBb(trame);
          }
          
          state = State.WAITING_FOR_OPCODE;
          hidenResponseReader.reset();
          break;
        }
        
      }
    }

    /**
     * Add a bytebuffer to the bytebuffer queue, tries to fill bufferOut and updateInterestOps
     *
     * @param bb
     */
    public void queueBb(ByteBuffer bb) {
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
      // TODO
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
        server.listLogins.remove(login);
        server.loginAndContextMap.remove(login);
        for (var file : sharedFiles) {
          var updatedNb = server.mapFileNb.merge(file, -1, Integer::sum);
          if (updatedNb <= 0){
            server.setFiles.remove(file);
          }
        }
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
      // TODO
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
      updateInterestOps();
    }
  }

   
  
  private Context getRandomContextProxy(Context me, Context dst) {
    var list = listContext;
    var size = list.size();
    for(;;){
      var i = new Random().nextInt(size);
      var proxy = list.get(i);
      if (!proxy.login.equals(me.login) && !proxy.login.equals(dst.login)){
        return proxy;
      }
    }
  }
  
  
  private static class ClassForListProxy {
    private final Context context;
    private final ArrayList<X> listProxy = new ArrayList<>();
    private final int nbRes;
    private int cmpt = 0;
    
    public ClassForListProxy(Context context, int nbRes) {
      this.context = context;
      this.nbRes = nbRes;
    }

    public void add(PairListeningAddressToken pair) {
      listProxy.add(pair);
    }
    
    public void getARes(){
      cmpt++;
    }
    
    public boolean HaveAllRes() {
      return cmpt >= nbRes;
    }
    
    public void sendCommandListProxy() {
      var bb = new LIST(listProxy.size(), listProxy).asBuffer();
      var trame = new TrameBytes(Codes.LIST_PROXY, bb).asBuffer();
      context.queueBb(trame);
    }
  }
  
  

  private static final int BUFFER_SIZE = 10_000;
  private static final Logger logger = Logger.getLogger(ServerChadow.class.getName());

  private final ServerSocketChannel serverSocketChannel;
  private final Selector selector;
  
  // to send message to one client
  private final HashMap<String, Context> loginAndContextMap = new HashMap<>();

  private final ArrayList<String> listLogins = new ArrayList<>();
  private final Set<FILE> setFiles = new HashSet<>();
  private final HashMap<FILE, Integer> mapFileNb = new HashMap<>();
  private final ServerSocketChannel listenSocket;
  
  private final HashMap<Integer, PairListeningAddressToken> routingTable = new HashMap<>();
  
  private final HashMap<LISTENING_ADDRESS, Context> mapLaContext = new HashMap<>();
  private int currentToken = 0;
  
  private int nextToken() {
    return currentToken++ % Integer.MAX_VALUE;
  }
  
  private final ArrayList<Context> listContext = new ArrayList<>();
  private final HashMap<Integer, ClassForListProxy> mapTokenClassForListProxy = new HashMap<>();
  private final HashMap<InetSocketAddress, Context> mapIsaContext = new HashMap<>();
  private final HashMap<Integer, Context> mapTokenContext = new HashMap<>();
  
  
  public ServerChadow(int port) throws IOException {
    serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.bind(new InetSocketAddress(port));
    selector = Selector.open();
    
    listenSocket = ServerSocketChannel.open();
    listenSocket.bind(null);
  }

  public void launch() throws IOException {
    listenSocket.configureBlocking(false);
    listenSocket.register(selector, SelectionKey.OP_ACCEPT);
    
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    while (!Thread.interrupted()) {
      Helpers.printKeys(selector); // for debug
      System.out.println("Starting select");
      try {
        selector.select(this::treatKey);
      } catch (UncheckedIOException tunneled) {
        throw tunneled.getCause();
      }
      System.out.println("Select finished");
    }
  }

  private void treatKey(SelectionKey key) {
    Helpers.printSelectedKey(key); // for debug
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
      logger.log(Level.INFO, "Connection closed with client due to IOException", e);
      silentlyClose(key);
    }
  }

  private void doAccept(SelectionKey key) throws IOException {
    var client = ((ServerSocketChannel) key.channel()).accept();
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

  /**
   * Add a bytebuffer to all connected clients queue
   */
  private void broadcast(ByteBuffer bb) {
    for (var key : selector.keys()) {
      var context = (Context) key.attachment();
      if (context != null && context.login != null) { // si la personne est bien connecté login n'est pas null
        var copyBb = bb.duplicate();
        context.queueBb(copyBb);
      }
    }
  }


  private void sendPrivateMsgToOne(String loginDst, ByteBuffer bb) {
    var value = loginAndContextMap.get(loginDst);
    if (value != null) {
      value.queueBb(bb);
    }
  }

  
  // renvoie la liste de toutes les adresses d'écoute des clients partageant le fichier 'file'
  private ArrayList<LISTENING_ADDRESS> getListSharer(FILE file) {
    var list = new ArrayList<LISTENING_ADDRESS>();

    for (var key : selector.keys()) {
      var context = (Context) key.attachment();
      if (context != null) {
        if (context.sharedFiles.contains(file)) {
          list.add(context.listenAddress);
        }
      }
    }
    return list;
  }
  
  
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
  


  public static void main(String[] args) throws NumberFormatException, IOException {
    if (args.length != 1) {
      usage();
      return;
    }
    new ServerChadow(Integer.parseInt(args[0])).launch();
  }

  private static void usage() {
    System.out.println("Usage : ServerChadow port");
  }
}