/*
 * (c) 2023-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Verwaltung der zuletzt verwendeten Dateien
 */

package jkcemu.file;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;


public class RecentFilesMngr implements ActionListener, MenuListener
{
  public static enum App {
			DISK_VIEWER,
			EMULATOR,
			HEX_EDITOR,
			IMAGE_VIEWER,
			TEXT_EDITOR };

  public interface Listener
  {
    public void recentFileActionPerformed( String fileName );
  };


  private static final int MAX_RECENT_FILES = 20;

  private Listener     listener;
  private File         listFile;
  private long         listFileMillis;
  private List<String> recentFileNames;
  private JMenu        menu;
  private JMenuItem[]  menuItems;
  private JMenuItem    menuClear;
  private JSeparator   menuSep;


  public static RecentFilesMngr getLazyInstance(
					RecentFilesMngr.Listener listener,
					App                      app )
  {
    File   configDir = Main.getConfigDir();
    String fileName  = getListFileName( app );
    return (configDir!= null) && (fileName != null) ?
	new RecentFilesMngr( listener, new File( configDir, fileName ) )
	: null;
  }


  public static String getListFileName( App app )
  {
    String fileName = null;
    switch( app ) {
      case DISK_VIEWER:
	fileName = "recent_dv.lst";
	break;
      case EMULATOR:
	fileName = "recent_emu.lst";
	break;
      case HEX_EDITOR:
	fileName = "recent_he.lst";
	break;
      case TEXT_EDITOR:
	fileName = "recent_te.lst";
	break;
    }
    return fileName;
  }


  public static boolean isRecentListFileName( String fileName )
  {
    boolean rv = false;
    for( App app : App.values() ) {
      String fName = getListFileName( app );
      if( fName != null ) {
	if( fName.equals( fileName ) ) {
	  rv = true;
	  break;
	}
      }
    }
    return rv;
  }


  public void setRecentFile( File file )
  {
    if( file != null ) {
      String path = file.getPath();
      if( path != null ) {
	if( !path.isEmpty() ) {
	  List<String> list = new ArrayList<>( MAX_RECENT_FILES );
	  list.add( path );

	  // Datei oeffnen, lesen und schreiben
	  RandomAccessFile raf = null;
	  try {
	    raf = new RandomAccessFile( this.listFile, "rw" );
	    readRecentFileNamesTo(
			list,
			new RAFInputStream( raf, true ),
			file );
	    raf.seek( 0 );
	    raf.setLength( 0 );
	    BufferedWriter writer = new BufferedWriter(
			new OutputStreamWriter(
				new RAFOutputStream( raf, true ),
				"UTF-8" ) );
	    for( String line : list ) {
	      writer.write( line );
	      writer.newLine();
	    }
	    writer.flush();
	  }
	  catch( IOException ex ) {}
	  finally {
	    EmuUtil.closeSilently( raf );
	  }

	  // Oberfleache aktualisieren
	  this.listFileMillis = -1L;	// Einlesen erzwingen
	  final JMenu menu = this.menu;
	  EventQueue.invokeLater(
			new Runnable()
			{
			  @Override
			  public void run()
			  {
			    menu.setVisible( true );
			  }
			} );
	}
      }
    }
  }


  public JMenu getMenu()
  {
    return this.menu;
  }


	/* --- ActionListener --- */

  @Override
  public void actionPerformed( ActionEvent e )
  {
    Object src = e.getSource();
    if( src == this.menuClear ) {
      if( this.menu.isVisible() ) {
	if( BaseDlg.showSuppressableYesNoDlg(
		this.menu,
		"M\u00F6chte Sie die Liste der zuletzt verwendeten"
			+ " Dateien l\u00F6schen?" ) )
	{
	  if( this.listFile.delete() ) {
	    this.menu.setVisible( false );
	    this.listFileMillis = -1L;
	  } else {
	    BaseDlg.showErrorDlg(
		this.menu,
		this.listFile.getPath() + ":\n"
			+ "Datei, die die Liste enth\u00E4lt,"
			+ " konnte nicht gel\u00F6scht werden.\n" );
	  }
	}
      }
    } else {
      for( int i = 0; i < this.menuItems.length; i++ ) {
	JMenuItem item = this.menuItems[ i ];
	if( (item == src) && (i < this.recentFileNames.size()) ) {
	  this.listener.recentFileActionPerformed(
				this.recentFileNames.get( i ) );
	  break;
	}
      }
    }
  }


