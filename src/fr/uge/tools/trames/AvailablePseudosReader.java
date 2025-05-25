package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ListReader;
import fr.uge.tools.object.StringReader;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.StringReader.StringBytes;

public final class AvailablePseudosReader implements Reader<AvailablePseudosReader.AvailablePseudos>,Trame {
  
    public record AvailablePseudos(List<String> pseudos) {
      public ByteBuffer asBuffer() {
        return new LIST(pseudos.size(), new ArrayList<>(pseudos.stream().map(StringBytes::new).toList())).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_AVAILABLE_PSEUDOS
    }

    private State state = State.WAITING_FOR_AVAILABLE_PSEUDOS;
    private AvailablePseudos value;
    
  
    private ListReader lr = new ListReader(new StringReader());
    private List<String> availablePseudos;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_AVAILABLE_PSEUDOS) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              availablePseudos = lr.get().list().stream().map(x -> ((StringBytes) x).str()).toList();
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
        
        value = new AvailablePseudos(availablePseudos);
        return ProcessStatus.DONE;
    }

    @Override
    public AvailablePseudos get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_AVAILABLE_PSEUDOS;
    }
}