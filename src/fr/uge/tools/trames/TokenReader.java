package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.object.IntReader;

public final class TokenReader implements Reader<TokenReader.Token>, Trame {

  
    public record Token(int tokenSrc) {
      
      public ByteBuffer asBuffer() {
        var bb = ByteBuffer.allocate(Integer.BYTES);
        bb.putInt(tokenSrc);
        bb.flip();
        return bb;
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_TOKEN_SRC
    }

    private State state = State.WAITING_FOR_TOKEN_SRC;
    private Token value;
    
  
    private IntReader intReader = new IntReader();
    private int tokenSrc;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_TOKEN_SRC) {
          var status = intReader.process(buffer);
          if (status == ProcessStatus.REFILL) {
            return ProcessStatus.REFILL;
          }
          if (status == ProcessStatus.DONE) {
            tokenSrc = intReader.get();
          }
        }
                 
        state = State.DONE;
        value = new Token(tokenSrc);
        return ProcessStatus.DONE;
    }

    @Override
    public Token get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_TOKEN_SRC;
        intReader.reset();
    }
}