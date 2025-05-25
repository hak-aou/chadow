package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ChunkReader;
import fr.uge.tools.object.IntReader;
import fr.uge.tools.object.ChunkReader.CHUNK;
import fr.uge.tools.object.FileReader.FILE;

public final class HidenResponseReader implements Reader<HidenResponseReader.HidenResponse>, Trame {
  
  
    public record HidenResponse(FILE file, int chunkNumber, CHUNK chunk, int token)  {
      
      public ByteBuffer asBuffer() {
        var fileBb = file.asBuffer();
        var chunkBb = chunk.asBuffer();
        
        var bb = ByteBuffer.allocate(fileBb.remaining() + Integer.BYTES + chunkBb.remaining() + Integer.BYTES);
        bb.put(fileBb);
        bb.putInt(chunkNumber);
        bb.put(chunkBb);
        bb.putInt(token);
        bb.flip();
        return bb;
      }
    };

    
    private enum State {
        DONE, ERROR, WAITING_FOR_REQUEST, WAITING_FOR_CHUNK, WAITING_FOR_TOKEN
    };


    private State state = State.WAITING_FOR_REQUEST;
   
    
    private HidenResponse value;
    
    private OpenRequestReader openRequestReader = new OpenRequestReader();
    private ChunkReader chunkReader = new ChunkReader();
    private IntReader intReader = new IntReader();
    private FILE file;
    private int chunkNumber;
    private CHUNK chunk;
    private int token;
    
    
    
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
        
        if (state == State.WAITING_FOR_CHUNK) {
          var status = chunkReader.process(buffer);
          switch (status) {
            case DONE:           
              chunk = chunkReader.get();
              state = State.WAITING_FOR_TOKEN;
              chunkReader.reset();
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
        value = new HidenResponse(file, chunkNumber, chunk, token);
        return ProcessStatus.DONE;
    }

    @Override
    public HidenResponse get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_REQUEST;
        intReader.reset();
    }
}