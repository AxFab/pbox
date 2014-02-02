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
package ax.data;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.net.*;
import ax.util.*;
import ax.net.BeamSocket;
import ax.io.BeamFormatException;

public class WebService implements Runnable
{
  private DirectoryMirror dirMirror;
  private Path topDir;

  private String hostName;
  private int portNumber = 9620;

  public WebService (DirectoryMirror dm)
  {
    this.dirMirror = dm;
    this.topDir = dm.getTopDir();
  }

  public void answer (BufferedReader in, PrintWriter out)
  {

  }

  private void request (BeamSocket beam)
      throws BeamFormatException, IOException
  {
    String hash= null;
    String label;
    beam.in.readObject ();    
    while ((label = beam.in.readLabel ()) != null) {
      switch (label) {

        case "hash":
          hash = beam.in.readString ();
          break;

        default:
          beam.in.readPass ();
          break;
      }
    }

    if (hash == null) {
      beam.out.writeOpenObject ("err");
      beam.out.writeString ("msg", "Missing arg 'hash'");
      beam.out.writeCloseObject ();
    }

    File fp = new File (topDir + "/.pbox/" +hash.substring(0, 2)+ "/" + hash.substring(2));
    if (fp.exists()) {
      beam.out.writeOpenObject ("data");
      beam.out.writeString ("hash", hash);
      beam.out.writeFile ("record", fp);
      beam.out.writeCloseObject ();
    } else {
      beam.out.writeOpenObject ("err");
      beam.out.writeString ("msg", "File doesn't exist");
      beam.out.writeCloseObject ();
    }
  }

  private void update (BeamSocket beam)
      throws BeamFormatException, IOException
  {
    String label = null;
    String url = null;
    String pHash = null;
    String hash = null;
    String type = null;
    long version = 0;

    beam.in.readObject ();
    while ((label = beam.in.readLabel ()) != null) {
      switch (label) {

        case "url":
          url = beam.in.readString ();
          break;

        case "parent":
          pHash = beam.in.readString ();
          break;

        case "hash":
          hash = beam.in.readString ();
          break;

        case "version":
          version = beam.in.readInteger ();
          break;

        case "type":
          type = beam.in.readString ();
          break;

        default:
          beam.in.readPass ();
          break;
      }
    }

    Path path = Paths.get (url);
    System.out.format ("[Debug] Received update about: %s\n", path);
    FileEntry fpEn = this.dirMirror.dataService.getFileEntry (path);

    if (fpEn == null) {
      fpEn = new FileEntry(path, hash, pHash, version, type, this.topDir);
      this.dirMirror.dataService.addFileEntry (fpEn, path);
    } else {
      fpEn.extern (hash, pHash, version);
    }


    beam.out.writeOpenObject ("err");
    beam.out.writeString ("msg", "Hein !?");
    beam.out.writeCloseObject ();
  }

  public void receiveRequest (BeamSocket beam)
      throws BeamFormatException, IOException
  {
    System.out.format ("[Trace] Receive socket \n");
    String label = beam.in.readLabel ();

    System.out.format ("[Trace] Receive request %s\n", label);
    if (label.equals ("request")) {
      request (beam);
    } else if (label.equals ("update")) {
      update (beam);
    } else if (label.equals ("ping")) {
      beam.in.readPass();
      beam.out.writeString ("pong", null);
    } else {
      beam.out.writeOpenObject ("err");
      beam.out.writeString ("msg", "Hein !?");
      beam.out.writeCloseObject ();
    }
  }

  public void listen () {

    ServerSocket serverSocket = null;
    
    for (;;) {
      try { 
        if (serverSocket == null) {
          System.out.format ("[Trace] Start listen at port %d\n", portNumber);
          serverSocket = new ServerSocket(portNumber);
        } 

        Socket clientSocket = serverSocket.accept();
        BeamSocket beam = new BeamSocket (clientSocket);
        receiveRequest (beam); 
        beam.send ();
        beam.close ();

      } catch (BeamFormatException ex) {
        System.out.format ("[Err.] Web bad request: %s\n", ex.getMessage());
      } catch (IOException ex) {
        System.out.format ("[Err.] WebService: %s\n", ex.getMessage());
        //serverSocket = null;
        try {
          Thread.sleep (3500);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  public void sendPing (String host, int port) 
  {
    try { 
      StopWatch chrono = new StopWatch();
      BeamSocket beam = BeamSocket.connectTo (host, port);
      chrono.start();
      beam.out.writeString("ping", null);
      beam.send ();
      String res = beam.in.readLabel();
      beam.in.readPass();
      chrono.stop();
      System.out.format ("[Trace] Ping %s:%d - %s {in:%dms}\n", host, port, res, chrono.getMillis());

    } catch (BeamFormatException ex) {
      System.out.format ("[Err.] Erorr format response %s\n", ex.getMessage());
    } catch (UnknownHostException ex) {
      System.out.format ("[Err.] Unknown host %s\n", ex.getMessage());
    } catch (IOException ex) {
      System.out.format ("[Err.] IOError %s\n", ex.getMessage());
    }
  }

  public void setPort (int port) 
  {
    this.portNumber = port;
  }

  private String remHost = null;
  private int remPort;


  public void addRemote (String name) 
  {
    int k = name.indexOf (':');
    remHost = name.substring(0, k);
    remPort = Integer.parseInt (name.substring(k+1));
    System.out.format ("[Trace] Register end-point at %s port %d\n",
        remHost, remPort);

    sendPing (remHost, remPort);
  }

  public void run () 
  {
    System.out.println ("[Trace] WebService started");
    this.listen();
    System.out.println ("[Trace] WebService stopped");
  }

}
