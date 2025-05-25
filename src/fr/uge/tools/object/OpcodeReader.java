package fr.uge.tools.object;

import fr.uge.tools.Reader;import java.nio.ByteBuffer;

public class OpcodeReader implements Reader<Byte> {

//    public record OPCODE(byte code) {
//
//      public ByteBuffer asBuffer() {
//        var bb = ByteBuffer.allocate(1);
//        bb.put(code);
//        bb.flip();
//        return bb;
//      }
//    }
  
    private enum State {
        DONE, WAITING, ERROR
    };

    private State state = State.WAITING;
    private byte value;

    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        buffer.flip();
        try {
          if (!buffer.hasRemaining()) {
            return ProcessStatus.REFILL;
          }
          
          state = State.DONE;
          value = buffer.get();
          return ProcessStatus.DONE;
          
        } finally {
          buffer.compact();
        }
    }

    @Override
    public Byte get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING;
    }
}