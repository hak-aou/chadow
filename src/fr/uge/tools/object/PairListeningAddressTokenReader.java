package fr.uge.tools.object;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ListReader.X;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;

public class PairListeningAddressTokenReader implements Reader<X> {
  
  
    public record PairListeningAddressToken(LISTENING_ADDRESS address, int token) implements X {
      
      public ByteBuffer asBuffer() {
        var addressBb = address.asBuffer();
            
        var bb = ByteBuffer.allocate(addressBb.remaining() + Integer.BYTES);
        bb.put(addressBb).putInt(token);
        bb.flip();
        return bb;
      }
      
    };

    private enum State {
        DONE, ERROR, WAITING_FOR_LISTENING_ADDRESS, WAITING_FOR_TOKEN
    };


    private State state = State.WAITING_FOR_LISTENING_ADDRESS;
    
    private PairListeningAddressToken value;
    
    private ListeningAddressReader laReader = new ListeningAddressReader();
    private IntReader intReader = new IntReader();
    private LISTENING_ADDRESS address;
    private int token;
    
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_LISTENING_ADDRESS) {
          var status = laReader.process(buffer);
          switch (status) {
            case DONE:           
              address = laReader.get();
              state = State.WAITING_FOR_TOKEN;
              laReader.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        
        var statut = intReader.process(buffer);
        if (statut == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        if (statut == ProcessStatus.DONE) {
          token = intReader.get();
        }
        
        state = State.DONE;
        value = new PairListeningAddressToken(address, token);
        return ProcessStatus.DONE;
    }

    @Override
    public PairListeningAddressToken get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_LISTENING_ADDRESS;
        intReader.reset();
    }
}