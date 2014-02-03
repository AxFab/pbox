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
      return;
    }

    File fp = FileEntry.buildFileRecord(topDir, hash);
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
    if (type.equals("Dir")) {
      System.out.format ("[Debug] Ignore update: <%s> %s\n",type, path);
    } else {
      System.out.format ("[Debug] Received update about: <%s> %s\n",type, path);
      FileEntry fpEn = this.dirMirror.dataService.getFileEntry (path);

      if (fpEn == null) {
        fpEn = new FileEntry(path, hash, pHash, version, type, this.topDir);
        this.dirMirror.dataService.addFileEntry (fpEn, path);
      } else {
        this.dirMirror.dataService.updateExFileEntry (fpEn, hash, pHash, version);
        // fpEn.extern (hash, pHash, version);
      }
    }

    beam.out.writeString ("ACK", null);
  }

  private void delete (BeamSocket beam)
      throws BeamFormatException, IOException
  {
    String label = null;
    String url = null;
    String hash = null;
    long version = 0;

    beam.in.readObject ();
    while ((label = beam.in.readLabel ()) != null) {
      switch (label) {

        case "url":
          url = beam.in.readString ();
          break;

        case "hash":
          hash = beam.in.readString ();
          break;

        case "version":
          version = beam.in.readInteger ();
          break;

        default:
          beam.in.readPass ();
          break;
      }
    }

    Path path = Paths.get (url);
    System.out.format ("[Debug] Received delete about: %s\n", path);
    FileEntry fpEn = this.dirMirror.dataService.getFileEntry (path);

    if (fpEn != null) {
      this.dirMirror.dataService.rmFileEntry (fpEn, path);
    }

    beam.out.writeString ("ACK", null);
    System.out.format ("[Debug] Ext. delete done: %s\n", path);
  }

  public void receiveRequest (BeamSocket beam)
      throws BeamFormatException, IOException
  {
    String label = beam.in.readLabel ();

    System.out.format ("[Trace] Receive request %s\n", label);
    if (label.equals ("request")) {
      request (beam);
    } else if (label.equals ("update")) {
      update (beam);
    } else if (label.equals ("delete")) {
      delete (beam);
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
      System.err.format ("[Err.] Erorr format response %s\n", ex.getMessage());
    } catch (UnknownHostException ex) {
      System.err.format ("[Err.] Unknown host %s\n", ex.getMessage());
    } catch (IOException ex) {
      System.err.format ("[Err.] IOError %s\n", ex.getMessage());
    }
  }

  private void readError (BeamSocket beam) 
      throws BeamFormatException, IOException
  {
    String label;
    String msg = null;
    beam.in.readObject();
    while ((label = beam.in.readLabel ()) != null) {
      switch (label) {

        case "msg":
          msg = beam.in.readString ();
          break;

        default:
          beam.in.readPass ();
          break;
      }
    }

    System.err.format ("[Err.] Server respond: %s\n", msg);
  }

  private void receiveFile (BeamSocket beam, Path top) 
      throws BeamFormatException, IOException
  {
    String label;
    String hash = null;
    beam.in.readObject();
    while ((label = beam.in.readLabel ()) != null) {
      switch (label) {

        case "hash":
          hash = beam.in.readString ();
          break;

        case "record":
          if (hash != null) {
            File fp = FileEntry.buildFileRecord(top, hash);
            fp.getParentFile().mkdirs();
            beam.in.readFile (fp);
          } else {
            beam.in.readPass ();
          }
        default:
          break;
      }
    }

  }

  public boolean get (String namespace, String object)
      throws IOException
  {
    try {

      System.out.format ("[Trace] Get file %s \n", object);
      System.out.format ("[Debug] Try connection %s:%s\n", remHost, remPort);
      BeamSocket beam = BeamSocket.connectTo (remHost, remPort);
      beam.out.writeOpenObject("request");
      beam.out.writeString("hash", object);
      beam.out.writeString("date", (new Date()).toString());
      beam.out.writeCloseObject();
      beam.send();

      String res = beam.in.readLabel();
      switch (res) {
        case "data":
          receiveFile (beam, this.topDir);
          System.out.format ("[Trace] Receive file %s \n", object);
          break;

        case "err":
          readError (beam);
          break;

        default:
          throw new BeamFormatException ("Unexpect response");
      }
      return true;
    } catch (BeamFormatException ex) {
      System.out.format ("[Err.] Erorr format response %s\n", ex.getMessage());
    } catch (UnknownHostException ex) {
      System.out.format ("[Err.] Unknown host %s\n", ex.getMessage());
    } catch (IOException ex) {
      System.out.format ("[Err.] IOError %s\n", ex.getMessage());
    }

    return false;
  }

  public void async (String namespace, String object, ILoadable bucket)
  {
    bucket.laodFinish (false, "Unable to load using this method");
  }

  public void peer (String namespace, String object, ILoadable bucket)
  {
    bucket.laodFinish (false, "Unable to load using this method");
  }


  public void sendUpdate (Path path, String hash, String pHash, long version, String type) 
  {
    try {
      // System.out.format("[update] %s -> %s\n", hash, path);

      BeamSocket beam = BeamSocket.connectTo (remHost, remPort);
      beam.out.writeOpenObject("update");
      beam.out.writeString("hash", hash);
      beam.out.writeString("url", path.toString());
      beam.out.writeString("parent", pHash);
      beam.out.writeInteger("version", version);
      beam.out.writeString("type", type);
      beam.out.writeCloseObject();
      beam.send();
      String res = beam.in.readLabel();
      switch (res) {
        case "ACK":
          beam.in.readPass();
          System.out.format ("[Trace] Update %s acknowledged\n", hash);
          break;

        case "err":
          readError (beam);
          break;

        default:
          throw new BeamFormatException ("Unexpect response");
      }

    } catch (BeamFormatException ex) {
      System.out.format ("[Err.] Erorr format response %s\n", ex.getMessage());
    } catch (UnknownHostException ex) {
      System.out.format ("[Err.] Unknown host %s\n", ex.getMessage());
    } catch (IOException ex) {
      System.out.format ("[Err.] IOError %s\n", ex.getMessage());
    }
  }


  public void sendDelete (Path path, String hash, long version)
  {
    try {
      // System.out.format("[update] %s -> %s\n", hash, path);

      BeamSocket beam = BeamSocket.connectTo (remHost, remPort);
      beam.out.writeOpenObject("delete");
      beam.out.writeString("hash", hash);
      beam.out.writeString("url", path.toString());
      beam.out.writeInteger("version", version);
      beam.out.writeCloseObject();
      beam.send();
      String res = beam.in.readLabel();
      switch (res) {
        case "ACK":
          beam.in.readPass();
          System.out.format ("[Trace] Delete %s acknowledged\n", hash);
          break;

        case "err":
          readError (beam);
          break;

        default:
          throw new BeamFormatException ("Unexpect response");
      }

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
    Thread.currentThread().setName ("WebService["+this.topDir.toString()+"]");
    System.out.println ("[Trace] WebService started");
    this.listen();
    System.out.println ("[Trace] WebService stopped");
  }

}
