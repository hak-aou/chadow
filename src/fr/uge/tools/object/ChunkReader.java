package fr.uge.tools.object;

import fr.uge.tools.Reader;import java.nio.ByteBuffer;

public class ChunkReader implements Reader<ChunkReader.CHUNK> {
  
  
    public record CHUNK(ByteBuffer payload) {
     
      public ByteBuffer asBuffer() {
        var size = payload.remaining();
        
        var bb = ByteBuffer.allocate(Integer.BYTES + size);
        bb.putInt(size);
        bb.put(payload);
        bb.flip();
        return bb;
      }
    }

    private enum State {
      DONE, ERROR, WAITING_FOR_SIZE, WAITING_FOR_CONTENT
    };

    private State state = State.WAITING_FOR_SIZE;
    private ByteBuffer internalBuffer; // write-mode
    private CHUNK chunk;
    
    private IntReader sizeReader = new IntReader();
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }

        if (state == State.WAITING_FOR_SIZE) {
          var statut = sizeReader.process(buffer);
          if (statut == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (statut == ProcessStatus.DONE) {
            var size = sizeReader.get();
            if (size < 0 || size > 1024) {
              state = State.ERROR;
              return ProcessStatus.ERROR;
            }
            state = State.WAITING_FOR_CONTENT;
            internalBuffer = ByteBuffer.allocate(size);
          }
        }
        
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
        state = State.DONE;
        internalBuffer.flip();
        chunk = new CHUNK(internalBuffer);
        return ProcessStatus.DONE;
    }

    @Override
    public CHUNK get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return chunk;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_SIZE;
        sizeReader.reset();
    }
}