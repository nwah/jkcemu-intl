/*
 * (c) 2026 Noah Burney
 *
 * Kleincomputer-Emulator
 *
 * Emulation des FujiNet-Moduls
 *
 * Das Modul stellt einen 8K-ROM mit dem Strukturbyte 0xFB bereit.
 * Der ROM enthaelt gegenwaertig nur den Menueeintrag "FUJINET",
 * der sofort zurueckspringt (RET).
 *
 * Die Verbindung zum emulierten FujiNet-Geraet erfolgt wie beim
 * openMSX-Modul ueber eine TCP-Verbindung nach 127.0.0.1,
 * standardmaessig auf Port 1985.
 * Die Verbindung wird von einem Hintergrund-Thread aufgebaut
 * und bei einem Fehler einmal pro Sekunde erneut versucht.
 *
 * Der Datenaustausch erfolgt ueber vier Register,
 * die am oberen Ende des 8K-Fensters eingeblendet werden
 * und somit die letzten vier ROM-Bytes ueberdecken
 * (bei eingeblendetem Modul auf C000 also DFFC-DFFF):
 *
 *   +1FFC  GETC     (lesen)     naechstes empfangenes Byte
 *   +1FFD  STATUS   (lesen)     Bit 7: Empfangsdaten vorhanden
 *                               Bit 6: Verbindung besteht
 *   +1FFE  PUTC     (schreiben) Byte an das FujiNet-Geraet senden
 *   +1FFF  CONTROL  (schreiben) Bit 0: Empfangspuffer loeschen
 *
 * Anders als beim openMSX-Modul steuert das CONTROL-Register nicht
 * die Einblendung des ROMs, da das beim KC85 der Modulschacht
 * mit dem Strukturbyte bzw. dem SWITCH-Kommando erledigt.
 */

package jkcemu.emusys.kc85;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;
import jkcemu.Main;
import jkcemu.base.EmuThread;
import jkcemu.base.EmuUtil;


public class FujiNet extends KC85ROM8KModule implements Runnable
{
  public static final String MODULE_NAME = "FUJINET";
  public static final String DESCRIPTION = "FujiNet";

  public static final String PROP_PORT     = "jkcemu.fujinet.port";
  public static final String SYSPROP_DEBUG = "jkcemu.debug.fujinet";

  public static final int DEFAULT_PORT = 1985;

  // Masken fuer Eigenschaft SYSPROP_DEBUG
  private static final int DEBUG_MASK_MSG  = 0x01;
  private static final int DEBUG_MASK_DATA = 0x02;

  // Offsets der Register im 8K-Fenster
  private static final int IO_OFFS_GETC    = 0x1FFC;
  private static final int IO_OFFS_STATUS  = 0x1FFD;
  private static final int IO_OFFS_PUTC    = 0x1FFE;
  private static final int IO_OFFS_CONTROL = 0x1FFF;

  // Bits im STATUS-Register
  private static final int STATUS_DATA_AVAILABLE = 0x80;
  private static final int STATUS_CONNECTED      = 0x40;

  // Bits im CONTROL-Register
  private static final int CONTROL_CLEAR_RX = 0x01;

  private static final String ROM_RESOURCE      = "/rom/kc85/fujinet.bin";
  private static final int    RX_BUF_SIZE       = 0x40000;
  private static final int    RECONNECT_MILLIS  = 1000;

  private volatile int     port;
  private volatile boolean stopped;
  private volatile Socket  socket;
  private Thread           thread;
  private byte[]           rxBuf;
  private int              rxPos;
  private int              rxLen;
  private int              debugMask;


  public FujiNet( int slot, EmuThread emuThread, Properties props )
  {
    super( slot, emuThread, MODULE_NAME, ROM_RESOURCE );
    this.port      = getPortProp( props );
    this.stopped   = false;
    this.socket    = null;
    this.rxBuf     = new byte[ RX_BUF_SIZE ];
    this.rxPos     = 0;
    this.rxLen     = 0;
    this.debugMask = 0;

    String text = System.getProperty( SYSPROP_DEBUG );
    if( text != null ) {
      try {
	this.debugMask = Integer.parseInt( text );
      }
      catch( NumberFormatException ex ) {}
    }

    this.thread = new Thread(
			Main.getThreadGroup(),
			this,
			"JKCEMU FujiNet" );
    this.thread.setDaemon( true );
    this.thread.start();
  }


