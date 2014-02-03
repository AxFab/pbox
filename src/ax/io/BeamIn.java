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

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.zip.InflaterInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BeamIn
{
  private InputStream in;
  private MessageDigest key;

  public BeamIn(File data) 
      throws FileNotFoundException
  {
    this.in = new InflaterInputStream(new FileInputStream(data));
    try {
    this.key = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
    }
  }

  public BeamIn(InputStream data) 
      throws FileNotFoundException
  {
    this.in = new InflaterInputStream(data);
    try {
    this.key = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
    }
  }

  private int readU29 () 
      throws IOException
  { 
    int k = 0;
    int value = 0;
    for (;;) {
      int digits = in.read ();
      key.update ((byte)digits);
      value |= (digits & 0x7f) << k;
      if ((digits & 0x80) == 0x80) {
        k += 7;
        continue;
      }

      return value;
    }
  }

  public String readLabel () 
      throws IOException
  {
    int length = readU29 ();
    if (length <= 0)
      return null;
    byte[] buffer = new byte[length];
    in.read(buffer, 0, length);
    key.update (buffer, 0, length);
    return new String (buffer);
  }

  private int readMark (int mark1, int mark2) 
      throws IOException, BeamFormatException
  {
    in.mark (1);
    int mark = in.read ();
    if (mark != mark1 && mark != mark2) {
      in.reset();
      throw new BeamFormatException(); 
    }

    key.update ((byte)mark);
    return mark;
  }

  private int readMark (int mark1) 
      throws IOException, BeamFormatException
  {
    in.mark (1);
    int mark = in.read ();
    if (mark != mark1) {
      in.reset();
      throw new BeamFormatException(); 
    }

    key.update ((byte)mark);
    return mark;
  }

  public void readObject ()
      throws IOException, BeamFormatException
  {
    readMark (0x09);
  }

  public void readCollection ()
      throws IOException, BeamFormatException
  {
    readMark (0x08);
  }

  public String readString ()
      throws IOException, BeamFormatException
  {
    readMark (0x06);
    return readLabel ();
  }

  public long readInteger ()
      throws IOException, BeamFormatException
  {
    readMark (0x03);
    byte[] buffer = new byte[8];
    in.read(buffer, 0, 8);
    key.update (buffer, 0, 8);
    return BeamHelper.bytesToLong(buffer);
  }

  public double readNumber ()
      throws IOException, BeamFormatException
  {
    readMark (0x04);
    // TODO
    return 0;
  }

  public Date readDate ()
      throws IOException, BeamFormatException
  {
    readMark (0x07);
    // TODO
    return null;
  }

  public boolean readBoolean ()
      throws IOException, BeamFormatException
  {
    return readMark (0x01, 0x02) == 0x01;
  }

  public byte[] readBytes ()
      throws IOException, BeamFormatException
  {
    readMark (0x05);
    int lg = readU29();
    byte data[] = new byte [lg];
    in.read(data, 0, lg);
    key.update (data, 0, lg);
    return data;
  }

  public void readFile (File raw)
      throws IOException, BeamFormatException
  {
    readMark (0x05);
    int len;
    int rest = readU29();

    byte buffer[] = new byte [1000];
    OutputStream out = new FileOutputStream(raw);

    while(rest > 0) {
      if (rest > 1000) {
        len = in.read(buffer);
        key.update (buffer, 0, 1000);
      } else {
        len = in.read(buffer, 0, rest);
        key.update (buffer, 0, rest);
      }
      
      if (len <=0 ) {
        out.close ();
        throw new BeamFormatException();
      }
      rest -= len;
      out.write(buffer, 0, len);
    }

    out.close ();
  }

  public void readPass () 
      throws BeamFormatException, IOException
  {
    int rest = 0;
    int len;
    int mark = in.read();
    key.update((byte)mark);

    switch (mark) {
      case 0x00: // NULL 
      case 0x01: // TRUE 
      case 0x02: // FALSE
        break;

      case 0x03: // INTEGER
        rest = 8;
        break;

      case 0x04: // Not Implem  Number
      case 0x07: // Not Implem  Date
        break;

      case 0x06: // STRING
      case 0x05: // BYTES
        rest = readU29();
        break;

      case 0x08: // Array
      case 0x09: // Object
        throw new BeamFormatException();
    }


    byte buffer[] = new byte [1000];
    while(rest > 0) {
      if (rest > 1000) {
        len = in.read(buffer);
        key.update (buffer, 0, 1000);
      } else {
        len = in.read(buffer, 0, rest);
        key.update (buffer, 0, rest);
      }
      
      if (len <=0 ) {
        throw new BeamFormatException();
      }
      rest -= len;
    }
  }

  public void close () 
      throws IOException
  {
    in.close ();
  }

  public String commit () 
  {
    byte[] hash = key.digest ();
    return BeamHelper.bytesToString (hash);
  }

}

