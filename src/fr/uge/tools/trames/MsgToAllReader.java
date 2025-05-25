package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;import fr.uge.tools.Tools;
import fr.uge.tools.object.StringReader;

public final class MsgToAllReader implements Reader<MsgToAllReader.MsgToAll>, Trame {
  
    public record MsgToAll(String message) {
      public ByteBuffer asBuffer() {
        var loginBb = Tools.stringToBuffer(message);
        
        var bb = ByteBuffer.allocate(loginBb.remaining());
        bb.put(loginBb);
        bb.flip();
        return bb;
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_MESSAGE
    }

    private State state = State.WAITING_FOR_MESSAGE;
    private MsgToAll value;
    
  
    private StringReader sr = new StringReader();
    private String message;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_MESSAGE) {
          var status = sr.process(buffer);
          switch (status) {
            case DONE:           
              message = sr.get().str();
              state = State.DONE;
              sr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        value = new MsgToAll(message);
        return ProcessStatus.DONE;
    }

    @Override
    public MsgToAll get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_MESSAGE;
    }
}