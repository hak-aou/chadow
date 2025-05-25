package fr.uge.tools.object;

import java.nio.ByteBuffer;

import fr.uge.tools.Reader;
import fr.uge.tools.Tools;

public class MessageReader implements Reader<MessageReader.Message> {

    public record Message(String login, String text) {
      
      public ByteBuffer asBuffer() {
        var loginBb =  Tools.stringToBuffer(login);
        var textBb = Tools.stringToBuffer(text);
        
        var bb = ByteBuffer.allocate(loginBb.remaining() + textBb.remaining());
        bb.put(loginBb).put(textBb);
        bb.flip();
        return bb;
      }
    };
    

    
    private enum State {
        DONE, ERROR, WAITING_FOR_LOGIN, WAITING_FOR_TEXT
    };

    private State state = State.WAITING_FOR_LOGIN;
    private Message msg;
    
  
    private StringReader sr = new StringReader();
    private String login; 
    private String text;

    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        for(;;) {
          var status = sr.process(buffer);
          switch (status) {
            case DONE:
              var str = sr.get().str();
              
              if (state == State.WAITING_FOR_LOGIN) {
                login = str;
                state = State.WAITING_FOR_TEXT;
              }
              else if (state == State.WAITING_FOR_TEXT) {
                text = str;
                state = State.DONE;
              }

              sr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
          
          if (state == State.DONE) {
            msg = new Message(login, text);
            return ProcessStatus.DONE;
          }
          
        }
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return msg;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_LOGIN;
    }
}