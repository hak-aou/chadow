package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ListReader;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.PairListeningAddressTokenReader;
import fr.uge.tools.object.PairListeningAddressTokenReader.PairListeningAddressToken;

public final class ListProxyReader implements Reader<ListProxyReader.ListProxy>,Trame {
  
    public record ListProxy(List<PairListeningAddressToken> list) {
      public ByteBuffer asBuffer() {
        return new LIST(list.size(), new ArrayList<>(list)).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_LIST_PROXY
    }

    private State state = State.WAITING_FOR_LIST_PROXY;
    private ListProxy value;
    
  
    private ListReader lr = new ListReader(new PairListeningAddressTokenReader());
    private List<PairListeningAddressToken> list;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_LIST_PROXY) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              list = lr.get().list().stream().map(x -> (PairListeningAddressToken) x).toList();
              state = State.DONE;
              lr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        value = new ListProxy(list);
        return ProcessStatus.DONE;
    }

    @Override
    public ListProxy get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_LIST_PROXY;
    }
}