package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.ListReader;

public final class StopShareFilesReader implements Reader<StopShareFilesReader.StopShareFiles>,Trame {
  
    public record StopShareFiles(List<FILE> filesUnshared) {
      public ByteBuffer asBuffer() {
        return new LIST(filesUnshared.size(), new ArrayList<>(filesUnshared)).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILES_TO_STOP_SHARE
    }

    private State state = State.WAITING_FOR_FILES_TO_STOP_SHARE;
    private StopShareFiles value;
    
  
    private ListReader lr = new ListReader(new FileReader());
    private List<FILE> filesUnshare;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_FILES_TO_STOP_SHARE) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              filesUnshare = lr.get().list().stream().map(x -> (FILE) x).toList();
              state = State.DONE;
              lr.reset();
              break;
            case REFILL:
              return ProcessStatus.REFILL;
            case ERROR:
              state = State.ERROR;
              return ProcessStatus.ERROR;
          }
        }
        
        value = new StopShareFiles(filesUnshare);
        return ProcessStatus.DONE;
    }

    @Override
    public StopShareFiles get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_FILES_TO_STOP_SHARE;
    }
}