package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Codes;
import fr.uge.tools.Reader;

public class TrameReader implements Reader<Trame> {

    private enum State {
        DONE, ERROR, WAIT_FOR_TRAME
    }

    byte code;
    public TrameReader(byte code) {
      this.code = code;
    }
    
    private State state = State.WAIT_FOR_TRAME;
    private Trame value;

    private TryConnectionReader tryConnectReader = new TryConnectionReader();
    
    private MsgToAllReader msgToAllReader = new MsgToAllReader();
    private MsgReceivedReader msgReceivedReader = new MsgReceivedReader();
    private MsgToOneReader msgToOneReader = new MsgToOneReader();
    private PseudoNotFoundReader pseudoNotFoundReader = new PseudoNotFoundReader();
    
    private ShareFilesReader shareFilesReader = new ShareFilesReader();
    private StopShareFilesReader stopShareFilesReader = new StopShareFilesReader();
    
    private AvailablePseudosReader availablePseudosReader = new AvailablePseudosReader();
    private AvailableFilesReader availableFilesReader = new AvailableFilesReader();
    
    private RequestListSharerOrProxyReader requestListSharerOrProxyReader = new RequestListSharerOrProxyReader();
    private ListSharerReader listSharerReader = new ListSharerReader();
    private OpenRequestReader openRequestReader = new OpenRequestReader();
    private OpenRequestDeniedReader openRequestDeniedReader = new OpenRequestDeniedReader();
    private OpenResponseReader openResponseReader = new OpenResponseReader();
    
    private ProxyReader proxyReader = new ProxyReader();
    private TokenReader tokenReader = new TokenReader();
    private ListProxyReader listProxyReader = new ListProxyReader();
    private HidenRequestReader hidenRequestReader = new HidenRequestReader();
    private HidenRequestDeniedReader hidenRequestDeniedReader = new HidenRequestDeniedReader();
    private HidenResponseReader hidenResponseReader = new HidenResponseReader();

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        switch (code) {
          case Codes.TRY_CONNECTION -> value = tryConnectReader;
          case Codes.MSG_TO_ALL -> value = msgToAllReader;
          case Codes.MSG_RECEIVED -> value = msgReceivedReader;
          case Codes.MSG_TO_ONE -> value = msgToOneReader;
          case Codes.PSEUDO_NOT_FOUND -> value = pseudoNotFoundReader;
          case Codes.SHARE_FILES -> value = shareFilesReader;
          case Codes.STOP_SHARE_FILES -> value = stopShareFilesReader;
          case Codes.AVAILABLE_PSEUDOS -> value = availablePseudosReader;
          case Codes.AVAILABLE_FILES -> value = availableFilesReader;
          case Codes.REQUEST_LIST_SHARER, Codes.REQUEST_LIST_PROXY -> value = requestListSharerOrProxyReader;
          case Codes.LIST_SHARER -> value = listSharerReader;
          case Codes.FIRST_OPEN_REQUEST, Codes.OPEN_REQUEST -> value = openRequestReader;
          case Codes.OPEN_REQUEST_DENIED -> value = openRequestDeniedReader;
          case Codes.OPEN_RESPONSE -> value = openResponseReader;
          case Codes.PROXY -> value = proxyReader;
          case Codes.PROXY_OK, Codes.TERMINAL, Codes.TERMINAL_OK -> value = tokenReader;
          case Codes.LIST_PROXY -> value = listProxyReader;
          case Codes.FIRST_HIDEN_REQUEST, Codes.HIDEN_REQUEST -> value = hidenRequestReader;
          case Codes.HIDEN_REQUEST_DENIED -> value = hidenRequestDeniedReader;
          case Codes.HIDEN_RESPONSE -> value = hidenResponseReader;
          // ....
        }
        
        state = State.DONE;
        return ProcessStatus.DONE;
    }

    @Override
    public Trame get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAIT_FOR_TRAME;
    }
}