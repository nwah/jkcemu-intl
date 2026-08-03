/*
 * (c) 2008-2026 Jens Mueller
 *
 * Z80-Emulator
 *
 * Emulation der Z80 CTC
 */

package z80emu;

import java.util.ArrayList;
import java.util.Collection;


public class Z80CTC implements Z80InterruptSource, Z80TStatesListener
{
  private String                     title;
  private Collection<Z80CTCListener> listeners;
  private int                        interruptVector;
  private int                        tStatesToIgnore;
  private Timer[]                    timer;


  public Z80CTC( String title )
  {
    this.title           = title;
    this.listeners       = null;
    this.interruptVector = 0;
    this.timer           = new Timer[ 4 ];
    for( int i = 0; i < this.timer.length; i++ ) {
      this.timer[ i ] = new Timer( i );
    }
  }


  public synchronized void addCTCListener( Z80CTCListener listener )
  {
    if( this.listeners == null ) {
      this.listeners = new ArrayList<>();
    }
    this.listeners.add( listener );
  }


  public synchronized void removeCTCListener( Z80CTCListener listener )
  {
    if( this.listeners != null )
      this.listeners.remove( listener );
  }


  public synchronized int externalUpdate( int timerNum, boolean state )
  {
    return (timerNum >= 0) && (timerNum < this.timer.length) ?
			this.timer[ timerNum ].externalUpdate( state ) : 0;
  }


  public synchronized int externalUpdate( int timerNum, int pulses )
  {
    return (timerNum >= 0) && (timerNum < this.timer.length) ?
			this.timer[ timerNum ].externalUpdate( pulses ) : 0;
  }


  public synchronized int read( int timerNum, int tStates )
  {
    int rv = 0xFF;

    /*
     * zuerst Taktzyklen des IO-Befehls verarbeiten,
     *
     * Da der von der CTC gelesene Wert an die CPU uebertragen
     * und dann dem Prozessorregister zugewiesen werden muss,
     * werden die letzten beiden Taktzyklen erst nach Ausfuehrung
     * verarbeitet.
     */
    int remainTStates = 0;
    int preTStates    = tStates - 2;
    if( preTStates > 0 ) {
      processTStates( preTStates );
      this.tStatesToIgnore += preTStates;
      remainTStates = tStates - preTStates;
    } else {
      remainTStates = tStates;
    }

    // Wert von CTC lesen
    if( (timerNum >= 0) && (timerNum < this.timer.length) ) {
      rv = this.timer[ timerNum ].read();
    }

    // restliche Taktzyklen des IO-Befehls verarbeiten
    if( remainTStates > 0 ) {
      processTStates( remainTStates );
      this.tStatesToIgnore += remainTStates;
    }

    return rv;
  }


  public synchronized void reset( boolean powerOn )
  {
    if( powerOn ) {
      this.interruptVector = 0;
    }
    for( int i = 0; i < this.timer.length; i++ ) {
      this.timer[ i ].reset();
    }
    this.tStatesToIgnore = 0;
  }


  /*
   * Diese Methode stellt eine Verbindung
   * vom Ausgang fromTimerNum zum Eingang toTimerNum her.
   */
  public void setTimerConnection( int fromTimerNum, int toTimerNum )
  {
    if( (fromTimerNum >= 0) && (fromTimerNum < this.timer.length) ) {
      this.timer[ fromTimerNum ].toTimer    = this.timer[ toTimerNum ];
      this.timer[ toTimerNum ].fromTimerNum = fromTimerNum;
    }
  }


  public synchronized void write( int timerNum, int value, int tStates )
  {
    /*
     * zuerst Taktzyklen des IO-Befehls verarbeiten,
     * ausser dem letzten Taktzyklus
     */
    boolean remainTState = false;
    if( tStates > 0 ) {
      remainTState = true;
      --tStates;
      if( tStates > 0 ) {
	processTStates( tStates );
	this.tStatesToIgnore += tStates;
      }
    }

    // Befehl ausfuehren
    boolean done = false;
    if( (timerNum >= 0) && (timerNum < this.timer.length) ) {
      if( this.timer[ timerNum ].expectsCounterInit() ) {
	this.timer[ timerNum ].writeCounterInit( value );
	done = true;
      }
    }
    if( !done ) {
      if( (value & 0x01) == 0 ) {
	this.interruptVector = value & 0xF8;
      } else {
	if( (timerNum >= 0) && (timerNum < this.timer.length) ) {
	  this.timer[ timerNum ].writeControl( value );
	}
      }
    }

    // verbleibenden Taktzyklus des IO-Befehls verarbeiten
    if( remainTState ) {
      processTStates( 1 );
      this.tStatesToIgnore++;
    }
  }


	/* --- Z80InterruptSource --- */