	/* --- Runnable --- */

  /*
   * Der Thread haelt die Verbindung zum FujiNet-Geraet offen
   * und liest die empfangenen Daten in den Empfangspuffer.
   */
  @Override
  public void run()
  {
    byte[] buf = new byte[ 0x1000 ];
    while( !this.stopped ) {
      Socket socket = this.socket;
      if( socket == null ) {
	socket = connect();
	if( socket == null ) {
	  try {
	    Thread.sleep( RECONNECT_MILLIS );
	  }
	  catch( InterruptedException ex ) {}
	  continue;
	}
      }
      try {
	InputStream in = socket.getInputStream();
	int         n  = in.read( buf );
	if( n < 0 ) {
	  throw new IOException( "Verbindung beendet" );
	}
	if( n > 0 ) {
	  if( (this.debugMask & DEBUG_MASK_DATA) != 0 ) {
	    System.out.printf( "FujiNet: %d Bytes empfangen\n", n );
	  }
	  putRxBytes( buf, n );
	}
      }
      catch( IOException ex ) {
	closeSocket();
	if( !this.stopped ) {
	  try {
	    Thread.sleep( RECONNECT_MILLIS );
	  }
	  catch( InterruptedException ex2 ) {}
	}
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void appendEtcInfoHTMLTo( StringBuilder buf )
  {
    buf.append( "FujiNet-Ger\u00E4t: 127.0.0.1:" );
    buf.append( this.port );
    buf.append( this.socket != null ?
			" (verbunden)"
			: " (nicht verbunden)" );
  }


  @Override
  public void applySettings( Properties props )
  {
    int port = getPortProp( props );
    if( port != this.port ) {
      this.port = port;
      closeSocket();		// Verbindung wird neu aufgebaut
    }
  }


  @Override
  public void dispose()
  {
    this.stopped = true;
    closeSocket();
    Thread thread = this.thread;
    if( thread != null ) {
      thread.interrupt();
      try {
	thread.join( 1000 );
      }
      catch( InterruptedException ex ) {}
    }
  }


  @Override
  public int getLEDrgb()
  {
    int rv = KC85FrontFld.RGB_LED_DARK;
    if( this.enabled && (this.socket != null) ) {
      rv = KC85FrontFld.RGB_LED_YELLOW;
    } else if( this.enabled ) {
      rv = KC85FrontFld.RGB_LED_GREEN;
    } else if( this.socket != null ) {
      rv = KC85FrontFld.RGB_LED_RED;
    }
    return rv;
  }


  @Override
  public int peekMemByte( int addr )
  {
    int rv = -1;
    switch( getIOOffs( addr ) ) {
      case IO_OFFS_GETC:
	rv = peekRxByte();
	break;

      case IO_OFFS_STATUS:
	rv = getStatus();
	break;

      default:
	rv = super.peekMemByte( addr );
    }
    return rv;
  }


  @Override
  public int readMemByte( int addr )
  {
    int rv = -1;
    switch( getIOOffs( addr ) ) {
      case IO_OFFS_GETC:
	rv = readRxByte();
	if( (this.debugMask & DEBUG_MASK_DATA) != 0 ) {
	  System.out.printf( "FujiNet: GETC -> %02X\n", rv );
	}
	break;

      case IO_OFFS_STATUS:
	rv = getStatus();
	break;

      default:
	rv = super.readMemByte( addr );
    }
    return rv;
  }


  @Override
  public void reset( boolean powerOn )
  {
    clearRxBuf();
  }


  @Override
  public int writeMemByte( int addr, int value )
  {
    int rv = 0;
    switch( getIOOffs( addr ) ) {
      case IO_OFFS_PUTC:
	if( (this.debugMask & DEBUG_MASK_DATA) != 0 ) {
	  System.out.printf( "FujiNet: PUTC %02X\n", value & 0xFF );
	}
	send( value );
	rv = 2;
	break;

      case IO_OFFS_CONTROL:
	if( (value & CONTROL_CLEAR_RX) != 0 ) {
	  clearRxBuf();
	}
	rv = 2;
	break;

      default:
	rv = super.writeMemByte( addr, value );
    }
    return rv;
  }


	/* --- private Methoden --- */

  private void clearRxBuf()
  {
    synchronized( this.rxBuf ) {
      this.rxPos = 0;
      this.rxLen = 0;
    }
  }


  private void closeSocket()
  {
    Socket socket = this.socket;
    this.socket   = null;
    if( socket != null ) {
      if( (this.debugMask & DEBUG_MASK_MSG) != 0 ) {
	System.out.println( "FujiNet: Verbindung geschlossen" );
      }
      EmuUtil.closeSilently( socket );
    }
  }


  private Socket connect()
  {
    Socket socket = null;
    try {
      socket = new Socket();
      socket.connect(
	new InetSocketAddress( InetAddress.getLoopbackAddress(), this.port ) );
      this.socket = socket;
      if( (this.debugMask & DEBUG_MASK_MSG) != 0 ) {
	System.out.printf(
		"FujiNet: verbunden mit 127.0.0.1:%d\n",
		this.port );
      }
    }
    catch( IOException ex ) {
      EmuUtil.closeSilently( socket );
      socket = null;
    }
    return socket;
  }


  /*
   * Die Methode liefert den Offset der angesprochenen
   * Registeradresse im 8K-Fenster oder -1,
   * wenn die Adresse kein Register des Moduls ist.
   */
  private int getIOOffs( int addr )
  {
    int rv = -1;
    if( this.enabled ) {
      int begAddr = getBegAddr();
      if( (addr >= (begAddr + IO_OFFS_GETC))
	  && (addr <= (begAddr + IO_OFFS_CONTROL)) )
      {
	rv = addr - begAddr;
      }
    }
    return rv;
  }


  private static int getPortProp( Properties props )
  {
    int rv = EmuUtil.getIntProperty( props, PROP_PORT, DEFAULT_PORT );
    if( (rv < 1) || (rv > 0xFFFF) ) {
      rv = DEFAULT_PORT;
    }
    return rv;
  }


  private int getStatus()
  {
    int rv = 0;
    synchronized( this.rxBuf ) {
      if( this.rxLen > 0 ) {
	rv |= STATUS_DATA_AVAILABLE;
      }
    }
    if( this.socket != null ) {
      rv |= STATUS_CONNECTED;
    }
    return rv;
  }


  private int peekRxByte()
  {
    int rv = 0;
    synchronized( this.rxBuf ) {
      if( this.rxLen > 0 ) {
	rv = (int) this.rxBuf[ this.rxPos ] & 0xFF;
      }
    }
    return rv;
  }


  private void putRxBytes( byte[] buf, int len )
  {
    synchronized( this.rxBuf ) {
      /*
       * Wenn der Empfangspuffer voll ist, werden die neuen Daten verworfen.
       * Zuvor wird der bereits gelesene Bereich am Anfang des Puffers
       * durch Zusammenschieben wieder nutzbar gemacht.
       */
      if( (this.rxPos + this.rxLen + len) > this.rxBuf.length ) {
	System.arraycopy( this.rxBuf, this.rxPos, this.rxBuf, 0, this.rxLen );
	this.rxPos = 0;
      }
      int n = Math.min( len, this.rxBuf.length - this.rxPos - this.rxLen );
      if( n > 0 ) {
	System.arraycopy(
			buf,
			0,
			this.rxBuf,
			this.rxPos + this.rxLen,
			n );
	this.rxLen += n;
      }
    }
  }


  private int readRxByte()
  {
    int rv = 0;
    synchronized( this.rxBuf ) {
      if( this.rxLen > 0 ) {
	rv = (int) this.rxBuf[ this.rxPos ] & 0xFF;
	this.rxPos++;
	--this.rxLen;
	if( this.rxLen == 0 ) {
	  this.rxPos = 0;
	}
      }
    }
    return rv;
  }


  private void send( int value )
  {
    Socket socket = this.socket;
    if( socket != null ) {
      try {
	OutputStream out = socket.getOutputStream();
	out.write( value & 0xFF );
	out.flush();
      }
      catch( IOException ex ) {
	closeSocket();		// Verbindung wird neu aufgebaut
      }
    }
  }
}
