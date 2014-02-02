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
import java.util.zip.DeflaterOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BeamOut
{
  private DeflaterOutputStream out;
  private MessageDigest key;

  public BeamOut(File data) 
      throws FileNotFoundException
  {
    this.out = new DeflaterOutputStream(new FileOutputStream(data));
    try {
    this.key = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
    }
  }

  public BeamOut(OutputStream data) 
      throws FileNotFoundException
  {
    this.out = new DeflaterOutputStream(data);
    try {
    this.key = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
    }
  }

  public void writeOpenObject (String tag) 
      throws IOException
  {
    writeLabel (tag);
    writeByte (0x09);
  }
  
  public void writeCloseObject () 
      throws IOException
  {
    writeByte (0x00); 
  }

  private void writeByte (int value)
      throws IOException
  {
    key.update ((byte)(value & 0xff));
    out.write ((byte)value & 0xff);
  }

  private void writeBytes (byte[] data, int length)
      throws IOException
  {
    key.update (data, 0, length);
    out.write (data, 0, length);
  }

  private void writeU29 (int value) 
      throws IOException
  {
    for (;;) {
      int d = value >> 7;
      if (value <= 0) {
        writeByte (value & 0x7f);
        return;
      } else {
        writeByte ((value & 0x7f) | 0x80);
        value = d;
      }
    }
  }

  private void writeLabel (String label) 
      throws IOException
  {
    byte[] data = label.getBytes();
    writeU29 (data.length);
    writeBytes (data, data.length);
  }

  public void writeOpenCollection (String tag) 
      throws IOException
  {
    writeLabel (tag);
    writeByte (0x08);
  }
  
  public void writeCloseCollection () 
      throws IOException
  {
    writeByte (0x00); 
  }

  public void writeString (String label, String value)
      throws IOException
  {
    writeLabel (label);
    writeByte (0x06);
    if (value == null)
      writeByte (0x00);
    else
      writeLabel (value);
  }

  public void writeInteger (String label, long value)
      throws IOException
  {
    writeLabel (label);
    writeByte (0x03);
    byte[] data = BeamHelper.longToBytes(value);
    writeBytes (data, data.length);
  }

  public void writeNumber (String label, double value)
      throws IOException
  {
    writeLabel (label);
    writeByte (0x04);
    // TODO
  }

  public void writeDate (String label, Date value)
      throws IOException
  {
    writeLabel (label);
    writeByte (0x07);
    // TODO
  }

  public void writeBoolean (String label, boolean value)
      throws IOException
  {
    writeLabel (label);
    writeByte (value ? 0x01 : 0x02);
  }

  public void writeBytes (String label, byte[] bytes)
      throws IOException
  {
    writeLabel (label);
    writeByte (0x05);
    writeU29 (bytes.length);
    writeBytes (bytes, bytes.length);
  }

  public void writeFile (String label, File raw)
      throws IOException
  {
    byte[] buffer = new byte[1000];
    int lg = (int)raw.length();
    if (raw.length () > Integer.MAX_VALUE) {
      throw new IOException ("File is too big");
    }
    writeLabel (label);
    writeByte (0x05);
    writeU29 (lg);

    InputStream in = new FileInputStream(raw);
    while((lg = in.read(buffer)) > 0) {
      writeBytes(buffer, lg);
    }

    in.close();
  }


  public void close () 
      throws IOException
  {
    out.close ();
  }

  public void flush() 
      throws IOException
  {
    this.out.finish();
    this.out.flush();
  }


  public String commit () 
  {
    byte[] hash = key.digest ();
    return BeamHelper.bytesToString (hash);
  }

}

