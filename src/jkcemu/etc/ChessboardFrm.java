/*
 * (c) 2009-2023 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Anzeige eines Schachbretts
 */

package jkcemu.etc;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.KeyEvent;
import java.util.EventObject;
import java.util.Properties;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import jkcemu.Main;
import jkcemu.base.BaseFrm;
import jkcemu.base.EmuSys;
import jkcemu.base.EmuUtil;
import jkcemu.base.EmuThread;
import jkcemu.base.GUIFactory;
import jkcemu.base.HelpFrm;
import jkcemu.base.ScreenFrm;
import jkcemu.image.ImageUtil;
import jkcemu.lang.LangUtil;


public class ChessboardFrm extends BaseFrm
{
  private static final String HELP_PAGE = "/help/chessboard.htm";

  private static ChessboardFrm instance = null;

  private JMenuItem     mnuClose;
  private JMenuItem     mnuCopy;
  private JMenuItem     mnuSwap;
  private JMenuItem     mnuHelpContent;
  private ChessboardFld chessboardFld;


  public static void checkAutoOpen(
				ScreenFrm  screenFrm,
				Properties props )
  {
    if( props != null ) {
      if( EmuUtil.getBooleanProperty(
			props,
			getSettingsPrefixInternal() + PROP_WINDOW_VISIBLE,
			false ) )
      {
	EmuThread emuThread = screenFrm.getEmuThread();
	if( emuThread != null ) {
	  if( emuThread.getEmuSys().supportsChessboard() ) {
	    open( emuThread );
	  }
	}
      }
    }
  }


  public static void close()
  {
    if( instance != null )
      instance.doClose();
  }


  public static void open( EmuThread emuThread )
  {
    if( instance == null ) {
      instance = new ChessboardFrm( emuThread );
    }
    EmuUtil.showFrame( instance );
  }


  public static void repaintChessboard()
  {
    if( instance != null )
      instance.chessboardFld.repaint();
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = false;
    if( e != null ) {
      Object src = e.getSource();
      if( src == this.mnuClose ) {
	rv = true;
	doClose();
      }
      else if( src == this.mnuCopy ) {
	rv = true;
	ImageUtil.copyToClipboard(
			this,
			this.chessboardFld.createImage() );
      }
      else if( src == this.mnuSwap ) {
	rv = true;
	this.chessboardFld.swap();
      }
      else if( src == this.mnuHelpContent ) {
	rv = true;
	HelpFrm.openPage( HELP_PAGE );
      }
    }
    return rv;
  }


  @Override
  public String getSettingsPrefix()
  {
    return getSettingsPrefixInternal();
  }


  @Override
  public void resetFired( EmuSys newEmuSys, Properties newProps )
  {
    if( newEmuSys != null ) {
      if( !newEmuSys.supportsChessboard() ) {
	doClose();
      }
    }
  }


	/* --- private Methoden --- */

  private static String getSettingsPrefixInternal()
  {
    return ChessboardFrm.class.getName() + ".";
  }


	/* --- Konstruktor --- */

  private ChessboardFrm( EmuThread emuThread )
  {
    setTitle( LangUtil.getText( "etc.title.jkcemu_chessboard" ) );


    // Menu Datei
    JMenu mnuFile = createMenuFile();

    this.mnuClose = createMenuItemClose();
    mnuFile.add( this.mnuClose );


    // Menu Bearbeiten
    JMenu mnuEdit = createMenuEdit();

    this.mnuCopy = createMenuItem(
		LangUtil.getText( "etc.action.copy_chessboard" ) );
    mnuEdit.add( this.mnuCopy );


    // Menu Ansicht
    JMenu mnuView = GUIFactory.createMenu(
		LangUtil.getText( "common.menu.view" ) );
    mnuView.setMnemonic(
		LangUtil.mnemonic( "common.menu.view", KeyEvent.VK_A ) );

    this.mnuSwap = createMenuItem(
		LangUtil.getText( "etc.action.switch_side" ) );
    mnuView.add( this.mnuSwap );


    // Menu Hilfe
    JMenu mnuHelp = createMenuHelp();

    this.mnuHelpContent = createMenuItem(
		LangUtil.getText( "etc.action.help_chessboard" ) );
    mnuHelp.add( this.mnuHelpContent );


    // Menu zusammenbauen
    setJMenuBar( GUIFactory.createMenuBar(
					mnuFile,
					mnuEdit,
					mnuView,
					mnuHelp ) );


    // Fensterinhalt
    setLayout( new BorderLayout() );

    this.chessboardFld = new ChessboardFld( emuThread );
    add( this.chessboardFld, BorderLayout.CENTER );


    // Fenstergroesse
    pack();
    setResizable( false );
    if( !applySettings( Main.getProperties() ) ) {
      setLocationByPlatform( true );
    }
  }
}