  @Override
  public void appendInterruptStatusHTMLTo( StringBuilder buf )
  {
    buf.append( "<table border=\"1\">\n"
	+ "<tr><th></th><th>Kanal&nbsp;0</th><th>Kanal&nbsp;1</th>"
	+ "<th>Kanal&nbsp;2</th><th>Kanal&nbsp;3</th></tr>\n"
	+ "<tr><td>Status:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      if( this.timer[ i ].running ) {
	buf.append( "l&auml;uft" );
      } else if( this.timer[ i ].waitForTrigger ) {
	buf.append( "wartet auf Trigger-Impuls" );
      } else {
	buf.append( "angehalten" );
      }
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Vorteiler:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      if( this.timer[ i ].extMode ) {
	buf.append( "nicht aktiv" );
      } else {
	buf.append( this.timer[ i ].preCounter );
	if( this.timer[ i ].pre256 ) {
	  buf.append( "/256" );
	} else {
	  buf.append( "/16" );
	}
      }
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Zeitkonstante:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      Integer counterInit = this.timer[ i ].counterInit;
      if( counterInit != null ) {
	buf.append( counterInit.toString() );
      } else {
	buf.append( "nicht gesetzt" );
      }
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Z&auml;hlerwert:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      buf.append( this.timer[ i ].counter & 0xFF );
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Takt:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      if( this.timer[ i ].extMode ) {
	if( this.timer[ i ].fromTimerNum >= 0 ) {
	  buf.append( "Kanal " );
	  buf.append( this.timer[ i ].fromTimerNum );
	} else {
	  buf.append( "extern" );
	}
      } else {
	buf.append( "Systemtakt" );
      }
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Interrupt-Status:</td>" );
    for( int i = 0; i < this.timer.length; i++ ) {
      buf.append( "<td>" );
      if( this.timer[ i ].interruptAccepted ) {
	buf.append( "angenommen (wird gerade bedient)" );
      } else if( this.timer[ i ].interruptRequested ) {
	buf.append( "angemeldet" );
      } else if( this.timer[ i ].interruptEnabled ) {
	buf.append( "freigegeben" );
      } else {
	buf.append( "gesperrt" );
      }
      buf.append( "</td>" );
    }
    buf.append( "</tr>\n"
	+ "<tr><td>Interrupt-Vektor:</td><td colspan=\"4\">" );
    buf.append( String.format( "%02Xh", this.interruptVector ) );
    buf.append( "</td></tr>\n"
	+ "</table>\n" );
  }


  @Override
  public synchronized int interruptAccept()
  {
    int rv = 0;
    for( int i = 0; i < this.timer.length; i++ ) {
      if( !this.timer[ i ].interruptAccepted
	  && this.timer[ i ].interruptRequested )
      {
	this.timer[ i ].interruptAccepted  = true;
	this.timer[ i ].interruptRequested = false;
	rv = this.interruptVector + (i * 2);
	break;
      }
    }
    return rv;
  }


  @Override
  public synchronized boolean interruptFinish( int addr )
  {
    boolean rv = false;
    for( int i = 0; i < this.timer.length; i++ ) {
      if( this.timer[ i ].interruptAccepted ) {
	this.timer[ i ].interruptAccepted = false;
	rv = true;
	break;
      }
    }
    return rv;
  }


  @Override
  public boolean isInterruptAccepted()
  {
    boolean rv = false;
    for( Timer t : this.timer ) {
      rv |= t.interruptAccepted;
    }
    return rv;
  }


  @Override
  public boolean isInterruptRequested()
  {
    boolean rv = false;
    for( int i = 0; i < this.timer.length; i++ ) {
      if( this.timer[ i ].interruptAccepted ) {
	break;
      }
      if( this.timer[ i ].interruptEnabled
		&& this.timer[ i ].interruptRequested )
      {
	rv = true;
	break;
      }
    }
    return rv;
  }


	/* --- Z80TStatesListener --- */

