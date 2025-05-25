package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ListReader;
import fr.uge.tools.object.ListeningAddressReader;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;

public final class ListSharerReader implements Reader<ListSharerReader.ListSharer>,Trame {
  
    public record ListSharer(List<LISTENING_ADDRESS> listeningAddressList) {
      public ByteBuffer asBuffer() {
        return new LIST(listeningAddressList.size(), new ArrayList<>(listeningAddressList)).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_LIST_SHARER
    }

    private State state = State.WAITING_FOR_LIST_SHARER;
    private ListSharer value;
    
  
    private ListReader lr = new ListReader(new ListeningAddressReader());
    private List<LISTENING_ADDRESS> listeningAddressList;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_LIST_SHARER) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              listeningAddressList = lr.get().list().stream().map(x -> (LISTENING_ADDRESS) x).toList();
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
        
        value = new ListSharer(listeningAddressList);
        return ProcessStatus.DONE;
    }

    @Override
    public ListSharer get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_LIST_SHARER;
    }
}