package fr.uge.tools;

import java.nio.ByteBuffer;

public record TrameBytes(byte opcode, ByteBuffer buffer) {

  public ByteBuffer asBuffer() {
    var opcodeBb = Tools.byteToBuffer(opcode);
    
    var bb = ByteBuffer.allocate(opcodeBb.remaining() + buffer.remaining());
    bb.put(opcodeBb).put(buffer);
    bb.flip();
    return bb;
  }
}