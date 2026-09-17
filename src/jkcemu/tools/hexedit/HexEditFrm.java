/*
 * (c) 2008-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Hex-Editor
 */

package jkcemu.tools.hexedit;

import java.awt.Dimension;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.EventObject;
import javax.naming.SizeLimitExceededException;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.HelpFrm;
import jkcemu.base.ReplyBytesDlg;
import jkcemu.file.Downloader;
import jkcemu.file.FileUtil;
import jkcemu.file.RecentDirsMngr;
import jkcemu.file.RecentFilesMngr;
import jkcemu.lang.LangUtil;
import jkcemu.print.PrintOptionsDlg;
import jkcemu.print.PrintUtil;
import jkcemu.text.TextFinder;


public class HexEditFrm
		extends AbstractHexCharFrm
		implements
			Downloader.Consumer,
			DropTargetListener,
			RecentFilesMngr.Listener
{
  public static final String TITLE = "hexedit.title.jkcemu_hex_editor";

  private static final String HELP_PAGE  = "/help/tools/hexeditor.htm";
  private static final int    BUF_EXTEND = 0x2000;

  private static HexEditFrm instance = null;

  private File            file;
  private String          fileName;
  private long            fileLastModified;
  private byte[]          dataBytes;
  private int             dataLen;
  private int             savedPos;
  private boolean         dataChanged;
  private boolean         readOnly;
  private RecentFilesMngr recentFilesMngr;
  private JMenuItem       mnuNew;
  private JMenuItem       mnuOpen;
  private JMenuItem       mnuSave;
  private JMenuItem       mnuSaveAs;
  private JMenuItem       mnuPrintOptions;
  private JMenuItem       mnuPrint;
  private JMenuItem       mnuClose;
  private JMenuItem       mnuBytesCopyHex;
  private JMenuItem       mnuBytesCopyAscii;
  private JMenuItem       mnuBytesCopyDump;
  private JMenuItem       mnuBytesInvert;
  private JMenuItem       mnuBytesReverse;
  private JMenuItem       mnuBytesSave;
  private JMenuItem       mnuBytesAppend;
  private JMenuItem       mnuBytesInsert;
  private JMenuItem       mnuBytesOverwrite;
  private JMenuItem       mnuBytesRemove;
  private JMenuItem       mnuFileInsert;
  private JMenuItem       mnuFileAppend;
  private JMenuItem       mnuSavePos;
  private JMenuItem       mnuGotoSavedPos;
  private JMenuItem       mnuSelectToSavedPos;
  private JMenuItem       mnuSelectAll;
  private JMenuItem       mnuChecksum;
  private JMenuItem       mnuFind;
  private JMenuItem       mnuFindNext;
  private JMenuItem       mnuHelpContent;
  private JButton         btnNew;
  private JButton         btnOpen;
  private JButton         btnSave;
  private JButton         btnFind;


  public static HexEditFrm open()
  {
    if( instance == null ) {
      instance = new HexEditFrm();
    }
    EmuUtil.showFrame( instance );
    return instance;
  }


  public static HexEditFrm open( byte[] data )
  {
    open();
    if( (data != null) && instance.confirmDataSaved() ) {
      instance.newFileInternal( data );
    }
    return instance;
  }


  public static HexEditFrm open( File file )
  {
    open();
    if( file != null ) {
      instance.openFile( file );
    }
    return instance;
  }


  public void openFile( File file )
  {
    if( instance.confirmDataSaved() )
      instance.openFileInternal( file, null, null, true );
  }


	/* --- Downloader.Consumer --- */

  @Override
  public void consume( byte[] dataBytes, String fileName )
  {
    if( instance.confirmDataSaved() )
      instance.openFileInternal( null, dataBytes, fileName, false );
  }


	/* --- DropTargetListener --- */

  @Override
  public void dragEnter( DropTargetDragEvent e )
  {
    if( !FileUtil.isFileDrop( e ) )
      e.rejectDrag();
  }


  @Override
  public void dragExit( DropTargetEvent e )
  {
    // leer
  }


  @Override
  public void dragOver( DropTargetDragEvent e )
  {
    // leer
  }


  @Override
  public void drop( DropTargetDropEvent e )
  {
    final File file = FileUtil.fileDrop( this, e );
    if( file != null ) {
      if( !Downloader.checkAndStart(
			this,
			file,
			Integer.MAX_VALUE,
			true,			// GZip-Dateien entpacken
			e,
			this ) )
      {
	// nicht auf Benutzerinteraktion warten
	EventQueue.invokeLater(
			new Runnable()
			{
			  @Override
			  public void run()
			  {
			    openFile( file );
			  }
			} );
      }
    }
  }


  @Override
  public void dropActionChanged( DropTargetDragEvent e )
  {
    // leer
  }


	/* --- RecentFilesMngr.Listener --- */

  @Override
  public void recentFileActionPerformed( String fileName )
  {
    openFile( new File( fileName ) );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src != null ) {
      if( (src == this.btnNew) || (src == this.mnuNew) ) {
	rv = true;
	if( confirmDataSaved() ) {
	  newFileInternal( null );
	}
      } else if( (src == this.btnOpen) || (src == this.mnuOpen) ) {
	rv = true;
	doOpen();
      } else if( (src == this.btnSave) || (src == this.mnuSave) ) {
	rv = true;
	doSave( false );
      } else if( src == this.mnuSaveAs ) {
	rv = true;
	doSave( true );
      } else if( src == this.mnuPrintOptions ) {
	rv = true;
	PrintOptionsDlg.showPrintOptionsDlg( this, true, true );
      } else if( src == this.mnuPrint ) {
	rv = true;
	PrintUtil.doPrint( this, this, "JKCEMU Hex-Editor" );
      } else if( src == this.mnuClose ) {
	rv = true;
	doClose();
      } else if( src == this.mnuBytesAppend ) {
	rv = true;
	doBytesAppend();
      } else if( src == this.mnuBytesCopyHex ) {
	rv = true;
	this.hexCharFld.copySelectedBytesAsHex();
      } else if( src == this.mnuBytesCopyAscii ) {
	rv = true;
	this.hexCharFld.copySelectedBytesAsAscii();
      } else if( src == this.mnuBytesCopyDump ) {
	rv = true;
	this.hexCharFld.copySelectedBytesAsDump();
      } else if( src == this.mnuBytesInvert ) {
	rv = true;
	doBytesInvert();
      } else if( src == this.mnuBytesReverse ) {
	rv = true;
	doBytesReverse();
      } else if( src == this.mnuBytesSave ) {
	rv = true;
	doBytesSave();
      } else if( src == this.mnuBytesInsert ) {
	rv = true;
	doBytesInsert();
      } else if( src == this.mnuBytesOverwrite ) {
	rv = true;
	doBytesOverwrite();
      } else if( src == this.mnuBytesRemove ) {
	rv = true;
	doBytesRemove();
      } else if( src == this.mnuFileAppend ) {
	rv = true;
	doFileAppend();
      } else if( src == this.mnuFileInsert ) {
	rv = true;
	doFileInsert();
      } else if( src == this.mnuSavePos ) {
	rv = true;
	doSavePos();
      } else if( src == this.mnuGotoSavedPos ) {
	rv = true;
	doGotoSavedPos( false );
      } else if( src == this.mnuSelectToSavedPos ) {
	rv = true;
	doGotoSavedPos( true );
      } else if( src == this.mnuSelectAll ) {
	rv = true;
	doSelectAll();
      } else if( src == this.mnuChecksum ) {
	rv = true;
	doChecksum();
      } else if( (src == this.btnFind) || (src == this.mnuFind) ) {
	rv = true;
	doFind();
      } else if( src == this.mnuFindNext ) {
	rv = true;
	doFindNext();
      } else if( src == this.mnuHelpContent ) {
	rv = true;
	HelpFrm.openPage( HELP_PAGE );
      } else {
	rv = super.doAction( e );
      }
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = false;
    if( confirmDataSaved() ) {
      if( Main.isTopFrm( this ) ) {
	rv = EmuUtil.closeOtherFrames( this );
	if( rv ) {
	  rv = super.doClose();
	}
	if( rv ) {
	  Main.exitSuccess();
	}
      } else {
	rv = super.doClose();
      }
      if( rv ) {
	// damit beim erneuten Oeffnen der Editor leer ist
	newFileInternal( null );
      }
    }
    return rv;
  }


  @Override
  public int getDataByte( int idx )
  {
    int rv = 0;
    if( this.dataBytes != null ) {
      if( (idx >= 0) && (idx < this.dataBytes.length) ) {
	rv = (int) this.dataBytes[ idx ] & 0xFF;
      }
    }
    return rv;
  }


  @Override
  public int getDataLength()
  {
    return this.dataLen;
  }


  @Override
  public boolean getDataReadOnly()
  {
    return false;
  }


  @Override
  protected void setContentActionsEnabled( boolean state )
  {
    this.mnuPrint.setEnabled( state );
    this.mnuSelectAll.setEnabled( state );
    this.mnuFind.setEnabled( state );
    this.btnFind.setEnabled( state );
  }


  @Override
  public boolean setDataByte( int idx, int value )
  {
    boolean rv = false;
    if( (idx >= 0) && (idx <= this.dataLen) ) {
      this.dataBytes[ idx ] = (byte) value;
      setDataChanged( true );
      rv = true;
    }
    return rv;
  }


  @Override
  protected void setFindNextActionsEnabled( boolean state )
  {
    this.mnuFindNext.setEnabled( state );
  }


  @Override
  protected void setSelectedByteActionsEnabled( boolean state )
  {
    this.mnuBytesCopyHex.setEnabled( state );
    this.mnuBytesCopyAscii.setEnabled( state );
    this.mnuBytesCopyDump.setEnabled( state );
    this.mnuBytesInvert.setEnabled( state );
    this.mnuBytesReverse.setEnabled( state );
    this.mnuBytesSave.setEnabled( state );
    this.mnuBytesInsert.setEnabled( state );
    this.mnuBytesOverwrite.setEnabled( state );
    this.mnuBytesRemove.setEnabled( state );
    this.mnuFileInsert.setEnabled( state );
    this.mnuChecksum.setEnabled( state );
    this.mnuSavePos.setEnabled( state );
    this.mnuSelectToSavedPos.setEnabled( state && (this.savedPos >= 0) );
  }


	/* --- Aktionen --- */

  private void doBytesAppend()
  {
    ReplyBytesDlg dlg = new ReplyBytesDlg(
					this,
					LangUtil.getText(
						"hexedit.text.append_bytes" ),
					this.recentInputFmt,
					this.recentBigEndian,
					null );
    dlg.setVisible( true );
    byte[] a = dlg.getApprovedBytes();
    if( a != null ) {
      if( a.length > 0 ) {
	int oldLen = this.dataLen;
	this.recentInputFmt  = dlg.getApprovedInputFormat();
	this.recentBigEndian = dlg.getApprovedBigEndian();
	try {
	  insertBytes( this.dataLen, a, 0 );
	}
	catch( SizeLimitExceededException ex ) {
	  BaseDlg.showErrorDlg( this, ex.getMessage() );
	}
	setDataChanged( true );
	updView();
	setSelection( oldLen, this.dataLen - 1 );
      }
    }
  }


  private void doBytesInsert()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    if( (caretPos >= 0) && (caretPos < this.dataLen) ) {
      ReplyBytesDlg dlg = new ReplyBytesDlg(
					this,
					LangUtil.getText(
						"hexedit.text.insert_bytes" ),
					this.recentInputFmt,
					this.recentBigEndian,
					null );
      dlg.setVisible( true );
      byte[] a = dlg.getApprovedBytes();
      if( a != null ) {
	if( a.length > 0 ) {
	  this.recentInputFmt  = dlg.getApprovedInputFormat();
	  this.recentBigEndian = dlg.getApprovedBigEndian();
	  try {
	    insertBytes( caretPos, a, 0 );
	  }
	  catch( SizeLimitExceededException ex ) {
	    BaseDlg.showErrorDlg( this, ex.getMessage() );
	  }
	  setDataChanged( true );
	  updView();
	  setSelection( caretPos, caretPos + a.length - 1 );
	}
      }
    }
  }


  private void doBytesInvert()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    int markPos  = this.hexCharFld.getMarkPosition();
    int m1       = -1;
    int m2       = -1;
    if( (caretPos >= 0) && (markPos >= 0) ) {
      m1 = Math.min( caretPos, markPos );
      m2 = Math.max( caretPos, markPos );
    } else {
      m1 = caretPos;
      m2 = caretPos;
    }
    if( m2 >= this.dataLen ) {
      m2 = this.dataLen - 1;
    }
    if( m1 >= 0 ) {
      String msg = null;
      if( m2 > m1 ) {
	msg = String.format(
		"M\u00F6chten Sie die %d ausgew\u00E4hlten Bytes"
			+ " invertieren?",
		m2 - m1 + 1);
      }
      else if( m2 == m1 ) {
	msg = String.format(
		"M\u00F6chten Sie das ausgew\u00E4hlte Byte"
			+ " mit dem Wert %02Xh invertieren?\n"
			+ "invertierte Wert: %02Xh",
		this.dataBytes[ m1 ] & 0xFF,
		~this.dataBytes[ m1 ] & 0xFF );
      }
      if( msg != null ) {
	if( BaseDlg.showYesNoDlg( this, msg ) ) {
	  int p = m1;
	  while( p <= m2 ) {
	    this.dataBytes[ p ] = (byte) ~this.dataBytes[ p ];
	    p++;
	  }
	  setDataChanged( true );
	  updView();
	  setSelection( m1, m2 );
	}
      }
    }
  }


  private void doBytesOverwrite()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    if( (caretPos >= 0) && (caretPos < this.dataLen) ) {
      ReplyBytesDlg dlg = new ReplyBytesDlg(
					this,
					LangUtil.getText( "hexedit.text.overwrite_bytes" ),
					this.recentInputFmt,
					this.recentBigEndian,
					null );
      dlg.setVisible( true );
      byte[] a = dlg.getApprovedBytes();
      if( a != null ) {
	if( a.length > 0 ) {
	  this.recentInputFmt  = dlg.getApprovedInputFormat();
	  this.recentBigEndian = dlg.getApprovedBigEndian();
	  try {
	    int src = 0;
	    int dst = caretPos;
	    while( (src < a.length) && (dst < this.dataLen) ) {
	      this.dataBytes[ dst++ ] = a[ src++ ];
	    }
	    if( src < a.length ) {
	      insertBytes( dst, a, src );
	    }
	  }
	  catch( SizeLimitExceededException ex ) {
	    BaseDlg.showErrorDlg( this, ex.getMessage() );
	  }
	  setDataChanged( true );
	  updView();
	  setSelection( caretPos, caretPos + a.length - 1 );
	}
      }
    }
  }


  private void doBytesRemove()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    int markPos  = this.hexCharFld.getMarkPosition();
    int m1       = -1;
    int m2       = -1;
    if( (caretPos >= 0) && (markPos >= 0) ) {
      m1 = Math.min( caretPos, markPos );
      m2 = Math.max( caretPos, markPos );
    } else {
      m1 = caretPos;
      m2 = caretPos;
    }
    if( m2 >= this.dataLen ) {
      m2 = this.dataLen - 1;
    }
    if( m1 >= 0 ) {
      String msg = null;
      if( m2 > m1 ) {
	msg = String.format(
		"M\u00F6chten Sie die %d ausgew\u00E4hlten Bytes entfernen?",
		m2 - m1 + 1);
      }
      else if( m2 == m1 ) {
	msg = String.format(
		"M\u00F6chten das ausgew\u00E4hlte Byte"
			+ " mit dem Wert %02Xh entfernen?",
		this.dataBytes[ m1 ] );
      }
      if( msg != null ) {
	if( BaseDlg.showYesNoDlg( this, msg ) ) {
	  if( m2 + 1 < this.dataLen ) {
	    m2++;
	    while( m2 < this.dataLen ) {
	      this.dataBytes[ m1++ ] = this.dataBytes[ m2++ ];
	    }
	  }
	  this.dataLen = m1;
	  setDataChanged( true );
	  updView();
	  setCaretPosition( m1, false );
	}
      }
    }
  }


  private void doBytesReverse()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    int markPos  = this.hexCharFld.getMarkPosition();
    int m1       = -1;
    int m2       = -1;
    if( (caretPos >= 0) && (markPos >= 0) ) {
      m1 = Math.min( caretPos, markPos );
      m2 = Math.max( caretPos, markPos );
    } else {
      m1 = caretPos;
      m2 = caretPos;
    }
    if( m2 >= this.dataLen ) {
      m2 = this.dataLen - 1;
    }
    if( m1 >= 0 ) {
      String msg = null;
      if( m2 > m1 ) {
	msg = String.format(
		"M\u00F6chten Sie die %d ausgew\u00E4hlten Bytes"
			+ " spiegeln?\n"
			+ "Bits tauschen: 0-7, 1-6, 2-5, 3-4",
		m2 - m1 + 1);
      }
      else if( m2 == m1 ) {
	msg = String.format(
		"M\u00F6chten Sie das ausgew\u00E4hlte Byte"
			+ " mit dem Wert %02Xh spiegeln?\n"
			+ "gespiegelter Wert: %02Xh",
		(int) this.dataBytes[ m1 ] & 0xFF,
		toReverseByte( this.dataBytes[ m1 ] ) );
      }
      if( msg != null ) {
	if( BaseDlg.showYesNoDlg( this, msg ) ) {
	  int p = m1;
	  while( p <= m2 ) {
	    this.dataBytes[ p ] = (byte) toReverseByte( this.dataBytes[ p ] );
	    p++;
	  }
	  setDataChanged( true );
	  updView();
	  setSelection( m1, m2 );
	}
      }
    }
  }


  private void doBytesSave()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    int markPos  = this.hexCharFld.getMarkPosition();
    int m1       = -1;
    int m2       = -1;
    if( (caretPos >= 0) && (markPos >= 0) ) {
      m1 = Math.min( caretPos, markPos );
      m2 = Math.max( caretPos, markPos );
    } else {
      m1 = caretPos;
      m2 = caretPos;
    }
    if( m2 >= this.dataLen ) {
      m2 = this.dataLen - 1;
    }
    if( m1 >= 0 ) {
      int len = Math.min( m2, this.dataBytes.length ) - m1 + 1;
      if( len > 0 ) {
	File file = FileUtil.showFileSaveDlg(
			this,
			LangUtil.getText( "hexedit.title.save_file" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_HEXEDIT ) );
	if( file != null ) {
	  if( saveFile( file, m1, len ) ) {
	    setRecentFile( file );
	  }
	}
      }
    }
  }


  private void doFileAppend()
  {
    File file = FileUtil.showFileOpenDlg(
			this,
			LangUtil.getText( "hexedit.title.append_file" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_HEXEDIT ) );
    if( file != null ) {
      try {
	int  oldLen  = this.dataLen;
	long dataLen = file.length();
	if( (dataLen > 0)
	    && ((dataLen + (long) oldLen) > Integer.MAX_VALUE) )
	{
	  throwFileTooBig();
	}
	byte[] a = FileUtil.readFile( file, false, Integer.MAX_VALUE );
	if( a != null ) {
	  if( a.length > 0 ) {
	    try {
	      insertBytes( this.dataLen, a, 0 );
	    }
	    catch( SizeLimitExceededException ex ) {
	      BaseDlg.showErrorDlg( this, ex.getMessage() );
	    }
	    setDataChanged( true );
	    updView();
	    setSelection( oldLen, this.dataLen - 1 );
	    setRecentFile( file );
	  }
	}
      }
      catch( IOException ex ) {
	BaseDlg.showErrorDlg( this, ex );
      }
    }
  }


  private void doFileInsert()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    if( (caretPos >= 0) && (caretPos < this.dataLen) ) {
      File file = FileUtil.showFileOpenDlg(
			this,
			LangUtil.getText( "hexedit.title.insert_file" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_HEXEDIT ) );
      if( file != null ) {
	try {
	  long dataLen = file.length();
	  if( (dataLen > 0)
	      && ((dataLen + (long) this.dataLen) > Integer.MAX_VALUE) )
	  {
	    throwFileTooBig();
	  }
	  byte[] a = FileUtil.readFile( file, false, Integer.MAX_VALUE );
	  if( a != null ) {
	    if( a.length > 0 ) {
	      try {
		insertBytes( caretPos, a, 0 );
	      }
	      catch( SizeLimitExceededException ex ) {
		BaseDlg.showErrorDlg( this, ex.getMessage() );
	      }
	      setDataChanged( true );
	      updView();
	      setSelection( caretPos, caretPos + a.length - 1 );
	      setRecentFile( file );
	    }
	  }
	}
	catch( IOException ex ) {
	  BaseDlg.showErrorDlg( this, ex );
	}
      }
    }
  }


  private void doGotoSavedPos( boolean moveOp )
  {
    if( this.savedPos >= 0 ) {
      this.hexCharFld.setCaretPosition( this.savedPos, moveOp );
      updCaretPosFields();
    }
  }


  private void doOpen()
  {
    if( confirmDataSaved() ) {
      File file = FileUtil.showFileOpenDlg(
			this,
			LangUtil.getText( "hexedit.title.open_file" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_HEXEDIT ) );
      if( file != null ) {
	openFileInternal( file, null, null, true );
      }
    }
  }


  private boolean doSave( boolean forceFileDlg )
  {
    boolean rv   = false;
    File    file = this.file;
    if( forceFileDlg || (file == null) ) {
      File preSelection = file;
      if( preSelection == null ) {
	if( this.fileName != null ) {
	  preSelection = new File( this.fileName );
	} else {
	  preSelection = RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_HEXEDIT );
	}
      }
      file = FileUtil.showFileSaveDlg(
				this,
				LangUtil.getText( "hexedit.title.save_file" ),
				preSelection );
    }
    if( file != null ) {
      if( FileUtil.checkNoFileSaveConflict(
			this,
			file,
			this.file,
			this.fileLastModified ) )
      {
	if( saveFile( file, 0, this.dataLen ) ) {
	  this.fileLastModified = (new File( file.getPath() )).lastModified();
	  this.file             = file;
	  this.readOnly         = false;
	  rv                    = true;
	  setDataChanged( false );
	  setRecentFile( file );
	  updTitle();
	}
      }
    }
    return rv;
  }


  private void doSavePos()
  {
    int caretPos = this.hexCharFld.getCaretPosition();
    if( caretPos >= 0 ) {
      this.savedPos = caretPos;
      this.mnuGotoSavedPos.setEnabled( true );
      this.mnuSelectToSavedPos.setEnabled( true );
    }
  }


	/* --- Konstruktor --- */

  private HexEditFrm()
  {
    this.file             = null;
    this.fileName         = null;
    this.fileLastModified = -1;
    this.dataBytes        = new byte[ 0x100 ];
    this.dataLen          = 0;
    this.savedPos         = -1;
    this.dataChanged      = false;
    this.recentFilesMngr  = RecentFilesMngr.getLazyInstance(
				this,
				RecentFilesMngr.App.HEX_EDITOR );
    updTitle();


    // Menu Datei
    JMenu mnuFile = createMenuFile();

    this.mnuNew = createMenuItem( LangUtil.getText( "common.action.new" ) );
    mnuFile.add( this.mnuNew );

    this.mnuOpen = createMenuItem(
		LangUtil.getText( EmuUtil.TEXT_OPEN_OPEN ) );
    mnuFile.add( this.mnuOpen );
    mnuFile.addSeparator();

    this.mnuSave = createMenuItemWithStandardAccelerator(
						LangUtil.getText(
							EmuUtil.TEXT_SAVE ),
						KeyEvent.VK_S );
    this.mnuSave.setEnabled( false );
    mnuFile.add( this.mnuSave );

    this.mnuSaveAs = createMenuItemSaveAs( true );
    mnuFile.add( this.mnuSaveAs );

    if( this.recentFilesMngr != null ) {
      mnuFile.add( this.recentFilesMngr.getMenu() );
    }
    mnuFile.addSeparator();

    this.mnuPrintOptions = createMenuItemOpenPrintOptions();
    mnuFile.add( this.mnuPrintOptions );

    this.mnuPrint = createMenuItemOpenPrint( true );
    this.mnuPrint.setEnabled( false );
    mnuFile.add( this.mnuPrint );
    mnuFile.addSeparator();

    this.mnuClose = createMenuItemClose();
    mnuFile.add( this.mnuClose );


    // Menu Bearbeiten
    JMenu mnuEdit = createMenuEdit();

    this.mnuBytesCopyHex = createMenuItem(
		LangUtil.getText( "common.action.copy_selected_bytes_hexadecimal" ) );
    this.mnuBytesCopyHex.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyHex );

    this.mnuBytesCopyAscii = createMenuItem(
		LangUtil.getText( "common.action.copy_selected_bytes_ascii" ) );
    this.mnuBytesCopyAscii.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyAscii );

    this.mnuBytesCopyDump = createMenuItem(
		LangUtil.getText( "common.action.copy_selected_bytes_hex" ) );
    this.mnuBytesCopyDump.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyDump );
    mnuEdit.addSeparator();

    this.mnuBytesInsert = createMenuItemWithStandardAccelerator(
					LangUtil.getText( "hexedit.action.insert_bytes" ),
					KeyEvent.VK_I );
    this.mnuBytesInsert.setEnabled( false );
    mnuEdit.add( this.mnuBytesInsert );

    this.mnuBytesOverwrite = createMenuItemWithStandardAccelerator(
					LangUtil.getText( "hexedit.action.overwrite_bytes" ),
					KeyEvent.VK_O );
    this.mnuBytesOverwrite.setEnabled( false );
    mnuEdit.add( this.mnuBytesOverwrite );

    this.mnuBytesAppend = createMenuItemWithStandardAccelerator(
					LangUtil.getText( "hexedit.action.append_bytes_end" ),
					KeyEvent.VK_E );
    mnuEdit.add( this.mnuBytesAppend );
    mnuEdit.addSeparator();

    this.mnuBytesSave = createMenuItem(
				LangUtil.getText( "hexedit.action.save_selected_bytes" ) );
    this.mnuBytesSave.setEnabled( false );
    mnuEdit.add( this.mnuBytesSave );

    this.mnuBytesInvert = createMenuItem(
				LangUtil.getText( "hexedit.action.invert_selected_bytes" ) );
    this.mnuBytesInvert.setEnabled( false );
    mnuEdit.add( this.mnuBytesInvert );

    this.mnuBytesReverse = createMenuItem(
				LangUtil.getText( "hexedit.action.mirror_selected_bytes" ) );
    this.mnuBytesReverse.setEnabled( false );
    mnuEdit.add( this.mnuBytesReverse );

    this.mnuBytesRemove = createMenuItemWithDirectAccelerator(
				LangUtil.getText( "hexedit.action.remove_selected_bytes" ),
				KeyEvent.VK_DELETE );
    this.mnuBytesRemove.setEnabled( false );
    mnuEdit.add( this.mnuBytesRemove );
    mnuEdit.addSeparator();

    this.mnuFileInsert = createMenuItem(
		LangUtil.getText( "hexedit.action.insert_file" ) );
    this.mnuFileInsert.setEnabled( false );
    mnuEdit.add( this.mnuFileInsert );

    this.mnuFileAppend = createMenuItem( LangUtil.getText(
			"hexedit.action.append_file_end" ) );
    mnuEdit.add( this.mnuFileAppend );
    mnuEdit.addSeparator();

    this.mnuSavePos = createMenuItem(
		LangUtil.getText( "hexedit.action.remember_position" ) );
    this.mnuSavePos.setEnabled( false );
    mnuEdit.add( this.mnuSavePos );

    this.mnuGotoSavedPos = createMenuItem(
				LangUtil.getText( "hexedit.action.jump_remembered_position" ) );
    this.mnuGotoSavedPos.setEnabled( false );
    mnuEdit.add( this.mnuGotoSavedPos );

    this.mnuSelectToSavedPos = createMenuItem(
			LangUtil.getText(
				"hexedit.action.select_up_remembered_position" ) );
    this.mnuSelectToSavedPos.setEnabled( false );
    mnuEdit.add( this.mnuSelectToSavedPos );

    this.mnuSelectAll = createMenuItemSelectAll( true );
    this.mnuSelectAll.setEnabled( false );
    mnuEdit.add( this.mnuSelectAll );
    mnuEdit.addSeparator();

    this.mnuChecksum = createMenuItem( LangUtil.getText(
			"hexedit.action.checksum_hash_value" ) );
    this.mnuChecksum.setEnabled( false );
    mnuEdit.add( this.mnuChecksum );
    mnuEdit.addSeparator();

    this.mnuFind = createMenuItemOpenFind( true );
    this.mnuFind.setEnabled( false );
    mnuEdit.add( this.mnuFind );

    this.mnuFindNext = createMenuItemFindNext( true );
    this.mnuFindNext.setEnabled( false );
    mnuEdit.add( this.mnuFindNext );


    // Einstellungen
    JMenu mnuSettings = createMenuSettings();
    addDirectEditMenuItemTo( mnuSettings );


    // Menu Hilfe
    JMenu mnuHelp       = createMenuHelp();
    this.mnuHelpContent = createMenuItem( LangUtil.getText(
			"hexedit.action.help_hex_editor" ) );
    mnuHelp.add( this.mnuHelpContent );


    // Menu
    setJMenuBar( GUIFactory.createMenuBar(
					mnuFile,
					mnuEdit,
					mnuSettings,
					mnuHelp ) );


    // Fensterinhalt
    setLayout( new GridBagLayout() );

    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					1, 1,
					1.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.HORIZONTAL,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );


    // Werkzeugleiste
    JToolBar toolBar = GUIFactory.createToolBar();
    toolBar.setFloatable( false );
    toolBar.setBorderPainted( false );
    toolBar.setOrientation( JToolBar.HORIZONTAL );
    toolBar.setRollover( true );
    add( toolBar, gbc );

    this.btnNew = GUIFactory.createRelImageResourceButton(
					this,
					"file/new.png",
					this.mnuNew.getText() );
    this.btnNew.addActionListener( this );
    toolBar.add( this.btnNew );

    this.btnOpen = GUIFactory.createRelImageResourceButton(
					this,
					"file/open.png",
					LangUtil.getText(
						EmuUtil.TEXT_OPEN ) );
    this.btnOpen.addActionListener( this );
    toolBar.add( this.btnOpen );

    this.btnSave = GUIFactory.createRelImageResourceButton(
					this,
					"file/save.png",
					LangUtil.getText(
						EmuUtil.TEXT_SAVE ) );
    this.btnSave.setEnabled( false );
    this.btnSave.addActionListener( this );
    toolBar.add( this.btnSave );
    toolBar.addSeparator();

    this.btnFind = GUIFactory.createRelImageResourceButton(
					this,
					"edit/find.png",
					LangUtil.getText(
						EmuUtil.TEXT_FIND ) );
    this.btnFind.setEnabled( false );
    this.btnFind.addActionListener( this );
    toolBar.add( this.btnFind );


    // Hex-ASCII-Anzeige
    gbc.anchor  = GridBagConstraints.CENTER;
    gbc.fill    = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    gbc.gridy++;
    add( createHexCharFld(), gbc );
    this.hexCharFld.setPreferredSize(
	new Dimension( this.hexCharFld.getDefaultPreferredWidth(), 300 ) );

    // Anzeige der Cursor-Position
    gbc.fill    = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0.0;
    gbc.gridy++;
    add( createCaretPosFld( "Cursor-Position" ), gbc );

    // Anzeige der Dezimalwerte der Bytes ab Cursor-Position
    gbc.gridy++;
    add( createValueFld(), gbc );


    // Drag&Drop aktivieren
    (new DropTarget( this.hexCharFld, this )).setActive( true );


    // sonstiges
    setResizable( true );
    if( !applySettings( Main.getProperties() ) ) {
      pack();
      setScreenCentered();
    }
    this.hexCharFld.setPreferredSize( null );
  }


	/* --- private Methoden --- */

  private boolean confirmDataSaved()
  {
    boolean rv = true;
    if( this.dataChanged ) {
      setState( Frame.NORMAL );
      toFront();
      String[] options = LangUtil.getTexts( new String[] {
			LangUtil.getText( EmuUtil.TEXT_SAVE ),
			LangUtil.getText( "common.text.discard" ),
			LangUtil.getText( EmuUtil.TEXT_CANCEL ) } );
      int selOpt = JOptionPane.showOptionDialog(
				this,
				LangUtil.getText(
					"hexedit.text.file_changed_not_saved" ),
				LangUtil.getText( "common.text.data_changed" ),
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				options,
				options[ 0 ] );
      if( selOpt == 0 ) {
	rv = doSave( false );
      }
      else if( selOpt != 1 ) {
	rv = false;
      }
    }
    return rv;
  }


  private void insertBytes(
			int    dstPos,
			byte[] srcBuf,
			int    srcPos ) throws SizeLimitExceededException
  {
    if( (srcPos >= 0) && (srcPos < srcBuf.length) && (srcBuf.length > 0) ) {
      int diffLen = srcBuf.length - srcPos;
      int reqLen  = this.dataLen + diffLen;
      if( reqLen >= this.dataBytes.length ) {
	int n = Math.min( reqLen + BUF_EXTEND, Integer.MAX_VALUE );
	if( n < reqLen) {
	  throw new SizeLimitExceededException( "Die max. zul\u00E4ssige"
			+ " Dateigr\u00F6\u00DFe wurde erreicht." );
	}
	byte[] tmpBuf = new byte[ n ];
	if( dstPos > 0 ) {
	  System.arraycopy( this.dataBytes, 0, tmpBuf, 0, dstPos );
	}
	System.arraycopy( srcBuf, srcPos, tmpBuf, dstPos, diffLen );
	if( dstPos < this.dataLen ) {
	  System.arraycopy(
			this.dataBytes,
			dstPos,
			tmpBuf,
			dstPos + diffLen,
			this.dataLen - dstPos );
	}
	this.dataBytes = tmpBuf;
      } else {
	for( int i = this.dataLen - 1; i >= dstPos; --i ) {
	  this.dataBytes[ i + diffLen ] = this.dataBytes[ i ];
	}
	System.arraycopy( srcBuf, srcPos, this.dataBytes, dstPos, diffLen );
      }
      this.dataLen += diffLen;
    }
  }


  private void newFileInternal( byte[] data )
  {
    if( data != null ) {
      this.dataBytes = new byte[ data.length + 0x100 ];
      if( data.length > 0 ) {
	System.arraycopy( data, 0, this.dataBytes, 0, data.length );
      }
      this.dataLen = data.length;
    } else {
      this.dataBytes = new byte[ 0x100 ];
      this.dataLen   = 0;
    }
    this.file     = null;
    this.fileName = null;
    setDataChanged( false );
    updTitle();
    updView();
    setCaretPosition( -1, false );
  }


  private void openFileInternal(
			File    file,
			byte[]  fileBytes,
			String  fileName,
			boolean updRecentFile )
  {
    try {
      String           msg = null;
      RandomAccessFile raf = null;
      try {
	boolean readOnly = false;
	int     dataLen  = 0;
	if( fileBytes != null ) {
	  dataLen = fileBytes.length;
	} else {
	  if( file != null ) {
	    long fileLastModified = file.lastModified();
	    long len              = file.length();
	    if( len > Integer.MAX_VALUE ) {
	      throwFileTooBig();
	    }
	    if( len > 0 ) {
	      len = len * 10L / 9L;
	    }
	    if( len < BUF_EXTEND ) {
	      len = BUF_EXTEND;
	    } else if( len > Integer.MAX_VALUE ) {
	      len = Integer.MAX_VALUE;
	    }
	    fileBytes = new byte[ (int) len ];

	    readOnly = !file.canWrite();
	    raf      = new RandomAccessFile( file, readOnly ? "r" : "rw" );

	    FileLock lock = null;
	    try {
	      if( !readOnly ) {
		try {
		  lock = raf.getChannel().tryLock();
		}
		catch( OverlappingFileLockException ex ) {}
		if( lock == null ) {
		  readOnly = true;
		  msg      = file.getPath() + ":\n"
				+ FileUtil.MSG_FILE_NOT_WRITABLE_BECAUSE_LOCK;
		}
	      }
	      while( dataLen < fileBytes.length ) {
		int n = raf.read(
			fileBytes,
			dataLen,
			fileBytes.length - dataLen );
		if( n <= 0 ) {
		  break;
		}
		dataLen += n;
	      }
	      if( dataLen >= fileBytes.length ) {
		int b = raf.read();
		while( b >= 0 ) {
		  if( dataLen >= fileBytes.length ) {
		    int n = Math.min(
				dataLen + BUF_EXTEND,
				Integer.MAX_VALUE );
		    if( dataLen >= n ) {
		      throwFileTooBig();
		    }
		    byte[] a = new byte[ n ];
		    System.arraycopy( fileBytes, 0, a, 0, dataLen );
		    fileBytes = a;
		  }
		  fileBytes[ dataLen++ ] = (byte) b;
		  b = raf.read();
		}
	      }
	    }
	    finally {
	      FileUtil.releaseSilently( lock );
	    }
	    raf.close();
	    raf = null;
	  }
	}
	if( fileBytes == null ) {
	  fileBytes = new byte[ 0x100 ];
	}
	this.file             = file;
	this.fileLastModified = fileLastModified;
	this.fileName         = fileName;
	this.dataBytes        = fileBytes;
	this.dataLen          = dataLen;
	this.readOnly         = readOnly;
	this.savedPos         = -1;
	this.mnuGotoSavedPos.setEnabled( false );
	this.mnuSelectToSavedPos.setEnabled( false );
	updTitle();
	updView();
	setCaretPosition( 0, false );
	if( updRecentFile ) {
	  setRecentFile( file );
	}
      }
      finally {
	EmuUtil.closeSilently( raf );
      }
      if( msg != null ) {
	BaseDlg.showInfoDlg( this, msg );
      }
    }
    catch( IOException ex ) {
      BaseDlg.showErrorDlg( this, ex );
    }
  }


  private boolean saveFile( File file, int offs, int len )
  {
    boolean rv = false;
    try {
      RandomAccessFile raf = null;
      try {
	raf = new RandomAccessFile( file, "rw" );

	FileLock lock = null;
	try {
	  lock = raf.getChannel().tryLock();
	  if( lock == null ) {
	    throw new OverlappingFileLockException();
	  }
	  raf.seek( 0 );
	  raf.setLength( 0 );
	  if( (offs >= 0) && (offs < this.dataBytes.length) ) {
	    raf.write(
		this.dataBytes,
		offs,
		Math.min(
			this.dataLen - offs,
			this.dataBytes.length + offs ) );
	  }
	  raf.getChannel().force( false );
	}
	finally {
	  FileUtil.releaseSilently( lock );
	}
	raf.close();
	raf = null;
	rv  = true;
      }
      finally {
	EmuUtil.closeSilently( raf );
      }
    }
    catch( OverlappingFileLockException ex ) {
      BaseDlg.showErrorDlg(
		this,
		file.getPath() + ":\n"
			+ FileUtil.MSG_FILE_NOT_WRITABLE_BECAUSE_LOCK );
    }
    catch( IOException ex ) {
      BaseDlg.showErrorDlg( this, ex );
    }
    return rv;
  }


  private void setDataChanged( boolean state )
  {
    this.dataChanged = state;
    updTitle();
    this.mnuSave.setEnabled( this.dataChanged );
    this.btnSave.setEnabled( this.dataChanged );
  }


  private void setRecentFile( File file )
  {
    if( file != null ) {
      RecentDirsMngr.setRecentDir( file, RecentDirsMngr.FILE_CAT_HEXEDIT );
      if( this.recentFilesMngr != null ) {
	this.recentFilesMngr.setRecentFile( file );
      }
    }
  }


  private static void throwFileTooBig() throws IOException
  {
    throw new IOException( LangUtil.getText( "common.error.file_large" ) );
  }


  private static int toReverseByte( int b )
  {
    return ((b >> 7) & 0x01)
		| ((b >> 5) & 0x02)
		| ((b >> 3) & 0x04)
		| ((b >> 1) & 0x08)
		| ((b << 1) & 0x10)
		| ((b << 3) & 0x20)
		| ((b << 5) & 0x40)
		| ((b << 7) & 0x80);
  }


  private void updTitle()
  {
    String fileText = null;
    if( this.file != null ) {
      fileText = this.file.getPath();
    } else if( this.fileName != null ) {
      fileText = this.fileName;
    } else {
      fileText = LangUtil.getText( "tools.text.new_file" );
    }
    String title = LangUtil.getText( TITLE ) + ": " + fileText;
    if( this.readOnly ) {
      title = LangUtil.getText( "hexedit.text.write_protected", title );
    }
    setTitle( title );
  }
}
