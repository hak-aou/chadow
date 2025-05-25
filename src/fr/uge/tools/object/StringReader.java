package fr.uge.tools.object;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.object.ListReader.X;
import static fr.uge.tools.Tools.UTF8;

public class StringReader implements Reader<X> {

    public record StringBytes(String str) implements X {
      public ByteBuffer asBuffer() {
        var strBb = UTF8.encode(str);
        
        var bb = ByteBuffer.allocate(Integer.BYTES + strBb.remaining());
        bb.putInt(strBb.remaining());
        bb.put(strBb);
        bb.flip();
        return bb;
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_SIZE, WAITING_FOR_STRING
    };

    private State state = State.WAITING_FOR_SIZE;
    private ByteBuffer internalBuffer; // write-mode
    private StringBytes value;
    
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
            state = State.WAITING_FOR_STRING;
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
        value = new StringBytes(UTF8.decode(internalBuffer).toString());
        return ProcessStatus.DONE;
    }

    @Override
    public StringBytes get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_SIZE;
        sizeReader.reset();
    }
}