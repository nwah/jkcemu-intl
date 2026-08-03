/*
 * (c) 2022 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des Digital In/Out Moduls M001
 * mit einem an der PIO angeschlossenen Plotter XY4131 / XY4140
 *
 * Der Plotter wird parallel an beiden PIO Ports emuliert.
 */

package jkcemu.emusys.kc85;

import java.util.Properties;
import jkcemu.base.EmuThread;
import jkcemu.etc.Plotter;
import z80emu.Z80CPU;
import z80emu.Z80CTC;
import z80emu.Z80InterruptSource;
import z80emu.Z80PIO;
import z80emu.Z80PIOPortListener;


public class M001 extends AbstractKC85Module implements
						Z80InterruptSource,
						Z80PIOPortListener
{
  private Z80CPU  cpu;
  private Z80CTC  ctc;
  private Z80PIO  pio;
  private Plotter plotter;
  private boolean plotterPenState;
  private boolean plotterStepState;


  public M001( int slot, EmuThread emuThread, Properties props  )
  {
    super( slot );
    this.cpu = emuThread.getZ80CPU();
    this.ctc = new Z80CTC( "CTC (M001)" );
    this.ctc.setTimerConnection( 2, 3 );
    this.pio     = new Z80PIO( "PIO (M001)" );
    this.plotter = new Plotter(
			Plotter.XY_PAGE_WIDTH,
			Plotter.XY_PAGE_HEIGHT );
    this.plotter.applySettings( props );
    this.plotterPenState  = false;
    this.plotterStepState = false;
    this.pio.addPIOPortListener( this, Z80PIO.PortInfo.A );
    this.pio.addPIOPortListener( this, Z80PIO.PortInfo.B );
    this.cpu.addTStatesListener( this.ctc );
  }


	/* --- Z80InterruptSource --- */

  @Override
  public void appendInterruptStatusHTMLTo( StringBuilder buf )
  {
    buf.append( "<h2>CTC (E/A-Adressen&nbsp;00-03)</h2>\n" );
    this.ctc.appendInterruptStatusHTMLTo( buf );
    buf.append( "<br/><br/>\n"
		+ "<h2>PIO (E/A-Adressen&nbsp;04-07)</h2>\n" );
    this.pio.appendInterruptStatusHTMLTo( buf );
  }


  @Override
  public synchronized int interruptAccept()
  {
    int rv = 0;
    if( this.ctc.isInterruptRequested() ) {
      rv = this.ctc.interruptAccept();
    }
    else if( this.pio.isInterruptRequested() ) {
      rv = this.pio.interruptAccept();
    }
    return rv;
  }


  @Override
  public synchronized boolean interruptFinish( int addr )
  {
    boolean rv = this.ctc.interruptFinish( addr );
    if( !rv ) {
      rv = this.pio.interruptFinish( addr );
    }
    return rv;
  }


  @Override
  public boolean isInterruptAccepted()
  {
    return this.ctc.isInterruptAccepted() || this.pio.isInterruptAccepted();
  }


  @Override
  public boolean isInterruptRequested()
  {
    boolean rv = this.ctc.isInterruptRequested();
    if( !rv ) {
      rv = this.pio.isInterruptRequested();
    }
    return rv;
  }


	/* --- Z80PIOPortListener --- */

  @Override
  public void z80PIOPortStatusChanged(
				Z80PIO          pio,
				Z80PIO.PortInfo port,
				Z80PIO.Status   status )
  {
    if( (pio == this.pio)
	&& ((status == Z80PIO.Status.OUTPUT_AVAILABLE)
	    || (status == Z80PIO.Status.OUTPUT_CHANGED)) )
    {
      if( port == Z80PIO.PortInfo.A ) {
	int v = this.pio.fetchOutValuePortA( 0xFF );
	boolean pen  = ((v & 0x80) != 0);
	boolean step = ((v & 0x04) != 0);
	if( pen != this.plotterPenState ) {
	  this.plotterPenState = pen;
	  this.plotter.setPenState( pen );
	}
	if( step != this.plotterStepState ) {
	  this.plotterStepState = step;
	  if( step ) {
	    int d = ((v & 0x01) != 0 ? 1 : -1);
	    if( (v & 0x02) != 0 ) {
	      this.plotter.movePen( 0, d );
	    } else {
	      this.plotter.movePen( d, 0 );
	    }
	  }
	}
      } else if( port == Z80PIO.PortInfo.B ) {
	int v = this.pio.fetchOutValuePortB( 0xFF );
	boolean pen  = ((v & 0x80) != 0);
	boolean step = ((v & 0x01) != 0);
	if( pen != this.plotterPenState ) {
	  this.plotterPenState = pen;
	  this.plotter.setPenState( pen );
	}
	if( step != this.plotterStepState ) {
	  this.plotterStepState = step;
	  if( step ) {
	    int d = ((v & 0x04) != 0 ? 1 : -1);
	    if( (v & 0x02) != 0 ) {
	      this.plotter.movePen( 0, d );
	    } else {
	      this.plotter.movePen( d, 0 );
	    }
	  }
	}
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void applySettings( Properties props )
  {
    super.applySettings( props );
    this.plotter.applySettings( props );
  }


  @Override
  public void dispose()
  {
    this.cpu.removeTStatesListener( this.ctc );
    this.plotter.dispose();
  }


  @Override
  public String getModuleName()
  {
    return "M001";
  }


  @Override
  public Plotter getPlotter()
  {
    return this.plotter;
  }


  @Override
  public int getTypeByte()
  {
    return 0xEF;
  }


  @Override
  public int readIOByte( int port, int tStates )
  {
    int rv = -1;
    if( this.enabled ) {
      switch( port & 0xFF ) {
	case 0x00:
	case 0x01:
	case 0x02:
	case 0x03:
	  rv = this.ctc.read( port & 0x03, tStates );
	  break;

	case 0x04:
	  rv = this.pio.readDataA();
	  break;

	case 0x05:
	  rv = this.pio.readDataB();
	  break;

	case 0x06:		// PIO Contral A
	case 0x07:		// PIO Control B
	  rv = 0xFF;
	  break;
      }
    }
    return rv;
  }


  @Override
  public void reset( boolean powerOn )
  {
    this.ctc.reset( powerOn );
    this.pio.reset( powerOn );
    this.plotter.reset();
    this.pio.putInValuePortA( 0x00, 0x20 );	// Plotter an Port A Ready
    this.pio.putInValuePortB( 0x00, 0x20 );	// Plotter an Port B Ready
    this.plotterPenState  = false;
    this.plotterStepState = false;
  }


  @Override
  public boolean writeIOByte( int port, int value, int tStates )
  {
    boolean rv = false;
    if( this.enabled ) {
      switch( port & 0xFF ) {
	case 0x00:
	case 0x01:
	case 0x02:
	case 0x03:
	  this.ctc.write( port & 0x03, value, tStates );
	  rv = true;
	  break;

	case 0x04:
	  this.pio.writeDataA( value );
	  rv = true;
	  break;

	case 0x05:
	  this.pio.writeDataB( value );
	  rv = true;
	  break;

	case 0x06:
	  this.pio.writeControlA( value );
	  rv = true;
	  break;

	case 0x07:
	  this.pio.writeControlB( value );
	  rv = true;
	  break;
      }
    }
    return rv;
  }
}
