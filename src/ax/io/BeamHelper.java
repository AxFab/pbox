/*
      pbox - personnal/private cloud box
    Copyright (C) 2014 <Fabien Bavent>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package ax.io;

import java.nio.ByteBuffer;

public class BeamHelper 
{
  public static byte[] longToBytes(long x) {
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.putLong(x);
    return buffer.array();
  }

  public static long bytesToLong(byte[] bytes) 
  {
    ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.put(bytes);
    buffer.flip();//need flip 
    return buffer.getLong();
  }

  public static String bytesToString (byte[] hash) 
  {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < hash.length; i++) {
        sb.append(Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1));
    }

    return sb.toString();
  }


}