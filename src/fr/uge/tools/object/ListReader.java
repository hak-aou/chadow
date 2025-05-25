package fr.uge.tools.object;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;
import fr.uge.tools.object.PairListeningAddressTokenReader.PairListeningAddressToken;
import fr.uge.tools.object.StringReader.StringBytes;

public class ListReader implements Reader<ListReader.LIST> {
  
    
    public sealed interface X permits StringBytes, FILE, LISTENING_ADDRESS, PairListeningAddressToken {
      ByteBuffer asBuffer();
    }
  
    public record LIST(int nbOfX, List<X> list) {
      
      public ByteBuffer asBuffer() {
        var listofBuffer = list.stream().<ByteBuffer>map(X::asBuffer).toList();
        
        var size = listofBuffer.stream().mapToInt(ByteBuffer::remaining).sum();
        
        var bb = ByteBuffer.allocate(Integer.BYTES + size);
        bb.putInt(nbOfX);
        for (var buffer : listofBuffer) {
          bb.put(buffer);
        }
        bb.flip();
        return bb;
      }
    };

    
    
    private enum State {
        DONE, ERROR, WAITING_FOR_NB_OF_X, WAITING_FOR_LIST
    };


    private State state = State.WAITING_FOR_NB_OF_X;
    
    
    private LIST value;
    
    private IntReader nbOfXReader = new IntReader();
    private int nbOfX;
    private final ArrayList<X> list = new ArrayList<>();
    private int cmpt = 0;
    
    
    private Reader<X> reader;

    
    public ListReader(Reader<X> reader) {
      this.reader = reader;  
    }
    
    
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_NB_OF_X) {
          var statut = nbOfXReader.process(buffer);
          if (statut == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (statut == ProcessStatus.DONE) {
            nbOfX = nbOfXReader.get();
            if (nbOfX < 0) {
              state = State.ERROR;
              return ProcessStatus.ERROR;
            }
            state = State.WAITING_FOR_LIST;
          }
        }
        
        
        if (nbOfX == 0) {
          state = State.DONE;
          value = new LIST(0, list);
          return ProcessStatus.DONE;
        }
        
        for (;;) {
          var status = reader.process(buffer);
          switch (status) {
            case DONE:
              
              var element = reader.get();
              list.add(element);
              cmpt++;
              reader.reset();
                          
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
                              
          if (cmpt == nbOfX) {            
            state = State.DONE;
            value = new LIST(nbOfX, list);
            return ProcessStatus.DONE;
          }
        }
    }

    @Override
    public LIST get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_NB_OF_X;
        nbOfXReader.reset();
        cmpt = 0;
        list.clear();
    }
}