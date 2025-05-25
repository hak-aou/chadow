package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ChunkReader;
import fr.uge.tools.object.ChunkReader.CHUNK;
import fr.uge.tools.object.FileReader.FILE;

public final class OpenResponseReader implements Reader<OpenResponseReader.OpenResponse>, Trame {
  
  
    public record OpenResponse(FILE file, int chunkNumber, CHUNK chunk)  {
      
      public ByteBuffer asBuffer() {
        var fileBb = file.asBuffer();
        var chunkBb = chunk.asBuffer();
        
        var bb = ByteBuffer.allocate(fileBb.remaining() + Integer.BYTES + chunkBb.remaining());
        bb.put(fileBb);
        bb.putInt(chunkNumber);
        bb.put(chunkBb);
        bb.flip();
        return bb;
      }
    };

    
    private enum State {
        DONE, ERROR, WAITING_FOR_REQUEST, WAITING_FOR_CHUNK
    };


    private State state = State.WAITING_FOR_REQUEST;
   
    
    private OpenResponse value;
    
    private OpenRequestReader openRequestReader = new OpenRequestReader();
    private ChunkReader chunkReader = new ChunkReader();
    private FILE file;
    private int chunkNumber;
    private CHUNK chunk;
    
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_REQUEST) {
          var status = openRequestReader.process(buffer);
          switch (status) {
            case DONE:           
              var request = openRequestReader.get();
              file = request.file();
              chunkNumber = request.chunkNumber();
              state = State.WAITING_FOR_CHUNK;
              openRequestReader.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
                
        var status = chunkReader.process(buffer);
        switch (status) {
          case DONE:           
            chunk = chunkReader.get();
            chunkReader.reset();
            break;
          case REFILL:
            return ProcessStatus.REFILL;
          case ERROR:
            state = State.ERROR;
            return ProcessStatus.ERROR;
        }
        
        state = State.DONE;
        value = new OpenResponse(file, chunkNumber, chunk);
        return ProcessStatus.DONE;
    }

    @Override
    public OpenResponse get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_REQUEST;
    }
}