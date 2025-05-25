package fr.uge.tools.trames;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.Tools;
import fr.uge.tools.object.StringReader;

public final class MsgReceivedReader implements Reader<MsgReceivedReader.MsgReceived>,Trame {
  
    public record MsgReceived(String pseudoSrc, String message) {
      public ByteBuffer asBuffer() {
        var pseudoSrcBb = Tools.stringToBuffer(pseudoSrc);
        var messageBb = Tools.stringToBuffer(message);

        var bb = ByteBuffer.allocate(pseudoSrcBb.remaining() + messageBb.remaining());
        bb.put(pseudoSrcBb).put(messageBb);
        bb.flip();
        return bb;
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_PSEUDO_DST, WAITING_FOR_MESSAGE
    }

    private State state = State.WAITING_FOR_PSEUDO_DST;
    private MsgReceived value;
    
    private StringReader sr = new StringReader();
    private String pseudoSrc;
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
              pseudoSrc = sr.get().str();
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
        
        value = new MsgReceived(pseudoSrc, message);
        return ProcessStatus.DONE;
    }

    @Override
    public MsgReceived get() {
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