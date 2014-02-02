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
package ax.net;

import java.util.zip.*;
import java.io.*;
import java.net.*;
import ax.io.BeamIn;
import ax.io.BeamOut;

public class BeamSocket
{
  public final BeamIn in;
  public final BeamOut out;
  public final Socket socket;

  public BeamSocket(Socket socket) 
      throws IOException
  {
    this.socket = socket;
    this.in = new BeamIn(socket.getInputStream());
    this.out = new BeamOut(socket.getOutputStream());
  }

  public static BeamSocket connectTo (String hostname, int port) 
      throws UnknownHostException, IOException
  {
    Socket kkSocket = new Socket(hostname, port);
    return new BeamSocket (kkSocket);
  }

  public static BeamSocket listen (ServerSocket serverSocket) 
      throws IOException
  {
    Socket clientSocket = serverSocket.accept();
    return new BeamSocket (clientSocket);
  }

  public void send() 
      throws IOException
  {
    this.out.flush();
    this.socket.getOutputStream().flush();
  }

  public void close ()
      throws IOException
  {
    //this.out.close();
    //this.in.close();
    this.socket.close ();
  }
}

