package fr.uge.tools.trames;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.uge.tools.Reader;
import fr.uge.tools.object.FileReader;
import fr.uge.tools.object.FileReader.FILE;
import fr.uge.tools.object.ListReader.LIST;
import fr.uge.tools.object.ListReader;

public final class ShareFilesReader implements Reader<ShareFilesReader.ShareFiles>,Trame {
  
    public record ShareFiles(List<FILE> filesShared) {
      public ByteBuffer asBuffer() {
        return new LIST(filesShared.size(), new ArrayList<>(filesShared)).asBuffer();
      }
    }
    
    private enum State {
        DONE, ERROR, WAITING_FOR_FILES_TO_SHARE
    }

    private State state = State.WAITING_FOR_FILES_TO_SHARE;
    private ShareFiles value;
    
  
    private ListReader lr = new ListReader(new FileReader());
    private List<FILE> filesToShare;
    
    
    @Override
    public ProcessStatus process(ByteBuffer buffer) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        
        if (state == State.WAITING_FOR_FILES_TO_SHARE) {
          var status = lr.process(buffer);
          switch (status) {
            case DONE:           
              filesToShare = lr.get().list().stream().map(x -> (FILE) x).toList();
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
        
        value = new ShareFiles(filesToShare);
        return ProcessStatus.DONE;
    }

    @Override
    public ShareFiles get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_FILES_TO_SHARE;
    }
}