	/* --- MenuListener --- */

  @Override
  public void menuCanceled( MenuEvent e )
  {
    // leer
  }


  @Override
  public void menuDeselected( MenuEvent e )
  {
    // leer
  }


  @Override
  public void menuSelected( MenuEvent e )
  {
    updRecentFileList();
  }


	/* --- private Methoden --- */

  private static void readRecentFileNamesTo(
					List<String> list,
					InputStream  in,
					File         fileToIgnore )
  {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader( new InputStreamReader( in, "UTF-8" ) );
      int n  = list.size();
      String line = reader.readLine();
      while( (line != null) && (n < MAX_RECENT_FILES) ) {
	line = line.trim();
	if( !line.isEmpty() ) {
	  boolean state = true;
	  if( fileToIgnore != null ) {
	    state = !fileToIgnore.equals( new File( line ) );
	  }
	  if( state ) {
	    list.add( line );
	    n++;
	  }
	}
	line = reader.readLine();
      }
    }
    catch( IOException ex ) {}
  }


  private void updRecentFileList()
  {
    long millis = this.listFile.lastModified();
    if( (millis < 0)
	|| (this.listFileMillis < 0)
	|| (this.listFileMillis < millis) )
    {
      this.listFileMillis = millis;
      this.recentFileNames.clear();
      InputStream in = null;
      try {
	in = new FileInputStream( this.listFile );
	readRecentFileNamesTo( this.recentFileNames, in, null );
      }
      catch( IOException ex ) {}
      finally {
	EmuUtil.closeSilently( in );
      }
      boolean hasItems   = false;
      boolean processing = true;
      int     n          = this.recentFileNames.size();
      for( int i = 0; i < this.menuItems.length; i++ ) {
	String fileName = null;
	String text     = null;
	if( processing && (i < n) ) {
	  fileName = this.recentFileNames.get( i );
	  int pos  = fileName.lastIndexOf( File.separatorChar );
	  if( pos >= 0 ) {
	    if( (pos + 1) < fileName.length() ) {
	      text = fileName.substring( pos + 1 );
	    }
	  } else {
	    text = fileName;
	  }
	  if( text != null ) {
	    if( text.isEmpty() ) {
	      text       = null;
	      processing = false;
	    }
	  }
	}
	if( processing && (fileName != null) && (text != null) ) {
	  this.menuItems[ i ].setText( text );
	  this.menuItems[ i ].setToolTipText( fileName );
	  this.menuItems[ i ].setVisible( true );
	  hasItems = true;
	} else {
	  this.menuItems[ i ].setText( "" );
	  this.menuItems[ i ].setToolTipText( null );
	  this.menuItems[ i ].setVisible( false );
	}
	this.menuSep.setVisible( hasItems );
	this.menuClear.setVisible( hasItems );
	this.menu.setVisible( hasItems );
      }
    }
  }


	/* --- Konstruktor --- */

  private RecentFilesMngr( Listener listener, File listFile )
  {
    this.listener        = listener;
    this.listFile        = listFile;
    this.listFileMillis  = -1L;
    this.recentFileNames = new ArrayList<>( MAX_RECENT_FILES );
    this.menu            = GUIFactory.createMenu( "Zuletzt verwendet" );
    this.menuItems = new JMenuItem[ MAX_RECENT_FILES ];
    for( int i = 0; i < this.menuItems.length; i++ ) {
      JMenuItem item = GUIFactory.createMenuItem( "" );
      item.addActionListener( this );
      this.menuItems[ i ] = item;
      this.menu.add( item );
    }
    this.menuSep = GUIFactory.createSeparator();
    this.menu.add( this.menuSep );
    this.menuClear = GUIFactory.createMenuItem( "Liste l\u00F6schen" );
    this.menuClear.addActionListener( this );
    this.menu.add( this.menuClear );
    updRecentFileList();
    this.menu.addMenuListener( this );
  }
}
