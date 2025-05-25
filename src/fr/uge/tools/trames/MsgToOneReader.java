package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;import fr.uge.tools.Tools;
import fr.uge.tools.object.StringReader;

public final class MsgToOneReader implements Reader<MsgToOneReader.MsgToOne>,Trame {
  
    public record MsgToOne(String pseudoDst, String message) {
      public ByteBuffer asBuffer() {
        var pseudoDstBb = Tools.stringToBuffer(pseudoDst);
        var messageBb = Tools.stringToBuffer(message);

        var bb = ByteBuffer.allocate(pseudoDstBb.remaining() + messageBb.remaining());
        bb.put(pseudoDstBb).put(messageBb);
        bb.flip();
        return bb;
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_PSEUDO_DST, WAITING_FOR_MESSAGE
    }

    private State state = State.WAITING_FOR_PSEUDO_DST;
    private MsgToOne value;
    
    private StringReader sr = new StringReader();
    private String pseudoDst;
    private String message;
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_PSEUDO_DST) {
          var status = sr.process(buffer);
          switch (status) {
            case DONE:
              pseudoDst = sr.get().str();
              state = State.WAITING_FOR_MESSAGE;
              sr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
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
        
        value = new MsgToOne(pseudoDst, message);
        return ProcessStatus.DONE;
    }

    @Override
    public MsgToOne get() {
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