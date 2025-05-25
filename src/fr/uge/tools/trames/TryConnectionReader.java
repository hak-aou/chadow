package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import fr.uge.tools.Reader;
import fr.uge.tools.Tools;
import fr.uge.tools.object.ListeningAddressReader;
import fr.uge.tools.object.ListeningAddressReader.LISTENING_ADDRESS;
import fr.uge.tools.object.StringReader;

public final class TryConnectionReader implements Reader<TryConnectionReader.TryConnection>,Trame {

  
    public record TryConnection(String login, LISTENING_ADDRESS address) {
      
      public ByteBuffer asBuffer() {
        var loginBb =  Tools.stringToBuffer(login);
        var addressBb = address.asBuffer();
        
        var bb = ByteBuffer.allocate(loginBb.remaining() + addressBb.remaining());
        bb.put(loginBb);
        bb.put(addressBb);
        bb.flip();
        return bb;
      }
    }
    

    
    private enum State {
        DONE, ERROR, WAITING_FOR_LOGIN, WAITING_FOR_LISTENING_ADDRESS
    }

    private State state = State.WAITING_FOR_LOGIN;
    private TryConnection value;
    
  
    private StringReader sr = new StringReader();
    private ListeningAddressReader laReader = new ListeningAddressReader();
    private String login;
    private LISTENING_ADDRESS address;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_LOGIN) {
          var status = sr.process(buffer);
          switch (status) {
            case DONE:           
              login = sr.get().str();
              state = State.WAITING_FOR_LISTENING_ADDRESS;
              sr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
         
        var status = laReader.process(buffer);
        switch (status) {
          case DONE:           
            address = laReader.get();
            state = State.DONE;
            laReader.reset();
            break;
          case REFILL:
            return ProcessStatus.REFILL;
          case ERROR:
            state = State.ERROR;
            return ProcessStatus.ERROR;
        }
        
        value = new TryConnection(login, address);
        return ProcessStatus.DONE;
    }

    @Override
    public TryConnection get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_LOGIN;
    }
}