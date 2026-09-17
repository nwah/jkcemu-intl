/*
 * (c) 2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Fenster zum Entpacken CP/M-kompatibler Diskettenabbilddateien
 */

package jkcemu.disk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventObject;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.BaseFrm;
import jkcemu.base.DesktopHelper;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.HelpFrm;
import jkcemu.base.UserCancelException;
import jkcemu.base.UserInputException;
import jkcemu.file.DirSelectDlg;
import jkcemu.file.FileEntry;
import jkcemu.file.FileNameFld;
import jkcemu.file.FileTimesData;
import jkcemu.file.FileUtil;
import jkcemu.file.RecentDirsMngr;
import jkcemu.lang.LangUtil;
import jkcemu.text.TextUtil;


public class DiskImgUnpackFrm
			extends BaseFrm
			implements DropTargetListener
{
  public static final String TITLE = "disk.title.jkcemu_unpack_cp";

  private static final String HELP_PAGE = "/help/disk/unpackdiskimg.htm";

  private static final String SYS_TRACKS_FILENAME   = "@boot.sys";
  private static final String FILE_ERROR_SUFFIX     = ".error";
  private static final String FILE_TRUNCATED_SUFFIX = ".truncated";

  private static final String PROP_APPLY_READONLY  = "apply_readonly";
  private static final String PROP_FORCE_LOWERCASE = "force_lowercase";
  private static final String PROP_UNPACK_DELETED  = "unpack_deleted_files";

  private static final boolean DEFAULT_APPLY_READONLY  = false;
  private static final boolean DEFAULT_FORCE_LOWERCASE = false;
  private static final boolean DEFAULT_UNPACK_DELETED  = false;

  private static final Color COLOR_HIGHLIGHTED = Color.RED;
  private static final Color COLOR_EMPHASIZED  = Color.RED;
  private static final Color COLOR_RECOGNIZED  = new Color( 0xFF007F00 );

  private static DiskImgUnpackFrm instance = null;

  private int                        diskSides;
  private int                        blockSize;
  private int                        sectorSize;
  private int                        sectorsPerBlock;
  private int                        sysBytesOffs;
  private int                        sysBytesLen;
  private byte[]                     sysBytes;
  private byte[]                     dirBytes;
  private byte[]                     timeBytes;
  private File                       outDir;
  private boolean                    blockNum16Bit;
  private boolean                    blockSizeTooBig;
  private boolean                    blockSizeTooSmall;
  private boolean                    fileTruncated;
  private boolean                    fileCRCErr;
  private boolean                    fileErr;
  private boolean                    unpackDeleted;
  private boolean                    unpackErr;
  private boolean                    sysBytesCRCErr;
  private boolean                    applyReadOnly;
  private boolean                    forceLowerCase;
  private boolean                    dataAreaTruncated;
  private java.util.List<SectorData> dataSectors;
  private JLabel                     infoBlockNumSize;
  private JLabel                     infoBlockSize;
  private JLabel                     labelBlockNumSize;
  private JLabel                     labelBlockSize;
  private JLabel                     labelOutDir;
  private JCheckBox                  cbApplyReadOnly;
  private JCheckBox                  cbForceLowerCase;
  private JCheckBox                  cbUnpackDeleted;
  private JComboBox<Object>          comboBlockSize;
  private FileNameFld                fldDiskFile;
  private JTextField                 fldRemark;
  private JTextField                 fldOutDir;
  private JTextArea                  fldLog;
  private JRadioButton               rbBlockNum8Bit;
  private JRadioButton               rbBlockNum16Bit;
  private JButton                    btnDiskFileOpen;
  private JButton                    btnDiskFileRemove;
  private JButton                    btnOutDirOpen;
  private JButton                    btnOutDirSelect;
  private JButton                    btnClose;
  private JButton                    btnCopyLog;
  private JButton                    btnHelp;
  private JButton                    btnDiskFileUnpack;


  public static DiskImgUnpackFrm open( File file )
  {
    if( instance == null ) {
      instance = new DiskImgUnpackFrm();
    }
    EmuUtil.showFrame( instance );
    if( file != null ) {
      instance.fireOpenFile( file );
    }
    return instance;
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
  public void drop( final DropTargetDropEvent e )
  {
    if( FileUtil.isFileDrop( e ) ) {
      e.acceptDrop( DnDConstants.ACTION_COPY );  // Quelle nicht loeschen
      try {
	Transferable t = e.getTransferable();
	if( t != null ) {
	  final Object o = t.getTransferData(
			      DataFlavor.javaFileListFlavor );
	  if( o != null ) {
	    if( o instanceof Collection ) {
	      if( ((Collection) o).size() > 0 ) {
		try {
		   fireOpenFile( ((Collection) o).iterator().next() );
		}
		catch( NoSuchElementException ex ) {}
	      }
	    }
	  }
	}
      }
      catch( IOException ex1 ) {}
      catch( UnsupportedFlavorException ex2 ) {}
    }
  }


  @Override
  public void dropActionChanged( DropTargetDragEvent e )
  {
    // leer
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = false;
    try {
      Object  src = e.getSource();
      if( src == this.btnDiskFileOpen ) {
	rv = true;
	doDiskFileOpen();
      }
      else if( src == this.btnDiskFileRemove ) {
	rv = true;
	doDiskFileRemove();
      }
      else if( src == this.btnOutDirOpen ) {
	rv = true;
	doOutDirOpen();
      }
      else if( src == this.btnOutDirSelect ) {
	rv = true;
	doOutDirSelect();
      }
      else if( src == this.btnDiskFileUnpack ) {
	rv = true;
	doDiskFileUnpack();
      }
      else if( src == this.btnCopyLog ) {
	rv = true;
	EmuUtil.copyToClipboard( this, this.fldLog.getText() );
      }
      else if( src == this.btnHelp ) {
	rv = true;
	HelpFrm.openPage( HELP_PAGE );
      }
      else if( src == this.btnClose ) {
	rv = true;
	doClose();
      }
    }
    catch( Exception ex ) {
      EmuUtil.checkAndShowError( this, null, ex );
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = true;
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
    return rv;
  }


	/* --- Aktionen --- */

  private void doDiskFileOpen()
  {
    File file = FileUtil.showFileOpenDlg(
			this,
			LangUtil.getText( "disk.title.open_disk_image" ),
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_DU_IN ),
			FileUtil.getPlainDiskFileFilter(),
			FileUtil.getAnaDiskFileFilter(),
			FileUtil.getCopyQMFileFilter(),
			FileUtil.getDskFileFilter(),
			FileUtil.getImageDiskFileFilter(),
			FileUtil.getTeleDiskFileFilter() );
    if( file != null ) {
      if( openFile( file ) ) {
	RecentDirsMngr.setRecentDir( file, RecentDirsMngr.FILE_CAT_DU_IN );
      }
    }
  }


  private void doDiskFileRemove()
  {
    this.fldDiskFile.setFile( null );
    this.btnDiskFileRemove.setEnabled( false );
    this.fldRemark.setText( "" );
    this.fldLog.setText( "" );
    this.btnCopyLog.setEnabled( false );
    this.btnOutDirOpen.setEnabled( false );
    setUnpackEnabled( false );
  }


  private void doDiskFileUnpack()
  {
    if( (this.dirBytes != null) && !this.dataSectors.isEmpty() ) {
      try {

	// Ausgabeverzeichnis prufen und ggf. anlegen
	String outDirText = this.fldOutDir.getText();
	if( outDirText != null ) {
	  outDirText = outDirText.trim();
	  if( outDirText.isEmpty() ) {
	    outDirText = null;
	  }
	}
	if( outDirText == null ) {
	  throw new UserInputException(
				LangUtil.getText(
					"disk.error.output_directory_not_specified" ) );
	}
	this.outDir = new File( outDirText );
	if( this.outDir.exists() ) {
	  boolean  outDirEmpty = true;
	  String[] items       = this.outDir.list();
	  if( items != null ) {
	    for( String item : items ) {
	      if( !item.equals( "." ) && !item.equals( ".." ) ) {
		outDirEmpty = false;
		break;
	      }
	    }
	  }
	  if( !outDirEmpty ) {
	    if( !BaseDlg.showConfirmWarningDlg(
			this,
			LangUtil.getText(
				"disk.msg.output_directory_already" ),
			LangUtil.getText( "common.msg.warning" ) ) )
	    {
	      throw new UserCancelException();
	    }
	  }
	} else {
	  if( !this.outDir.mkdirs() ) {
	    throw new IOException(
			this.outDir.getPath() + ".\nDas Ausgabeverzeichnis"
				+ " konnte nicht angelegt werden." );
	  }
	}

	// ausgewaehlte Blockgroesse ermitteln
	this.blockSize = getSelectedBlockSize();
	if( this.blockSize <= 0 ) {
	  throw new UserInputException(
		LangUtil.getText( "disk.error.select_block_size" ) );
	}
	this.sectorSize = this.dataSectors.get( 0 ).getDataLength();
	if( this.sectorSize < 0x80 ) {
	  appendToLog(
		String.format(
			"Ung\u00FCltige Sektorgr\u00F6\u00DFe: %d Bytes\n",
			this.sectorSize ) );
	}
	this.sectorsPerBlock = this.blockSize / this.sectorSize;
	if( (this.sectorsPerBlock <= 0)
	    || ((this.sectorSize * this.sectorsPerBlock) != this.blockSize) )
	{
	  StringBuilder buf = new StringBuilder();
	  buf.append( "Die Blockgr\u00F6\u00DFe muss gleich oder ein"
		+ " Vielfaches der Sektorgr\u00F6\u00DFe sein.\n"
		+ "Die Sektorgr\u00F6\u00DFe betr\u00E4gt " );
	  if( (this.sectorSize % 1024) == 0 ) {
	    buf.append( this.sectorSize / 1024 );
	    buf.append( " kByte." );
	  } else {
	    buf.append( this.sectorSize );
	    buf.append( " Byte." );
	  }
	  throw new UserInputException( buf.toString() );
	}

	// eigentliches Entpacken
	this.blockNum16Bit     = this.rbBlockNum16Bit.isSelected();
	this.applyReadOnly     = this.cbApplyReadOnly.isSelected();
	this.forceLowerCase    = this.cbForceLowerCase.isSelected();
	this.unpackDeleted     = this.cbUnpackDeleted.isSelected();
	this.timeBytes         = null;
	this.blockSizeTooBig   = false;
	this.blockSizeTooSmall = false;
	this.fileTruncated     = false;
	this.fileCRCErr        = false;
	this.fileErr           = false;
	this.unpackErr         = false;
	this.fldLog.setText( "" );
	Main.setProperty(
		PROP_APPLY_READONLY,
		Boolean.toString( this.applyReadOnly ) );
	Main.setProperty(
		PROP_FORCE_LOWERCASE,
		Boolean.toString( this.forceLowerCase ) );
	Main.setProperty(
		PROP_UNPACK_DELETED,
		Boolean.toString( this.unpackDeleted ) );

	// Systemspuren entpacken
	if( (this.sysBytes != null) && (this.sysBytesLen > 0) ) {
	  int len = Math.min(
			this.sysBytes.length - this.sysBytesOffs,
			this.sysBytesLen );
	  if( len > 0 ) {
	    String fileName = SYS_TRACKS_FILENAME;
	    if( this.sysBytesCRCErr ) {
	      fileName += FILE_ERROR_SUFFIX;
	      appendToLog( fileName + ": Systemspuren enthalten"
				+ " mit CRC-Fehler gelesene Sektoren\n" );
	    } else {
	      appendToLog( "Systemspuren -> " + fileName + "\n" );
	    }
	    File file = new File( this.outDir, fileName );
	    try {
	      OutputStream out = null;
	      try {
		out = new FileOutputStream( file );
		out.write( this.sysBytes, this.sysBytesOffs, len );
		out.close();
		out = null;
	      }
	      finally {
		EmuUtil.closeSilently( out );
	      }
	    }
	    catch( IOException ex ) {
	      appendErrorToLog( ex );
	      this.fileErr   = true;
	      this.unpackErr = true;
	    }
	    if( this.fileErr && !this.sysBytesCRCErr ) {
	      fileName = SYS_TRACKS_FILENAME + FILE_ERROR_SUFFIX;
	      if( file.renameTo( new File( this.outDir, fileName ) ) ) {
		appendToLog( LangUtil.getText(
				"disk.text.file_renamed_blank_line",
				fileName ) );
	      }
	    }
	  }
	}

	// Dateien entpacken
	exportFiles();

	// Fertigmeldung
	if( this.unpackErr ) {
	  String errMsg = LangUtil.getText(
				"disk.text.errors_occurred_during" );
	  if( this.blockSizeTooBig || this.blockSizeTooSmall ) {
	    String sizeHint = null;
	    if( this.blockSizeTooBig && !this.blockSizeTooSmall ) {
	      sizeHint = LangUtil.getText(
		"disk.text.selected_block_size_probably_large" );
	    } else if( !this.blockSizeTooBig && this.blockSizeTooSmall ) {
	      sizeHint = LangUtil.getText(
		"disk.text.selected_block_size_probably_small" );
	    } else {
	      sizeHint = LangUtil.getText(
		"disk.text.selected_block_size_probably_wrong" );
	    }
	    errMsg = errMsg + sizeHint;
	  }
	  appendToLog( "\n" + errMsg + "\n" );
	  throw new IOException( errMsg );
	} else {
	  appendToLog( LangUtil.getText( "disk.msg.done" ) );
	}
	this.btnOutDirOpen.setEnabled( true );
      }
      catch( UserCancelException ex ) {}
      catch( Exception ex ) {
	EmuUtil.checkAndShowError( this, null, ex );
      }
    }
  }


  private void doOutDirOpen() throws IOException
  {
    if( this.outDir != null ) {
      if( !DesktopHelper.isOpenSupported() ) {
	EmuUtil.throwNotSupportedByJRE();
      }
      DesktopHelper.open( this.outDir );
    }
  }


  private void doOutDirSelect()
  {
    File   preSelection = null;
    String outDirText   = this.fldOutDir.getText();
    if( outDirText != null ) {
      outDirText = outDirText.trim();
      if( !outDirText.isEmpty() ) {
	preSelection = new File( outDirText );
      }
    }
    File outDir = DirSelectDlg.selectDirectory( this, preSelection );
    if( outDir != null ) {
      this.fldOutDir.setText( outDir.getPath() );
    }
  }


	/* --- Konstruktor --- */

  private DiskImgUnpackFrm()
  {
    setTitle( LangUtil.getText( TITLE ) );
    this.dataAreaTruncated = false;
    this.dataSectors       = new ArrayList<>();
    this.dirBytes          = null;
    this.sysBytes          = null;
    this.timeBytes         = null;
    this.outDir            = null;
    this.applyReadOnly     = false;
    this.forceLowerCase    = false;
    this.unpackDeleted     = false;
    this.blockNum16Bit     = false;
    this.blockSizeTooBig   = false;
    this.blockSizeTooSmall = false;
    this.fileTruncated     = false;
    this.fileCRCErr        = false;
    this.fileErr           = false;
    this.unpackErr         = false;
    this.sysBytesCRCErr    = false;
    this.sysBytesLen       = 0;
    this.sysBytesOffs      = 0;
    this.diskSides         = 0;
    this.blockSize         = 0;
    this.sectorSize        = 0;
    this.sectorsPerBlock   = 0;


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

    // Diskettenabbilddatei
    JPanel panelDiskFile = GUIFactory.createPanel( new GridBagLayout() );
    panelDiskFile.setBorder(
		GUIFactory.createTitledBorder(
			LangUtil.getText( "disk.section.disk_image_file" ) ) );
    add( panelDiskFile, gbc );

    GridBagConstraints gbcDiskFile = new GridBagConstraints(
					0, 0,
					1, 1,
					0.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.NONE,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );

    panelDiskFile.add(
		GUIFactory.createLabel(
			LangUtil.getText( EmuUtil.LABEL_FILE ) ),
		gbcDiskFile );

    this.fldDiskFile          = new FileNameFld();
    gbcDiskFile.insets.left   = 0;
    gbcDiskFile.insets.bottom = 0;
    gbcDiskFile.fill          = GridBagConstraints.HORIZONTAL;
    gbcDiskFile.weightx       = 1.0;
    gbcDiskFile.gridx++;
    panelDiskFile.add( this.fldDiskFile, gbcDiskFile );

    this.btnDiskFileOpen = GUIFactory.createRelImageResourceButton(
				this,
				"file/open.png",
				LangUtil.getText(
					"disk.action.select_disk_image" ) );
    gbcDiskFile.fill    = GridBagConstraints.NONE;
    gbcDiskFile.weightx = 0.0;
    gbcDiskFile.gridx++;
    panelDiskFile.add( this.btnDiskFileOpen, gbcDiskFile );

    this.btnDiskFileRemove = GUIFactory.createRelImageResourceButton(
				this,
				"file/delete.png",
				LangUtil.getText(
					"disk.action.remove_disk_image" ) );
    this.btnDiskFileRemove.setEnabled( false );
    gbcDiskFile.gridx++;
    panelDiskFile.add( this.btnDiskFileRemove, gbcDiskFile );

    gbcDiskFile.insets.left   = 5;
    gbcDiskFile.insets.bottom = 5;
    gbcDiskFile.gridx         = 0;
    gbcDiskFile.gridy++;
    panelDiskFile.add(
		GUIFactory.createLabel(
			LangUtil.getText( "common.label.comment" ) ),
		gbcDiskFile );

    this.fldRemark = GUIFactory.createTextField();
    this.fldRemark.setEditable( false );
    gbcDiskFile.insets.left = 0;
    gbcDiskFile.fill        = GridBagConstraints.HORIZONTAL;
    gbcDiskFile.weightx     = 1.0;
    gbcDiskFile.gridwidth   = GridBagConstraints.REMAINDER;
    gbcDiskFile.gridx++;
    panelDiskFile.add( this.fldRemark, gbcDiskFile );


    // Entpacken
    JPanel panelUnpack = GUIFactory.createPanel( new GridBagLayout() );
    panelUnpack.setBorder( GUIFactory.createTitledBorder(
		LangUtil.getText( "disk.action.unpack" ) ) );
    gbc.gridy++;
    add( panelUnpack, gbc );

    GridBagConstraints gbcUnpack = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					1.0, 0.0,
					GridBagConstraints.WEST,
					GridBagConstraints.HORIZONTAL,
					new Insets( 5, 5, 0, 5 ),
					0, 0 );


    // Zielverzeichnis
    JPanel panelOutDir = GUIFactory.createPanel();
    panelOutDir.setLayout( new BoxLayout( panelOutDir, BoxLayout.X_AXIS ) );
    panelUnpack.add( panelOutDir, gbcUnpack );

    this.labelOutDir = GUIFactory.createLabel(
		LangUtil.getText( "common.title.unpack" ) );
    panelOutDir.add( this.labelOutDir );
    panelOutDir.add( Box.createHorizontalStrut( 5 ) );

    this.fldOutDir = GUIFactory.createTextField();
    panelOutDir.add( this.fldOutDir );
    panelOutDir.add( Box.createHorizontalStrut( 5 ) );

    Dimension prefSize = this.fldOutDir.getPreferredSize();
    if( prefSize != null ) {
      this.fldOutDir.setMaximumSize(
		new Dimension( Integer.MAX_VALUE, prefSize.height ) );
    }

    this.btnOutDirSelect = GUIFactory.createRelImageResourceButton(
				this,
				"file/open.png",
				LangUtil.getText( "disk.action.select_target_directory" ) );
    panelOutDir.add( this.btnOutDirSelect );


    // Blockgroesse, Blocknummerngroesse und Optionen
    this.labelBlockSize = GUIFactory.createLabel(
					LangUtil.getText( "disk.label.block_size_kbyte" ) );
    gbcUnpack.fill      = GridBagConstraints.NONE;
    gbcUnpack.weightx   = 0.0;
    gbcUnpack.gridwidth = 1;
    gbcUnpack.gridy++;
    panelUnpack.add( this.labelBlockSize, gbcUnpack );

    this.labelBlockNumSize = GUIFactory.createLabel(
		LangUtil.getText( "disk.label.block_numbers" ) );
    gbcUnpack.gridy++;
    panelUnpack.add( this.labelBlockNumSize, gbcUnpack );

    this.comboBlockSize = GUIFactory.createComboBox();
    this.comboBlockSize.setEditable( false );
    this.comboBlockSize.addItem( "--- Bitte ausw\u00E4hlen ---" );
    int blockSize = 1024;
    for( int i = 0; i < 5; i++ ) {
      this.comboBlockSize.addItem( blockSize / 1024 );
      blockSize *= 2;
    }
    gbcUnpack.gridwidth = 2;
    gbcUnpack.gridx++;
    --gbcUnpack.gridy;
    panelUnpack.add( this.comboBlockSize, gbcUnpack );

    ButtonGroup grpBlockNumSize = new ButtonGroup();

    this.rbBlockNum8Bit = GUIFactory.createRadioButton(
		LangUtil.getText( "common.option.8_bit" ) );
    grpBlockNumSize.add( this.rbBlockNum8Bit );
    gbcUnpack.gridwidth = 1;
    gbcUnpack.gridy++;
    panelUnpack.add( this.rbBlockNum8Bit, gbcUnpack );

    this.rbBlockNum16Bit = GUIFactory.createRadioButton(
		LangUtil.getText( "common.option.16_bit" ), true );
    grpBlockNumSize.add( this.rbBlockNum16Bit );
    gbcUnpack.gridx++;
    panelUnpack.add( this.rbBlockNum16Bit, gbcUnpack );

    this.infoBlockSize = GUIFactory.createLabel();
    gbcUnpack.gridx++;
    --gbcUnpack.gridy;
    panelUnpack.add( this.infoBlockSize, gbcUnpack );

    this.infoBlockNumSize = GUIFactory.createLabel();
    gbcUnpack.gridy++;
    panelUnpack.add( this.infoBlockNumSize, gbcUnpack );

    this.cbUnpackDeleted = GUIFactory.createCheckBox(
	LangUtil.getText( "disk.option.unpack_deleted_files" ),
	Main.getBooleanProperty(
			PROP_UNPACK_DELETED,
			DEFAULT_UNPACK_DELETED ) );
    gbcUnpack.gridwidth    = GridBagConstraints.REMAINDER;
    gbcUnpack.gridx        = 0;
    gbcUnpack.gridy++;
    panelUnpack.add( this.cbUnpackDeleted, gbcUnpack );

    this.cbForceLowerCase = GUIFactory.createCheckBox(
				LangUtil.getText(
					"common.option.write_file_names" ),
				Main.getBooleanProperty(
						PROP_FORCE_LOWERCASE,
						DEFAULT_FORCE_LOWERCASE ) );
    gbcUnpack.insets.top = 0;
    gbcUnpack.gridy++;
    panelUnpack.add( this.cbForceLowerCase, gbcUnpack );

    this.cbApplyReadOnly = GUIFactory.createCheckBox(
				LangUtil.getText(
					"disk.option.apply_read_only" ),
				Main.getBooleanProperty(
						PROP_APPLY_READONLY,
						DEFAULT_APPLY_READONLY ) );
    gbcUnpack.insets.bottom = 0;
    gbcUnpack.gridy++;
    panelUnpack.add( this.cbApplyReadOnly, gbcUnpack );


    // Protokoll
    JPanel panelLog = GUIFactory.createPanel( new BorderLayout() );
    panelLog.setBorder( GUIFactory.createTitledBorder(
		LangUtil.getText( "disk.section.log" ) ) );
    gbc.fill    = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    gbc.gridy++;
    add( panelLog, gbc );

    this.fldLog = GUIFactory.createTextArea( 8, 1 );
    this.fldLog.setEditable( false );
    panelLog.add(
		GUIFactory.createScrollPane( this.fldLog ),
		BorderLayout.CENTER );


    // Schaltflaechen
    JPanel panelBtn = GUIFactory.createPanel( new GridLayout( 1, 5, 5, 5 ) );
    panelBtn.setBorder( GUIFactory.createEmptyBorder( 0, 5, 5, 5 ) );
    gbc.anchor      = GridBagConstraints.CENTER;
    gbc.fill        = GridBagConstraints.NONE;
    gbc.weightx     = 0.0;
    gbc.weighty     = 0.0;
    gbc.gridy++;
    add( panelBtn, gbc );

    this.btnDiskFileUnpack = GUIFactory.createButton(
		LangUtil.getText( "disk.action.unpack" ) );
    panelBtn.add( this.btnDiskFileUnpack );

    this.btnOutDirOpen = GUIFactory.createButton(
					LangUtil.getText( "disk.action.open_directory" ) );
    this.btnOutDirOpen.setEnabled( false );
    panelBtn.add( this.btnOutDirOpen );

    this.btnCopyLog = GUIFactory.createButton(
		LangUtil.getText( "disk.action.copy_log" ) );
    this.btnCopyLog.setEnabled( false );
    panelBtn.add( this.btnCopyLog );

    this.btnHelp = GUIFactory.createButton(
		LangUtil.getText( "common.menu.help" ) );
    panelBtn.add( this.btnHelp );

    this.btnClose = GUIFactory.createButton(
		LangUtil.getText( "common.action.close" ) );
    panelBtn.add( this.btnClose );


    // Fenstergroesse
    setResizable( true );
    if( !applySettings( Main.getProperties() ) ) {
      pack();
      setScreenCentered();
    }


    // Listener
    this.btnDiskFileOpen.addActionListener( this );
    this.btnDiskFileRemove.addActionListener( this );
    this.btnDiskFileUnpack.addActionListener( this );
    this.btnOutDirOpen.addActionListener( this );
    this.btnOutDirSelect.addActionListener( this );
    this.btnClose.addActionListener( this );
    this.btnHelp.addActionListener( this );
    this.btnCopyLog.addActionListener( this );


    // sonstiges
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    resetPrefLogSize();
		  }
		} );
    setUnpackEnabled( false );
    (new DropTarget( this.fldDiskFile, this )).setActive( true );
    (new DropTarget( this.fldRemark, this )).setActive( true );
  }


	/* --- private Methoden --- */

  private void appendErrorToLog( Exception ex )
  {
    String msg = ex.getMessage();
    if( msg != null ) {
      if( msg.isEmpty() ) {
	msg = null;
      }
    }
    if( msg == null ) {
      msg = ex.getClass().getName();
    }
    StringBuilder buf = new StringBuilder( 128 );
    buf.append( "  Fehler: " );
    buf.append( msg );
    buf.append( "\n\n" );
    appendToLog( buf.toString() );
  }


  private void appendToLog( final String msg )
  {
    if( this.fldLog != null ) {
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


  private boolean checkUniqueBlockNums( int entryPos )
  {
    boolean      rv        = true;
    Set<Integer> blockNums = new HashSet<>();
    int          pos       = entryPos + 16;
    if( this.blockNum16Bit ) {
      for( int i = 0; i < 8; i++ ) {
	int blockNum = EmuUtil.getWord( this.dirBytes, pos );
	if( blockNum != 0 ) {
	  blockNums.add( blockNum );
	}
	pos +=2;
      }
      int basePos = 0;
      while( rv && ((basePos + 31) < this.dirBytes.length) ) {
	if( (basePos != entryPos)
	    && CPMDirUtil.isValidFilledDirEntry(
					this.dirBytes,
					basePos,
					true,
					null,
					null ) )
	{
	  pos = basePos + 16;
	  for( int i = 0; i < 8; i++ ) {
	    if( blockNums.contains(
			EmuUtil.getWord( this.dirBytes, pos ) ) )
	    {
	      rv = false;
	      break;
	    }
	    pos +=2;
	  }
	}
	basePos += 32;
      }
    } else {
      for( int i = 0; i < 16; i++ ) {
	int blockNum = (int) this.dirBytes[ pos++ ] & 0xFF;
	if( blockNum != 0 ) {
	  blockNums.add( blockNum );
	}
      }
      int basePos = 0;
      while( rv && ((basePos + 31) < this.dirBytes.length) ) {
	if( (basePos != entryPos)
	    && CPMDirUtil.isValidFilledDirEntry(
					this.dirBytes,
					basePos,
					true,
					null,
					null ) )
	{
	  pos = basePos + 16;
	  for( int i = 0; i < 16; i++ ) {
	    if( blockNums.contains( (int) this.dirBytes[ pos++ ] & 0xFF ) ) {
	      rv = false;
	      break;
	    }
	  }
	}
	basePos += 32;
      }
    }
    return rv;
  }


  /*
   * Exportieren eines phyisischen Extents
   *
   * Rueckgabe: true wenn nach dem naechsten Extents gesucht werdeb soll
   */
  private boolean exportExtent(
			OutputStream  out,
			int           extentPos,
			int           preExtentNum ) throws IOException
  {
    boolean rv       = false;
    int     nRecords = (int) this.dirBytes[ extentPos + 15 ] & 0xFF;
    if( (nRecords == 0x80)
	|| (this.blockNum16Bit
			&& (this.blockSize == 1024)
			&& (nRecords == 0x40)) )
    {
      rv = true;
    }
    boolean status    = true;
    int     nRemain   = nRecords * 0x80;
    int     extentNum = CPMDirUtil.getExtentNumByEntryPos(
							dirBytes,
							extentPos );
    int additionalExtents = extentNum;
    if( preExtentNum >= 0 ) {
      additionalExtents = extentNum - preExtentNum - 1;
      if( additionalExtents < 0 ) {
	appendToLog( LangUtil.getText( "disk.msg.order_physical_extents" ) );
	this.fileTruncated = true;
	this.fileErr       = true;
	this.unpackErr     = true;
	status             = false;
	rv                 = false;
      }
    }
    nRemain += (additionalExtents * 16 * 1024);
    if( status ) {
      int pos = extentPos + 16;
      while( pos < (extentPos + 32) ) {
	int blockNum = 0;
	if( this.blockNum16Bit ) {
	  blockNum = EmuUtil.getWord( this.dirBytes, pos );
	  pos += 2;
	} else {
	  blockNum = (int) this.dirBytes[ pos++ ] & 0xFF;
	}
	if( blockNum == 0 ) {
	  status = false;
	  rv     = false;
	  break;
	}
	int sectorIdx = blockNum * this.sectorsPerBlock;
	for( int i = 0; status && (i < this.sectorsPerBlock); i++ ) {
	  if( sectorIdx >= this.dataSectors.size() ) {
	    StringBuilder buf = new StringBuilder();
	    if( this.dataAreaTruncated ) {
	      buf.append( LangUtil.getText(
			"disk.text.block_lies_outside_data_area_area",
			blockNum ) );
	    } else {
	      this.blockSizeTooBig = true;
	      buf.append( LangUtil.getText(
			"disk.text.block_lies_outside_data_area",
			blockNum ) );
	    }
	    appendToLog( buf.toString() );
	    this.fileTruncated = true;
	    this.unpackErr     = true;
	    status             = false;
	    rv                 = false;
	    break;
	  }
	  SectorData sector = this.dataSectors.get( sectorIdx++ );
	  if( !this.fileCRCErr && sector.checkError() ) {
	    appendToLog( LangUtil.getText( "disk.msg.sectors_read_crc" ) );
	    this.fileCRCErr = true;
	    this.fileErr    = true;
	  }
	  nRemain -= sector.writeTo(
				out,
				Math.min( this.sectorSize, nRemain ) );
	  if( nRemain <= 0 ) {
	    break;
	  }
	}
      }
      if( (nRemain != 0) && (nRecords != 0) ) {
	if( !this.fileTruncated ) {
	  appendToLog(
		String.format(
			"  Extent %d: Anzahl Bytes und Anzahl Bl\u00F6cke"
				+ " passen nicht zusammen.\n",
			extentNum ) );
	  if( nRemain < 0 ) {
	    this.blockSizeTooBig = true;
	  } else if( !this.dataAreaTruncated && (nRemain > 0) ) {
	    this.blockSizeTooSmall = true;
	  }
	  this.fileErr   = true;
	  this.unpackErr = true;
	}
	rv = false;
      }
    }
    return rv;
  }


  private void exportFile(
			OutputStream  out,
			int           extentPos,
			boolean       deleted ) throws IOException
  {
    boolean processing   = true;
    int     preExtentNum = -1;
    while( processing && ((extentPos + 31) < this.dirBytes.length) ) {
      if( deleted && !checkUniqueBlockNums( extentPos ) ) {
	appendToLog( LangUtil.getText( "disk.msg.file_cannot_unpacked" ) );
	this.fileTruncated = true;
	processing         = false;
      } else {
	if( !exportExtent( out, extentPos, preExtentNum ) ) {
	  processing = false;
	  break;
	}
	preExtentNum = CPMDirUtil.getExtentNumByEntryPos(
							dirBytes,
							extentPos );
	extentPos += 32;
      }
    }
  }


  private void exportFiles()
  {
    Set<String>   fileKeys           = new HashSet<>();
    Set<String>   fileNames          = new HashSet<>();
    StringBuilder fileNameBuf        = new StringBuilder();
    AtomicInteger userNumBuf         = new AtomicInteger();
    int           nInvalidDirEntries = 0;
    int           entryPos           = 0;
    while( (entryPos + 31) < this.dirBytes.length ) {
      fileNameBuf.setLength( 0 );
      if( CPMDirUtil.isValidFilledDirEntry(
					this.dirBytes,
					entryPos,
					true,
					userNumBuf,
					fileNameBuf ) )
      {
	String fileName = fileNameBuf.toString();
	if( fileName.isEmpty() ) {
	  appendToLog(
		String.format(
			"Ung\u00FCltiger Directory-Eintrag %d ignoriert\n",
			entryPos / 32 ) );
	} else {
	  int     userNum = userNumBuf.get();
	  boolean deleted = (userNum == 0xE5);
	  String  fileKey = fileName;
	  if( deleted ) {
	    fileKey = String.format( "Gel\u00F6scht: %s", fileName );
	  } else if( userNum > 0 ) {
	    fileKey = String.format(
				"%d: %s",
				userNum,
				fileName );
	  }
	  if( fileKeys.add( fileKey ) ) {

	    // auf DateStamper-Datei pruefen
	    if( (entryPos == 0)
		&& fileName.equalsIgnoreCase( DateStamper.FILENAME ) )
	    {
	      try {
		ByteArrayOutputStream timeBuf = new ByteArrayOutputStream();
		exportFile( timeBuf, entryPos, false );
		this.timeBytes = timeBuf.toByteArray();
	      }
	      catch( IOException ex ) {}
	    }

	    // Log-Ausschrift vorbereiten
	    StringBuilder logBuf  = new StringBuilder();
	    logBuf.append( fileKey );
	    if( this.forceLowerCase ) {
	      fileName = fileName.toLowerCase();
	    }

	    // ggf. Verzeichnis anlegen
	    String  subDir = null;
	    boolean status = true;
	    if( deleted ) {
	      if( this.unpackDeleted && !fileNames.contains( fileName ) ) {
		if( checkUniqueBlockNums( entryPos ) ) {
		  subDir = "deleted";
		} else {
		  logBuf.append( "\n  Datei kann nicht entpackt werden,\n"
			+ "  da der Datenbereich bereits von anderen"
			+ " Dateien verwendet wird.\n" );
		  appendToLog( logBuf.toString() );
		  status = false;
		}
	      } else {
		status = false;
	      }
	    } else if( userNum > 0 ) {
	      subDir = String.valueOf( userNum );
	    }
	    File fileDir = this.outDir;
	    if( status && (subDir != null) ) {
	      fileDir = new File( this.outDir, subDir );
	      fileDir.mkdirs();
	      if( !fileDir.exists() ) {
		appendToLog(
		      "\n  Verzeichnis \'" + fileDir.getPath()
			      + "\' konnte nicht angelegt werden.\n" );
		this.unpackErr = true;
		status         = false;
	      }
	    }

	    // Dateiname
	    fileNames.add( fileName );
	    if( status ) {
	      boolean renamed = false;
	      if( (fileName.indexOf( '/' ) >= 0)
		  || (fileName.indexOf( '\\' ) >= 0) )
	      {
		fileName = fileName.replace( '/', '_' ).replace( '\\', '_' );
		renamed  = true;
	      }
	      if( renamed || this.forceLowerCase || (subDir != null) ) {
		logBuf.append( " -> " );
		if( subDir != null ) {
		  logBuf.append( subDir );
		  logBuf.append( File.separatorChar );
		}
		logBuf.append( fileName );
	      }
	      logBuf.append( '\n' );

	      File file = new File( fileDir, fileName );
	      if( status ) {
		appendToLog( logBuf.toString() );

		// Datei exportieren
		this.fileTruncated = false;
		this.fileCRCErr    = false;
		this.fileErr       = false;
		OutputStream out   = null;
		try {
		  out = new BufferedOutputStream(
					new FileOutputStream( file ) );
		  exportFile( out, entryPos, deleted );
		  out.close();
		  out = null;
		}
		catch( IOException ex ) {
		  appendErrorToLog( ex );
		  this.fileErr   = true;
		  this.unpackErr = true;
		}
		finally {
		  EmuUtil.closeSilently( out );
		}

		// Zeitstempel setzen
		if( this.timeBytes != null ) {
		  int pos = entryPos / 2;
		  if( (pos + 15) < timeBytes.length ) {
		    FileTimesData.createOf( file ).setTimesInMillis(
			DateStamper.getMillis( this.timeBytes, pos ),
			DateStamper.getMillis( this.timeBytes, pos + 5 ),
			DateStamper.getMillis( this.timeBytes, pos + 10 ) );
		  }
		}

		// ggf. Datei umbenennen
		if( this.fileTruncated || this.fileErr ) {
		  String fName2 = fileName;
		  if( this.fileTruncated ) {
		    fName2 += FILE_TRUNCATED_SUFFIX;
		  }
		  if( this.fileErr ) {
		    fName2 += FILE_ERROR_SUFFIX;
		  }
		  File dirFile = file.getParentFile();
		  File newFile = (dirFile != null ?
					new File( dirFile, fName2 )
					: new File( fName2 ));
		  if( file.renameTo( newFile ) ) {
		    appendToLog( LangUtil.getText(
				"disk.text.file_renamed",
				fName2 ) );
		  }
		}

		// ggf. Schreibschutzattribut setzen
		if( this.applyReadOnly
		    && (((int) this.dirBytes[ entryPos + 9 ] & 0x80) != 0) )
		{
		  FileUtil.setFileWritable( file, false );
		}
	      }
	    }
	  }
	}
      }
      entryPos += 32;
    }
  }


  private void fireOpenFile( final Object o )
  {
    if( o != null ) {
      if( o instanceof File ) {
	EventQueue.invokeLater(
			new Runnable()
			{
			  @Override
			  public void run()
			  {
			    openFile( (File) o );
			  }
			} );
      }
    }
  }


  private int getSelectedBlockSize()
  {
    int    blockSize = -1;
    Object item      = this.comboBlockSize.getSelectedItem();
    if( item != null ) {
      if( item instanceof Integer ) {
	blockSize = ((Integer) item).intValue() * 1024;
      }
    }
    return blockSize;
  }


  private boolean openFile( File file )
  {
    boolean rv = false;
    if( file != null ) {
      try {
	AbstractFloppyDisk disk = null;
	try {
	  byte[] plainDiskBytes = null;

	  // Datei lesen
	  if( file.length() > Integer.MAX_VALUE ) {
	    throw new IOException(
			LangUtil.getText( "common.error.file_large" ) );
	  }
	  String fName = file.getName();
	  if( fName != null ) {
	    fName = fName.toLowerCase();
	    if( TextUtil.endsWith( fName, DiskUtil.anaDiskFileExt )
		|| TextUtil.endsWith( fName, DiskUtil.gzAnaDiskFileExt ) )
	    {
	      disk = AnaDisk.readFile( this, file );
	    }
	  }
	  if( disk == null ) {
	    byte[] header = FileUtil.readFile( file, true, 0x100 );
	    if( header != null ) {
	      if( CopyQMDisk.isCopyQMFileHeader( header ) ) {
		disk = CopyQMDisk.readFile( this, file );
	      }
	      else if( CPCDisk.isCPCDiskFileHeader( header ) ) {
		disk = CPCDisk.readFile( this, file );
	      }
	      else if( ImageDisk.isImageDiskFileHeader( header ) ) {
		disk = ImageDisk.readFile( this, file );
	      }
	      else if( TeleDisk.isTeleDiskFileHeader( header ) ) {
		disk = TeleDisk.readFile( this, file, true );
	      }
	    }
	  }
	  if( disk != null ) {
	    if( !DiskUtil.checkAndConfirmWarning( this, disk ) ) {
	      throw new UserCancelException();
	    }
	  } else if( TextUtil.endsWith( fName, DiskUtil.plainDiskFileExt )
		     || TextUtil.endsWith(
				fName,
				DiskUtil.gzPlainDiskFileExt ) )
	  {
	    plainDiskBytes = Files.readAllBytes( file.toPath() );
	  }

	  // Infos zur Abbilddatei ausgeben
	  String remark = null;
	  if( disk != null ) {
	    remark = disk.getRemark();
	  }
	  this.fldDiskFile.setFile( file );
	  this.btnDiskFileRemove.setEnabled( true );
	  EmuUtil.setText( this.fldRemark, remark );
	  this.timeBytes         = null;
	  this.dirBytes          = null;
	  this.sysBytes          = null;
	  this.sysBytesCRCErr    = false;
	  this.sysBytesLen       = 0;
	  this.sysBytesOffs      = 0;
	  this.diskSides         = 0;
	  this.blockSize         = 0;
	  this.blockSizeTooBig   = false;
	  this.blockSizeTooSmall = false;
	  this.fileTruncated     = false;
	  this.fileErr           = false;
	  this.unpackErr         = false;
	  this.dataAreaTruncated = false;
	  this.dataSectors.clear();

	  // Initialwerte
	  try {
	    this.comboBlockSize.setSelectedIndex( 0 );
	  }
	  catch( IllegalArgumentException ex ) {}
	  this.rbBlockNum16Bit.setSelected( true );
	  this.infoBlockSize.setText( "" );
	  this.infoBlockNumSize.setText( "" );

	  // Verzeichnis suchen
	  String  outDirText      = "";
	  String  logText         = "";
	  String  dataTruncReason = null;
	  byte[]  dirBytes        = null;
	  boolean dirFound        = false;
	  if( plainDiskBytes != null ) {
	    AtomicInteger sysOffs = new AtomicInteger( 0 );
	    AtomicInteger dirOffs = new AtomicInteger( 0 );
	    rv                    = true;
	    dirBytes              = CPMDirUtil.findAndReadDirBytes(
							plainDiskBytes,
							sysOffs,
							dirOffs,
							this.dataSectors );
	    this.diskSides    = 1;
	    this.sysBytes     = plainDiskBytes;
	    this.sysBytesOffs = sysOffs.get();
	    this.sysBytesLen  = dirOffs.get() - this.sysBytesOffs;
	  } else if( disk != null ) {
	    StringBuilder errBuf = new StringBuilder();
	    rv                   = true;
	    dirBytes             = CPMDirUtil.findAndReadDirBytes(
							disk,
							this.dataSectors,
							errBuf );
	    if( errBuf.length() > 0 ) {
	      this.dataAreaTruncated = true;
	      dataTruncReason = errBuf.toString();
	    }
	    this.diskSides = disk.getSides();

	    // Systemspuren extrahieren
	    if( !this.dataSectors.isEmpty() ) {
	      int endCyl = this.dataSectors.get( 0 ).getCylinder();
	      if( endCyl > 0 ) {
		ByteArrayOutputStream sysBuf = new ByteArrayOutputStream();
		try {
		  for( int cyl = 0; cyl < endCyl; cyl++ ) {
		    for( int head = 0; head < this.diskSides; head++ ) {
		      for( SectorData sector : disk.getSortedTrackSectors(
							      cyl, head ) )
		      {
			if( sector.checkError() ) {
			  this.sysBytesCRCErr = true;
			}
			sector.writeTo( sysBuf, sector.getDataLength() );
		      }
		    }
		  }
		}
		catch( IOException ex ) {}
		this.sysBytes     = sysBuf.toByteArray();
		this.sysBytesLen  = this.sysBytes.length;
		this.sysBytesOffs = 0;
	      }
	    }
	  }
	  if( dirBytes != null ) {
	    StringBuilder buf = new StringBuilder();
	    if( this.sysBytesOffs > 0 ) {
	      buf.append( LangUtil.getText(
			"disk.text.hard_disk_image_file_byte",
			this.sysBytesOffs ) );
	    }
	    if( dirBytes.length > 0 ) {
	      java.util.List<FileEntry> entries = CPMDirUtil.extractDir(
								dirBytes,
								true );
	      if( !entries.isEmpty() ) {
		if( dataTruncReason != null ) {
		  buf.append( LangUtil.getText(
			"disk.text.irregularity_found",
			dataTruncReason ) );
		}
		buf.append( "Directory:\n" );
		for( FileEntry entry : entries ) {
		  String fileName = entry.getName();
		  if( fileName != null ) {
		    if( !fileName.isEmpty() ) {
		      buf.append( "  " );
		      buf.append( fileName );
		      buf.append( '\n' );
		      dirFound = true;
		    }
		  }
		}
	      }
	    }
	    if( dirFound ) {
	      this.dirBytes = dirBytes;
	      logText       = buf.toString();

	      // Blockgroesse und Blocknummernformat ermitteln
	      AtomicInteger blockNumSize    = new AtomicInteger();
	      AtomicInteger blockSize       = new AtomicInteger();
	      AtomicBoolean blockSizeUnique = new AtomicBoolean();
	      if( !CPMDirUtil.recognizeBlockNumFmt(
						dirBytes,
						blockNumSize,
						blockSize,
						blockSizeUnique ) )
	      {
		blockNumSize.set( 0 );
		blockSize.set( 0 );
	      }
	      Color  color = COLOR_EMPHASIZED;
	      String text  = LangUtil.getText(
				"disk.text.block_size_not_detected" );
	      if( blockSize.get() > 0 ) {
		String sizeText = null;
		if( (blockSize.get() % 1024) == 0 ) {
		  sizeText = LangUtil.getText( "disk.text.kbyte",
				blockSize.get() / 1024 );
		} else {
		  sizeText = LangUtil.getText(
				"disk.text.bytes", blockSize.get() );
		}
		if( blockSizeUnique.get() ) {
		  color = COLOR_RECOGNIZED;
		  text  = LangUtil.getText( "disk.text.detected", sizeText );
		} else {
		  text = LangUtil.getText( "disk.text.probably_not_uniquely",
				sizeText );
		}
		this.comboBlockSize.setSelectedItem(
					blockSize.get() / 1024 );
	      }
	      this.infoBlockSize.setForeground( color );
	      this.infoBlockSize.setText( text );

	      if( blockNumSize.get() == 8 ) {
		this.rbBlockNum8Bit.setSelected( true );
		this.infoBlockNumSize.setForeground( COLOR_RECOGNIZED );
		this.infoBlockNumSize.setText(
			LangUtil.getText( "disk.text.8_bit_detected" ) );
	      } else if( blockNumSize.get() == 16 ) {
		this.rbBlockNum16Bit.setSelected( true );
		this.infoBlockNumSize.setForeground( COLOR_RECOGNIZED );
		this.infoBlockNumSize.setText(
			LangUtil.getText( "disk.text.16_bit_detected" ) );
	      } else {
		this.rbBlockNum16Bit.setSelected( true );
		this.infoBlockNumSize.setForeground( COLOR_EMPHASIZED );
		this.infoBlockNumSize.setText(
			LangUtil.getText( "disk.text.not_detected" ) );
	      }

	      // Ausgabeverzeichnis vorbelegen
	      File outDir = RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_DU_OUT );
	      if( outDir == null ) {
		outDir = file.getParentFile();
	      }
	      String subDirText = "unpacked";
	      if( fName != null ) {
		int idx = fName.lastIndexOf( '.' );
		if( idx > 0 ) {
		  subDirText = fName.substring( 0, idx );
		} else {
		  subDirText = fName;
		}
	      }
	      if( outDir != null ) {
		outDirText = (new File( outDir, subDirText )).getPath();
	      } else {
		outDirText = subDirText;
	      }
	    } else {
	      logText = "Es gibt nichts zu entpacken,\n"
			+ "da das Directory leer ist.";
	    }
	  } else {
	    if( (disk != null) || (plainDiskBytes != null) ) {
	      logText = "Datei kann nicht entpackt werden,\n"
			+ "da kein CP/M-kompatibles Directory"
			+ " gefunden wurde.";
	    } else {
	      logText = "Unbekanntes Dateiformat";
	    }
	  }
	  this.outDir = null;
	  this.fldOutDir.setText( outDirText );
	  EmuUtil.setText( this.fldLog, logText );
	  this.btnCopyLog.setEnabled(
			(logText != null) && !logText.isEmpty() );
	  this.btnOutDirOpen.setEnabled( false );
	  setUnpackEnabled( dirFound );
	}
	finally {
	  if( disk != null ) {
	    disk.closeSilently();
	  }
	}
      }
      catch( UserCancelException ex ) {}
      catch( Exception ex ) {
	rv = false;
	EmuUtil.checkAndShowError( this, null, ex );
      }
    }
    return rv;
  }


  private void resetPrefLogSize()
  {
    this.fldLog.setColumns( 0 );
    this.fldLog.setRows( 0 );
  }


  private void setUnpackEnabled( boolean state )
  {
    this.labelOutDir.setEnabled( state );
    this.fldOutDir.setEnabled( state );
    this.btnOutDirSelect.setEnabled( state );
    this.labelBlockSize.setEnabled( state );
    this.comboBlockSize.setEnabled( state );
    this.infoBlockSize.setEnabled( state );
    this.labelBlockNumSize.setEnabled( state );
    this.rbBlockNum8Bit.setEnabled( state );
    this.rbBlockNum16Bit.setEnabled( state );
    this.infoBlockNumSize.setEnabled( state );
    this.btnDiskFileUnpack.setEnabled( state );
  }
}
