package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.IntReader;
import fr.uge.tools.object.FileReader.FILE;

public final class HidenRequestDeniedReader implements Reader<HidenRequestDeniedReader.HidenRequestDenied>, Trame {
  
  
    public record HidenRequestDenied(FILE file, int chunkNumber, int token)  {
      
      public ByteBuffer asBuffer() {
        var fileBb = file.asBuffer();
        
        var bb = ByteBuffer.allocate(fileBb.remaining() +  (2 * Integer.BYTES));
        bb.put(fileBb);
        bb.putInt(chunkNumber);
        bb.putInt(token);
        bb.flip();
        return bb;
      }
    };

    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILE, WAITING_FOR_CHUNKNUMBER, WAITING_FOR_TOKEN
    };


    private State state = State.WAITING_FOR_FILE;
   
    
    private HidenRequestDenied value;
    
    private FileReader fileReader = new FileReader();
    private IntReader intReader = new IntReader();
    private FILE file;
    private int chunkNumber;
    private int token;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_FILE) {
          var status = fileReader.process(buffer);
          switch (status) {
            case DONE:           
              file = fileReader.get();
              state = State.WAITING_FOR_CHUNKNUMBER;
              fileReader.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        if (state == State.WAITING_FOR_CHUNKNUMBER) {
          var statut = intReader.process(buffer);
          if (statut == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (statut == ProcessStatus.DONE) {
            chunkNumber = intReader.get();
            if (chunkNumber < 0) {
              state = State.ERROR;
              return ProcessStatus.ERROR;
            }
            state = State.WAITING_FOR_TOKEN;
            intReader.reset();
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
        value = new HidenRequestDenied(file, chunkNumber, token);
        return ProcessStatus.DONE;
    }

    @Override
    public HidenRequestDenied get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_FILE;
        intReader.reset();
    }
}