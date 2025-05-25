package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.IntReader;
import fr.uge.tools.object.FileReader.FILE;

public final class OpenRequestReader implements Reader<OpenRequestReader.OpenRequest>, Trame {
  
  
    public record OpenRequest(FILE file, int chunkNumber)  {
      
      public ByteBuffer asBuffer() {
        var fileBb = file.asBuffer();
        
        var bb = ByteBuffer.allocate(fileBb.remaining() +  Integer.BYTES);
        bb.put(fileBb);
        bb.putInt(chunkNumber);
        bb.flip();
        return bb;
      }
    };

    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILE, WAITING_FOR_CHUNKNUMBER
    };


    private State state = State.WAITING_FOR_FILE;
   
    
    private OpenRequest value;
    
    private FileReader fileReader = new FileReader();
    private IntReader intReader = new IntReader();
    private FILE file;
    private int chunkNumber;
    
    
    
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
        }
        
        state = State.DONE;
        value = new OpenRequest(file, chunkNumber);
        return ProcessStatus.DONE;
    }

    @Override
    public OpenRequest get() {
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