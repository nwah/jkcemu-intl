/*
 * (c) 2023-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Abstraktes Fenster fuer Aktionen,
 * die in einem separaten Thread laufen
 */

package jkcemu.base;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EventObject;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Checksum;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.Document;
import jkcemu.Main;
import jkcemu.base.PopupMenuOwner;
import jkcemu.file.FileProgressInputStream;
import jkcemu.file.FileUtil;
import jkcemu.lang.LangUtil;
import jkcemu.text.LogTextActionMngr;


public abstract class AbstractThreadFrm
				extends BaseFrm
				implements PopupMenuOwner, Runnable
{
  protected boolean cancelled;
  protected int     errorCount;

  private boolean           withIconAnimation;
  private boolean           autoClose;
  private volatile boolean  finished;
  private boolean           notified;
  private Set<Path>         renamedPaths;
  private Thread            thread;
  private LogTextActionMngr actionMngr;
  private JTextArea         fldLog;
  private JProgressBar      progressBar;
  private JButton           btnClose;


  protected AbstractThreadFrm(
			String  threadName,
			String  msg,
			boolean withLog,
			boolean withProgressBar,
			boolean withIconAnimation )
  {
    this.withIconAnimation = withIconAnimation;
    this.autoClose         = true;
    this.cancelled         = false;
    this.finished          = false;
    this.notified          = false;
    this.errorCount        = 0;
    this.renamedPaths      = new TreeSet<>();
    this.thread            = new Thread(
					Main.getThreadGroup(),
					this,
					threadName );


    // Fensterinhalt
    setLayout( new GridBagLayout() );
    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    if( msg != null ) {
      add( new JLabel( msg ), gbc );
      gbc.gridy++;
    }

    gbc.anchor        = GridBagConstraints.CENTER;
    gbc.insets.bottom = 5;
    if( withLog ) {
      this.fldLog = GUIFactory.createTextArea( 10, 32 );
      this.fldLog.setEditable( false );
      gbc.fill    = GridBagConstraints.BOTH;
      gbc.weightx = 1.0;
      gbc.weighty = 1.0;
      add( GUIFactory.createScrollPane( this.fldLog ), gbc );
      gbc.gridy++;
    } else {
      this.fldLog = null;
    }

    if( withProgressBar ) {
      this.progressBar = GUIFactory.createProgressBar(
					JProgressBar.HORIZONTAL );
      this.progressBar.setBorderPainted( true );
      this.progressBar.setStringPainted( false );
      gbc.fill    = GridBagConstraints.HORIZONTAL;
      gbc.weighty = 0.0;
      add( this.progressBar, gbc );
      gbc.gridy++;
    } else {
      this.progressBar = null;
    }

    this.btnClose = GUIFactory.createButtonCancel();
    gbc.fill      = GridBagConstraints.NONE;
    gbc.weightx   = 0.0;
    gbc.weighty   = 0.0;
    add( this.btnClose, gbc );


    // Fenstergroesse und -position
    pack();
    setLocationByPlatform( true );
    if( this.fldLog != null ) {
      this.fldLog.setColumns( 0 );
      this.fldLog.setRows( 0 );

      // Aktionen im Popup-Menu
      this.actionMngr = new LogTextActionMngr( this.fldLog, true );
    } else {
      this.actionMngr = null;
    }


    // Starten des Threads veranlassen
    final Thread thread = this.thread;
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    thread.start();
		  }
		} );
  }


  protected void appendErrorToLog( Object errObj )
  {
    StringBuilder buf = new StringBuilder( 128 );
    buf.append( "  Fehler" );
    if( errObj != null ) {
      String errMsg = null;
      if( errObj instanceof Exception ) {
	errMsg = ((Exception) errObj).getMessage();
      } else {
	errMsg = errObj.toString();
      }
      if( errMsg != null ) {
	if( !errMsg.isEmpty() ) {
	  buf.append( ": " );
	  buf.append( errMsg );
	}
      }
    }
    buf.append( '\n' );
    appendToLog( buf.toString() );
    this.autoClose = false;
  }


  protected void appendIgnoredToLog()
  {
    appendToLog( LangUtil.getText( "base.msg.ignored" ) );
    this.autoClose = false;
  }


  protected void appendToLog( final String msg )
  {
    if( (this.fldLog != null) && !this.cancelled ) {
      final JTextArea fld = this.fldLog;
      EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    fld.append( msg );
		  }
		} );
		    
    }
  }


  protected void disableAutoClose()
  {
    this.autoClose = false;
  }


  protected abstract void doProgress();


  protected void incErrorCount()
  {
    this.errorCount++;
  }


  /*
   * Die Methode oeffnet eine Datei zum Lesen.
   * Optional kann ein Checksum-Objekt uebergeben werden.
   * In dem Fall wird die Datei erst einmal komplett gelesen
   * und an dem uebergebenen Object die Pruefsumme berechnet.
   * Anschliessend kehrt die Methode mit einem geoeffneten
   * und am Anfang der Datei stehenden InputStream-Objekt zurueck.
   * Der Fortschrittsbalken steht dann schon bei 50%.
   */
  protected FileProgressInputStream openInputFile(
					File     file,
					Checksum cks ) throws IOException
  {
    return new FileProgressInputStream(
				file,
				this.progressBar,
				this.withIconAnimation ? this : null,
				cks );
  }


  /*
   * Die Methode erzeugt ein Dateiobjekt.
   * Dabei werden ungueltige Zeichen in Unterstriche gewandelt und
   * innerhalb des Vorgangs die Eindeutigkeit des Namens sichergestellt.
   */
  public File prepareUniqueOutFile( File outDir, String orgFileName )
  {
    AtomicBoolean renamed = new AtomicBoolean( false );
    File          file    = FileUtil.prepareOutFile(
					outDir,
					orgFileName,
					renamed );

    /*
     * Durch das Umbenennen koennte der Dateiname innerhalb
     * des Vorgangs doppelt vorkommen.
     * Aus diesem Grund wird in diesem Fall,
     * und nur in solchen Faellen (um z.B. das bewusste uebereinander
     * Entpacken von Archiven zu ermoeglichen)
     * die Eindeutigkeit des Namens sichergestellt.
     */
    String usedName = file.getName();
    if( usedName == null ) {
      usedName = "";
    }
    if( file.exists() && (renamed.get() || equalsToRenamedFile( file )) ) {
      String baseName  = usedName;
      String extension = "";
      int idx = usedName.lastIndexOf( '.' );
      if( idx >= 0 ) {
	baseName  = usedName.substring( 0, idx );
	extension = usedName.substring( idx );
      }
      File tmpFile = file;
      int  counter = 1;
      do {
	usedName = String.format(
				"%s_(%d)%s",
				baseName,
				counter++,
				extension );
	tmpFile = new File( outDir, usedName );
      } while( tmpFile.exists() );
      file    = tmpFile;
      renamed.set( true );
    }
    if( renamed.get() ) {
      StringBuilder buf = new StringBuilder( 256 );
      int           len = orgFileName.length();
      buf.append( '\'' );
      for( int i = 0; i < len; i++ ) {
	char ch = orgFileName.charAt( i );
	if( (ch < '\u0020')
	    || (ch == '\\') || (ch == '\'')
	    || ((ch > '\u007E') && (ch < '\u00A0'))
	    || (ch > '\u00FF') )
	{
	  buf.append( String.format( "\\u%04X", (int) ch ) );
	} else {
	  buf.append( ch );
	}
      }
      buf.append( "\' -> \'" );
      buf.append( usedName );
      buf.append( "\'\n" );
      appendToLog( buf.toString() );
      try {
	this.renamedPaths.add( file.toPath().normalize().toAbsolutePath() );
      }
      catch( InvalidPathException ex ) {}
      this.autoClose = false;
    }
    return file;
  }


	/* --- PopupMenuOwner --- */

  @Override
  public JPopupMenu getPopupMenu()
  {
    return this.actionMngr.getPopupMenu();
  }


	/* --- Runnable --- */

  @Override
  public void run()
  {
    doProgress();
    this.finished = true;
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    progressFinished();
		  }
		} );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public void addNotify()
  {
    super.addNotify();
    if( !this.notified ) {
      this.notified = true;
      if( this.fldLog != null ) {
	this.fldLog.addMouseListener( this );
      }
      this.btnClose.addActionListener( this );
    }
  }


  @Override
  public boolean doAction( EventObject e )
  {
    boolean matched = false;
    if( e.getSource() == this.btnClose ) {
      matched        = true;
      this.cancelled = true;
      if( !this.thread.isAlive() ) {
	doClose();
      }
    }
    return matched;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = false;
    if( !this.cancelled && !this.finished ) {
      if( BaseDlg.showYesNoDlg(
		this,
		LangUtil.getText( "base.msg.want_cancel_running" ) ) )
      {
	this.cancelled = true;
	Thread thread  = this.thread;
	if( thread != null ) {
	  try {
	    thread.join( 500 );
	  }
	  catch( Exception ex ) {}
	}
	rv = true;
      }
    } else {
      rv = true;
    }
    if( rv ) {
      rv = super.doClose();
    }
    return rv;
  }


  @Override
  public void putSettingsTo( Properties props )
  {
    /*
     * Leer, da die Position dieser selbstaendig oeffnenden Fenster
     * vom System festgelegt wird und deshalb die Fensterposition
     * nicht gespeichert werden soll
     */
  }


  @Override
  protected boolean showPopupMenu( MouseEvent e )
  {
    return this.actionMngr.showPopupMenu( e );
  }


  @Override
  public void removeNotify()
  {
    super.removeNotify();
    if( this.notified ) {
      this.notified = false;
      if( this.fldLog != null ) {
	this.fldLog.removeMouseListener( this );
      }
      this.btnClose.removeActionListener( this );
    }
  }


	/* --- private Methoden --- */

  private boolean equalsToRenamedFile( File file )
  {
    boolean rv = false;
    try {
      rv = this.renamedPaths.contains(
			file.toPath().normalize().toAbsolutePath() );
    }
    catch( InvalidPathException ex ) {}
    return rv;
  }


  private void progressFinished()
  {
    this.btnClose.setText( LangUtil.getText( EmuUtil.TEXT_CLOSE ) );
    if( !this.cancelled && (this.errorCount > 0) ) {
      if( this.fldLog != null ) {
	this.fldLog.append( "\n" );
	this.fldLog.append( Integer.toString( this.errorCount ) );
	this.fldLog.append( " Fehler\n" );
	try {
	  Document doc = this.fldLog.getDocument();
	  if( doc != null ) {
	    this.fldLog.setCaretPosition( doc.getLength() );
	  }
	}
	catch( IllegalArgumentException ex ) {}
      }
      if( this.progressBar != null ) {
	this.progressBar.setMinimum( 0 );
	this.progressBar.setMaximum( 1 );
	this.progressBar.setValue( 0 );
      }
    } else {
      if( this.autoClose ) {
	doClose();
      }
    }
  }
}