  @Override
  public synchronized void z80TStatesProcessed( Z80CPU cpu, int tStates )
  {
    if( tStates < this.tStatesToIgnore ) {
      this.tStatesToIgnore -= tStates;
    } else {
      tStates -= this.tStatesToIgnore;
      this.tStatesToIgnore = 0;
      if( tStates > 0 ) {
	processTStates( tStates );
      }
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public String toString()
  {
    return this.title != null ? this.title : "CTC";
  }


	/* --- private Klasse --- */

  private class Timer
  {
    private int              timerNum;
    private int              fromTimerNum;
    private Timer            toTimer;
    private volatile Integer counterInit;
    private volatile int     counter;
    private volatile int     preCounter;
    private boolean          pre256;
    private boolean          extMode;
    private boolean          slope;
    private boolean          waitForTrigger;
    private boolean          interruptEnabled;
    private boolean          interruptAccepted;
    private boolean          interruptRequested;
    private boolean          nextIsCounterInit;
    private boolean          running;
    private Boolean          lastInSlope;


    private Timer( int timerNum )
    {
      this.timerNum     = timerNum;
      this.fromTimerNum = -1;
      this.toTimer      = null;
      reset();
    }


    private boolean expectsCounterInit()
    {
      return this.nextIsCounterInit;
    }


    private int externalUpdate( boolean slope )
    {
      int rv = 0;
      if( slope == this.slope ) {
        Boolean lastInSlope = this.lastInSlope;
        if( lastInSlope != null ) {
	  if( slope != lastInSlope.booleanValue() ) {
	    rv = externalUpdateIntern( 1 );
	  }
	}
      }
      this.lastInSlope = slope ? Boolean.TRUE : Boolean.FALSE;
      return rv;
    }


    private int externalUpdate( int pulses )
    {
      this.lastInSlope = null;
      return externalUpdateIntern( pulses );
    }


    private int externalUpdateIntern( int pulses )
    {
      int rv = 0;
      if( (pulses > 0) && (this.counterInit != null) ) {
	if( this.extMode ) {
	  rv = updCounter( pulses );
	} else {
	  if( this.waitForTrigger ) {
	    if( start() ) {
	      this.waitForTrigger = false;
	    }
	  }
	}
      }
      return rv;
    }


    private void initCounter()
    {
      Integer counterInit = this.counterInit;
      if( counterInit != null ) {
	if( counterInit.intValue() > 0 ) {
	  this.counter = counterInit.intValue();
	} else {
	  this.counter = 0x100;
	}
      }
    }


    private void processTStates( int pulses )
    {
      // Ein neuer Zaehlerwert wird erst nach einem Taktzyklus uebernommen.
      if( (pulses > 0) && (this.counterInit != null) ) {
	if( !this.extMode ) {
	  updCounter( updPreCounter( 1 ) );
	}
	--pulses;
	if( this.extMode || !this.waitForTrigger ) {
	  start();
	}
      }
      if( !this.extMode && (pulses > 0) ) {
	updCounter( updPreCounter( pulses ) );
      }
    }


    private int read()
    {
      return this.counter;
    }


    private void reset()
    {
      this.counterInit        = null;
      this.counter            = 0;
      this.preCounter         = 0;
      this.pre256             = false;
      this.extMode            = false;
      this.slope              = false;
      this.waitForTrigger     = false;
      this.interruptEnabled   = false;
      this.interruptAccepted  = false;
      this.interruptRequested = false;
      this.nextIsCounterInit  = false;
      this.running            = false;
      this.lastInSlope        = null;
    }


    private boolean start()
    {
      boolean rv          = false;
      Integer counterInit = this.counterInit;
      if( (counterInit != null) && !this.running ) {
	this.running = true;
	rv           = true;
      }
      return rv;
    }


    private void writeControl( int value )
    {
      this.interruptAccepted  = false;
      this.interruptRequested = false;
      this.interruptEnabled   = ((value & 0x80) != 0);
      this.extMode            = ((value & 0x40) != 0);
      this.pre256             = ((value & 0x20) != 0);
      this.slope              = ((value & 0x10) != 0);
      this.waitForTrigger     = ((value & 0x08) != 0);
      this.nextIsCounterInit  = ((value & 0x04) != 0);
      if( (value & 0x02) != 0 ) {
	Integer counterInit = this.counterInit;
	if( counterInit != null ) {
	  this.counter     = counterInit.intValue();
	  this.counterInit = null;
	  this.preCounter  = 0;
	}
	this.running = false;
      }
      this.lastInSlope = null;
    }


    private void writeCounterInit( int value )
    {
      this.nextIsCounterInit = false;
      this.counterInit       = value & 0xFF;
      if( this.extMode || !this.running || this.waitForTrigger ) {
	initCounter();
      }
    }


    private int updCounter( int pulses )
    {
      int rv = 0;
      Integer counterInit = this.counterInit;
      if( counterInit != null ) {
	while( this.running && (pulses > 0) ) {
	  if( pulses < this.counter ) {
	    this.counter -= pulses;
	    pulses = 0;
	  } else {
	    pulses -= this.counter;
	    initCounter();
	    rv++;
	    if( this.interruptEnabled ) {
	      this.interruptRequested = true;
	    }
	    if( this.toTimer != null ) {
	      this.toTimer.externalUpdate( 1 );
	    }
	    informListeners( this.timerNum );
	  }
	}
      }
      return rv;
    }


    private int updPreCounter( int pulses )
    {
      int rv = 0;
      while( this.running && (pulses > 0) ) {
	if( this.preCounter == 0 ) {
	  this.preCounter = this.pre256 ? 256 : 16;
	}
	if( pulses < this.preCounter ) {
	  this.preCounter -= pulses;
	  pulses = 0;
	} else {
	  pulses -= this.preCounter;
	  this.preCounter = 0;
	  rv++;
	}
      }
      return rv;
    }
  }


	/* --- private Methoden --- */

  private void informListeners( int timerNum )
  {
    Collection<Z80CTCListener> listeners = this.listeners;
    if( listeners != null ) {
      for( Z80CTCListener listener : listeners ) {
	listener.z80CTCUpdate( this, timerNum );
      }
    }
  }


  private void processTStates( int tStates )
  {
    for( int i = 0; i < this.timer.length; i++ ) {
      this.timer[ i ].processTStates( tStates );
    }
  }
}
