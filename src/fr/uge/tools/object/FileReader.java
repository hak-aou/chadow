package fr.uge.tools.object;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.Tools;
import fr.uge.tools.object.ListReader.X;

public class FileReader implements Reader<X> {
  
  
    public record FILE(String filename, ByteBuffer id, int size) implements X {
      
      public ByteBuffer asBuffer() {
        var nameBb = Tools.stringToBuffer(filename);
        var copyId = id.duplicate(); // important
        
        var bb = ByteBuffer.allocate(nameBb.remaining() + copyId.remaining() + Integer.BYTES);
        bb.put(nameBb);
        bb.put(copyId);
        bb.putInt(size);
        bb.flip();
        return bb;
      }
    };

    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILENAME, WAITING_FOR_ID, WAITING_FOR_SIZE
    };


    private State state = State.WAITING_FOR_FILENAME;
    private ByteBuffer internalBuffer; // write-mode
    
    private FILE value;
    
    private StringReader sr = new StringReader();
    private IntReader sizeReader = new IntReader();
    private String filename;
    private int size;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        
        if (state == State.WAITING_FOR_FILENAME) {
          var status = sr.process(buffer);
          switch (status) {
            case DONE:           
              filename = sr.get().str();
              state = State.WAITING_FOR_ID;
              internalBuffer = ByteBuffer.allocate(16); // important
              sr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
                
        if (state == State.WAITING_FOR_ID) {
          buffer.flip();
          try {
              if (buffer.remaining() <= internalBuffer.remaining()) {
                  internalBuffer.put(buffer);
              } else {
                  var oldLimit = buffer.limit();
                  buffer.limit(internalBuffer.remaining());
                  internalBuffer.put(buffer);
                  buffer.limit(oldLimit);
              }
          } finally {
              buffer.compact();
          }
          if (internalBuffer.hasRemaining()) {
              return ProcessStatus.REFILL;
          }
          state = State.WAITING_FOR_SIZE;
        }
        
        
        var statut = sizeReader.process(buffer);
        if (statut == ProcessStatus.REFILL) {
          return ProcessStatus.REFILL;
        }
        if (statut == ProcessStatus.DONE) {
          size = sizeReader.get();
          if (size < 0) {
            state = State.ERROR;
            return ProcessStatus.ERROR;
          }        
        }
        
        state = State.DONE;
        internalBuffer.flip();
        value = new FILE(filename, internalBuffer, size);
        return ProcessStatus.DONE;
    }

    @Override
    public FILE get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_FILENAME;
        sizeReader.reset();
        internalBuffer.clear();
    }
}