/*
 * (c) 2019-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Desktop-Integration fuer Java 9 und hoeher
 */

package jkcemu.base.jversion;

import java.awt.Desktop;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.desktop.ScreenSleepEvent;
import java.awt.desktop.ScreenSleepListener;
import java.awt.desktop.SystemSleepEvent;
import java.awt.desktop.SystemSleepListener;
import java.awt.desktop.UserSessionEvent;
import java.awt.desktop.UserSessionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import jkcemu.Main;
import jkcemu.base.AboutDlg;
import jkcemu.base.BaseFrm;
import jkcemu.base.DesktopHelper;
import jkcemu.base.GUIFactory;
import jkcemu.base.ScreenFrm;
import jkcemu.lang.LangUtil;


public class DesktopHelper_9
			extends DesktopHelper
			implements
				ScreenSleepListener,
				SystemSleepListener,
				UserSessionListener
{
  private ScreenFrm           screenFrm;
  private Taskbar             taskbar;
  private Map<Window,Integer> win2Progress;
  private boolean             winProgressEnabled;


  public DesktopHelper_9( final BaseFrm topFrm )
  {
    this.screenFrm          = null;
    this.taskbar            = null;
    this.win2Progress       = null;
    this.winProgressEnabled = false;
    if( topFrm instanceof ScreenFrm ) {
      this.screenFrm = (ScreenFrm) topFrm;
    }
    if( this.desktop != null ) {

      // Standard.Menu
      if( this.desktop.isSupported( Desktop.Action.APP_MENU_BAR ) ) {
	try {
	  JMenuItem mnuQuit = GUIFactory.createMenuItem(
			LangUtil.getText( "base.action.quit" ) );
	  mnuQuit.addActionListener( e->topFrm.doClose() );

	  JMenu mnuApp = GUIFactory.createMenu( Main.getAppName() );
	  mnuApp.add( mnuQuit );

	  this.desktop.setDefaultMenuBar(
			GUIFactory.createMenuBar( mnuApp ) );
	}
	catch( UnsupportedOperationException ex ) {}
      }

      // About-Handler
      if( this.desktop.isSupported( Desktop.Action.APP_ABOUT ) ) {
	try {
	  this.desktop.setAboutHandler( e->AboutDlg.fireOpen( topFrm ) );
	}
	catch( UnsupportedOperationException ex ) {}
      }

      // Quit-Handler
      if( this.desktop.isSupported( Desktop.Action.APP_QUIT_HANDLER ) ) {
	try {
	  this.desktop.setQuitHandler(
			(e,r)->{
				if( topFrm.doClose() ) {
				  r.performQuit();
				} else {
				  r.cancelQuit();
				}
			} );
	}
	catch( UnsupportedOperationException ex ) {}
      }

      // Listener zum Zuruecksetzen der Taktfrequenzberechnung
      if( this.screenFrm != null ) {
	this.desktop.addAppEventListener( this );
      }
    }

    // Taskbar
    if( Taskbar.isTaskbarSupported() ) {
      try {
	this.taskbar = Taskbar.getTaskbar();

	// Icon
	if( this.taskbar.isSupported( Taskbar.Feature.ICON_IMAGE ) ) {
	  java.util.List<Image> iconImages = Main.getIconImages( topFrm );
	  if( iconImages != null ) {
	    int n = iconImages.size();
	    if( n > 0 ) {
	      this.taskbar.setIconImage( iconImages.get( n - 1 ) );
	    }
	  }
	}

	// Fortschrittanzeige
	if( this.taskbar.isSupported( Taskbar.Feature.PROGRESS_STATE_WINDOW )
	    && this.taskbar.isSupported(
				Taskbar.Feature.PROGRESS_VALUE_WINDOW ) )
	{
	  this.winProgressEnabled = true;
	} else if( this.taskbar.isSupported(
				Taskbar.Feature.PROGRESS_VALUE ) )
	{
	  this.win2Progress = new HashMap<>();
	}
      }
      catch( UnsupportedOperationException ex ) {}
    }
  }


	/* --- ScreenSleepListener --- */

  @Override
  public void screenAboutToSleep( ScreenSleepEvent e )
  {
    // leer
  }

  @Override
  public void screenAwoke( ScreenSleepEvent e )
  {
    resetCPUSpeed();
  }


	/* --- SystemSleepListener --- */

  @Override
  public void systemAboutToSleep( SystemSleepEvent e )
  {
    // leer
  }

  @Override
  public void systemAwoke( SystemSleepEvent e )
  {
    resetCPUSpeed();
  }


	/* --- UserSessionListener --- */

  @Override
  public void userSessionActivated( UserSessionEvent e )
  {
    resetCPUSpeed();
  }

  @Override
  public void userSessionDeactivated( UserSessionEvent e )
  {
    // leer
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean isMoveToTrashSupportedInternal()
  {
    return this.desktop != null ?
		this.desktop.isSupported( Desktop.Action.MOVE_TO_TRASH )
		: false;
  }


  @Override
  protected void moveToTrashInternal( File file ) throws IOException
  {
    if( this.desktop == null ) {
      throwMoveToTrashNotSupported();
    }
    if( !this.desktop.moveToTrash( file ) ) {
      throw new IOException( file.getPath()
		+  ":\nKonnte nicht in den Papierkorb geworfen werden" );
    }
  }


  /*
   * Fortschrittanzeige:
   *   Beim Wert 1.0 = 100% wird die Fortschrittanzeige deaktiviert,
   *   da es wahrscheinlich keinen weiteren Aufruf der Methode
   *   fuer dieses Fenster mehr gibt.
   */
  @Override
  protected void setProgressValueInternal( Window window, float value )
  {
    int intValue = Math.round( value * 100F );
    if( this.winProgressEnabled ) {
      try {
	Taskbar.State state = Taskbar.State.OFF;
	if( (intValue >= 0) && (intValue < 100) ) {
	  state = Taskbar.State.NORMAL;
	}
	this.taskbar.setWindowProgressState( window, state );
	if( state == Taskbar.State.NORMAL ) {
	  this.taskbar.setWindowProgressValue( window, intValue );
	}
      }
      catch( UnsupportedOperationException ex ) {
	this.winProgressEnabled = false;
      }
    } else if( this.win2Progress != null ) {
      try {
	if( (intValue > 0) && (intValue < 100) ) {
	  this.win2Progress.remove( window );
	} else {
	  this.win2Progress.put( window, intValue );
	}
	int totalCount = 0;
	int totalValue = 0;
	for( Integer v : this.win2Progress.values() ) {
	  if( v != null ) {
	    totalValue += v.intValue();
	    totalCount++;
	  }
	}
	if( totalCount > 1 ) {
	  totalValue /= totalCount;
	}
	this.taskbar.setProgressValue( totalValue );
      }
      catch( UnsupportedOperationException ex ) {
	this.win2Progress = null;
      }
    }
  }


	/* --- private Methoden --- */

  private void resetCPUSpeed()
  {
    if( this.screenFrm != null )
      this.screenFrm.getEmuThread().getZ80CPU().resetSpeed();
  }
}
