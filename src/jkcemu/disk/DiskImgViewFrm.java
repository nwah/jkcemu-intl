/*
 * (c) 2016-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Inspektor fuer Diskettenabbilddateien
 */

package jkcemu.disk;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import jkcemu.Main;
import jkcemu.base.BaseDlg;
import jkcemu.base.BaseFrm;
import jkcemu.base.ByteDataSource;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;
import jkcemu.base.HelpFrm;
import jkcemu.base.HexCharFld;
import jkcemu.base.ReplyBytesDlg;
import jkcemu.file.FileEntry;
import jkcemu.file.FileUtil;
import jkcemu.file.RecentDirsMngr;
import jkcemu.file.RecentFilesMngr;
import jkcemu.lang.LangUtil;


public class DiskImgViewFrm extends BaseFrm
			implements
				ByteDataSource,
				CaretListener,
				DropTargetListener,
				HyperlinkListener,
				RecentFilesMngr.Listener
{
  public static final String TITLE = Main.APPNAME
					+ "Diskettenabbilddatei-Inspektor";

  private static final String HELP_PAGE     = "/help/disk/diskimgviewer.htm";
  private static final String MARK_BEG      = "<font color=\"red\">";
  private static final String MARK_END      = "</font>";
  private static final String MARK_BOGUS_ID = "r?";
  private static final String MARK_NO_DATA  = MARK_BEG + "no_data" + MARK_END;
  private static final String MARK_DELETED  = MARK_BEG + "del" + MARK_END;
  private static final String MARK_ERROR    = MARK_BEG + "err" + MARK_END;

  private static final String PROP_SPLIT_POS = "split.position";


  private static class NotFoundException extends Exception
  {
    private NotFoundException() {}
  };


  private static final int PREF_SECTORLIST_W = 300;
  private static final int PREF_SECTORDATA_W = 300;
  private static final int PREF_SECTORDATA_H = 200;

  private static final String HTML_ACTION_KEY_PREFIX  = "sector:";
  private static final String HTML_ACTION_HREF_PREFIX
				= "href=" + HTML_ACTION_KEY_PREFIX;

  private static final String NOT_RECOGNIZED = "nicht erkannt";

  private static DiskImgViewFrm instance = null;

  private AbstractFloppyDisk        disk;
  private File                      file;
  private String                    exportPrefix;
  private SectorData                selectedSector;
  private RecentFilesMngr           recentFilesMngr;
  private boolean                   lastFound;
  private boolean                   lastBigEndian;
  private ReplyBytesDlg.InputFormat lastInputFmt;
  private String                    lastFindText;
  private byte[]                    findBytes;
  private int                       findCyl;
  private int                       findDataPos;
  private int                       findHead;
  private int                       findWraps;
  private int                       findSectorIdx;
  private SectorData                findSectorData;
  private JMenuItem                 mnuOpen;
  private JMenuItem                 mnuExportTracks;
  private JMenuItem                 mnuClose;
  private JMenuItem                 mnuBytesCopyAscii;
  private JMenuItem                 mnuBytesCopyHex;
  private JMenuItem                 mnuBytesCopyDump;
  private JMenuItem                 mnuFind;
  private JMenuItem                 mnuFindPrev;
  private JMenuItem                 mnuFindNext;
  private JMenuItem                 mnuHelpContent;
  private JTabbedPane               tabbedPane;
  private JSplitPane                splitPane;
  private JEditorPane               fldFileContent;
  private JTextField                fldFileName;
  private JTextField                fldFileEtc;
  private JTextField                fldPhysFormat;
  private JTextField                fldRemark;
  private JTextField                fldTimestamp;
  private JTextField                fldCPMSys;
  private JTextField                fldBlockSize;
  private JTextField                fldBlockNumFmt;
  private JTextField                fldSectorPos;
  private JTextField                fldSectorID;
  private JTextField                fldSectorEtc;
  private JList<String>             listCPMDir;
  private HexCharFld                fldSectorData;
  private JButton                   btnSectorCopy;
  private JButton                   btnSectorExport;
  private JScrollPane               spSectorData;
  private JLabel                    sectorDataInfo;


  public static DiskImgViewFrm open()
  {
    if( instance == null ) {
      instance = new DiskImgViewFrm();
    }
    EmuUtil.showFrame( instance );
    return instance;
  }


  public static DiskImgViewFrm open( File file )
  {
    open();
    if( file != null ) {
      instance.openFile( file );
    }
    return instance;
  }


	/* --- ByteDataSource --- */

  @Override
  public int getAddrOffset()
  {
    return 0;
  }


  @Override
  public int getDataByte( int addr )
  {
    SectorData sector = this.selectedSector;
    return sector != null ? sector.getDataByte( addr ) : 0;
  }


  @Override
  public int getDataLength()
  {
    SectorData sector = this.selectedSector;
    return sector != null ? sector.getDataLength() : 0;
  }


  @Override
  public boolean getDataReadOnly()
  {
    return true;
  }


  @Override
  public boolean setDataByte( int addr, int value )
  {
    return false;
  }


	/* --- CaretListener --- */

  @Override
  public void caretUpdate( CaretEvent e )
  {
    int     pos   = this.fldSectorData.getCaretPosition();
    boolean state = ((pos >= 0) && (pos < getDataLength()));
    this.mnuBytesCopyAscii.setEnabled( state );
    this.mnuBytesCopyHex.setEnabled( state );
    this.mnuBytesCopyDump.setEnabled( state );
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


  @Override
  public void dropActionChanged( DropTargetDragEvent e )
  {
    // leer
  }


	/* --- HyperlinkListener --- */

  @Override
  public void hyperlinkUpdate( HyperlinkEvent e )
  {
    if( (this.disk != null)
	&& (e.getSource() == this.fldFileContent)
	&& e.getEventType().equals( HyperlinkEvent.EventType.ACTIVATED ) )
    {
      javax.swing.text.Element elem = e.getSourceElement();
      if( elem != null ) {
	javax.swing.text.AttributeSet atts = elem.getAttributes();
	if( atts != null ) {
	  Object a = atts.getAttribute( javax.swing.text.html.HTML.Tag.A );
	  if( a != null ) {
	    String text = a.toString();
	    if( text != null ) {
	      if( text.startsWith( HTML_ACTION_HREF_PREFIX ) ) {
		text = text.substring( HTML_ACTION_HREF_PREFIX.length() );
		try {
		  String[] items = text.split( ":" );
		  if( items != null ) {
		    if( items.length == 3 ) {
		      showSector(
				Integer.parseInt( items[ 0 ].trim() ),
				Integer.parseInt( items[ 1 ].trim() ),
				Integer.parseInt( items[ 2 ].trim() ) );
		    }
		  }
		}
		catch( NumberFormatException ex ) {}
		catch( PatternSyntaxException ex ) {}
	      }
	    }
	  }
	}
      }
    }
  }


	/* --- RecentFilesMngr.Listener --- */

  @Override
  public void recentFileActionPerformed( String fileName )
  {
    openFile( new File( fileName ) );
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  public boolean applySettings( Properties props )
  {
    boolean rv   = super.applySettings( props );
    int splitPos = EmuUtil.getIntProperty(
				props,
				getSettingsPrefix() + PROP_SPLIT_POS,
				-1 );
    if( splitPos >= 0 ) {
      this.splitPane.setDividerLocation( splitPos );
    }
    return rv;
  }


  @Override
  public boolean doAction( EventObject e )
  {
    boolean rv  = false;
    Object  src = e.getSource();
    if( src != null ) {
      if( src == this.mnuOpen ) {
	rv = true;
	doFileOpen();
      } else if( src == this.mnuExportTracks ) {
	rv = true;
	TrackExportDlg.exportTracks( this, this.disk, this.exportPrefix );
      } else if( src == this.mnuClose ) {
	rv = true;
	doClose();
      } else if( src == this.mnuBytesCopyAscii ) {
        rv = true;
        this.fldSectorData.copySelectedBytesAsAscii();
      } else if( src == this.mnuBytesCopyHex ) {
        rv = true;
        this.fldSectorData.copySelectedBytesAsHex();
      } else if( src == this.mnuBytesCopyDump ) {
        rv = true;
        this.fldSectorData.copySelectedBytesAsDump();
      } else if( src == this.mnuFind ) {
        rv = true;
        doFind();
      } else if( src == this.mnuFindPrev ) {
        rv = true;
        doFindNext( true );
      } else if( src == this.mnuFindNext ) {
        rv = true;
        doFindNext( false );
      } else if( src == this.mnuHelpContent ) {
	rv = true;
	HelpFrm.openPage( HELP_PAGE );
      } else if( src == this.btnSectorCopy ) {
	rv = true;
	doSectorCopy();
      } else if( src == this.btnSectorExport ) {
	rv = true;
	doSectorExport();
      }
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = false;
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
      // damit beim erneuten Oeffnen das Eingabefeld leer ist
      if( this.disk != null ) {
	this.disk.closeSilently();
      }
      this.disk = null;
      this.file = null;
      this.mnuExportTracks.setEnabled( false );
      this.mnuFind.setEnabled( false );
      this.fldFileName.setText( "" );
      this.fldPhysFormat.setText( "" );
      this.fldRemark.setText( "" );
      this.fldTimestamp.setText( "" );
      this.fldFileEtc.setText( "" );
      this.fldCPMSys.setText( "" );
      this.fldBlockSize.setText( "" );
      this.fldBlockNumFmt.setText( "" );
      this.fldFileContent.setContentType( "text/plain" );
      this.fldFileContent.setText( "" );
      clearSectorDetails();
    }
    return rv;
  }


  @Override
  public void putSettingsTo( Properties props )
  {
    if( props != null ) {
      super.putSettingsTo( props );
      props.setProperty(
		getSettingsPrefix() + PROP_SPLIT_POS,
		String.valueOf( this.splitPane.getDividerLocation() ) );
    }
  }


	/* --- Konstruktor --- */

  private DiskImgViewFrm()
  {
    setTitle( TITLE );
    this.disk            = null;
    this.file            = null;
    this.exportPrefix    = null;
    this.selectedSector  = null;
    this.recentFilesMngr = RecentFilesMngr.getLazyInstance(
				this,
				RecentFilesMngr.App.DISK_VIEWER );

    // Menu Datei
    JMenu mnuFile = createMenuFile();

    this.mnuOpen = createMenuItemWithStandardAccelerator(
						EmuUtil.TEXT_OPEN_OPEN,
						KeyEvent.VK_O );
    mnuFile.add( this.mnuOpen );
    if( this.recentFilesMngr != null ) {
      mnuFile.add( this.recentFilesMngr.getMenu() );
    }
    mnuFile.addSeparator();

    this.mnuExportTracks = createMenuItem( "Spuren exportieren..." );
    this.mnuExportTracks.setEnabled( false );
    mnuFile.add( this.mnuExportTracks );
    mnuFile.addSeparator();

    this.mnuClose = createMenuItemClose();
    mnuFile.add( this.mnuClose );


    // Menu Bearbeiten
    JMenu mnuEdit = createMenuEdit();

    this.mnuBytesCopyHex = createMenuItem(
		"Ausgw\u00E4hlte Bytes als Hexadezimalzahlen kopieren" );
    this.mnuBytesCopyHex.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyHex );

    this.mnuBytesCopyAscii = createMenuItem(
		"Ausgw\u00E4hlte Bytes als ASCII-Text kopieren" );
    this.mnuBytesCopyAscii.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyAscii );

    this.mnuBytesCopyDump = createMenuItem(
		"Ausgw\u00E4hlte Bytes als Hex-ASCII-Dump kopieren" );
    this.mnuBytesCopyDump.setEnabled( false );
    mnuEdit.add( this.mnuBytesCopyDump );
    mnuEdit.addSeparator();

    this.mnuFind = createMenuItemOpenFind( true );
    this.mnuFind.setEnabled( false );
    mnuEdit.add( this.mnuFind );

    this.mnuFindNext = createMenuItemFindNext( true );
    this.mnuFindNext.setEnabled( false );
    mnuEdit.add( this.mnuFindNext );

    this.mnuFindPrev = createMenuItemFindPrev( true );
    this.mnuFindPrev.setEnabled( false );
    mnuEdit.add( this.mnuFindPrev );


    // Menu Hilfe
    JMenu mnuHelp       = createMenuHelp(); 
    this.mnuHelpContent = createMenuItem(
			"Hilfe zum Diskettenabbilddatei-Inspektor..." );
    mnuHelp.add( this.mnuHelpContent );


    // Menu
    setJMenuBar( GUIFactory.createMenuBar( mnuFile, mnuEdit, mnuHelp ) );


    // Fensterinhalt
    setLayout( new BorderLayout() );

    this.tabbedPane = GUIFactory.createTabbedPane();
    add( this.tabbedPane, BorderLayout.CENTER );

    JPanel panelPhys = new JPanel( new BorderLayout() );
    this.tabbedPane.addTab( "Physische Struktur", panelPhys );

    this.fldFileContent = GUIFactory.createEditorPane();
    this.fldFileContent.setEditable( false );
    this.fldFileContent.setPreferredSize(
				new Dimension( PREF_SECTORDATA_W, 1 ) );

    JPanel panelDetails = GUIFactory.createPanel( new GridBagLayout() );

    this.splitPane = GUIFactory.createSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			false,
			GUIFactory.createScrollPane( this.fldFileContent ),
			panelDetails );
    panelPhys.add( this.splitPane, BorderLayout.CENTER );


    // linke Seite Detailansicht
    GridBagConstraints gbcDetails = new GridBagConstraints(
						0, 0,
						1, 1,
						1.0, 0.0,
						GridBagConstraints.NORTHWEST,
						GridBagConstraints.HORIZONTAL,
						new Insets( 5, 5, 5, 5 ),
						0, 0 );

    // Bereich Datei
    JPanel panelFile = GUIFactory.createPanel( new GridBagLayout() );
    panelFile.setBorder( GUIFactory.createTitledBorder( "Datei" ) );
    panelDetails.add( panelFile, gbcDetails );

    GridBagConstraints gbcFile = new GridBagConstraints(
						0, 0,
						1, 1,
						0.0, 0.0,
						GridBagConstraints.WEST,
						GridBagConstraints.NONE,
						new Insets( 5, 5, 0, 5 ),
						0, 0 );

    panelFile.add( GUIFactory.createLabel( "Dateiname:" ), gbcFile );
    gbcFile.gridy++;
    panelFile.add( GUIFactory.createLabel( "Format:" ), gbcFile );
    gbcFile.gridy++;
    panelFile.add( GUIFactory.createLabel( "Bemerkung:" ), gbcFile );
    gbcFile.insets.bottom = 5;
    gbcFile.gridy++;
    panelFile.add( GUIFactory.createLabel( "Zeitstempel:" ), gbcFile );
    gbcFile.gridy++;
    panelFile.add( GUIFactory.createLabel( "Weitere Infos:" ), gbcFile );

    this.fldFileName = GUIFactory.createTextField();
    this.fldFileName.setEditable( false );
    gbcFile.fill          = GridBagConstraints.HORIZONTAL;
    gbcFile.weightx       = 1.0;
    gbcFile.insets.bottom = 0;
    gbcFile.gridy         = 0;
    gbcFile.gridx++;
    panelFile.add( this.fldFileName, gbcFile );

    this.fldPhysFormat = GUIFactory.createTextField();
    this.fldPhysFormat.setEditable( false );
    gbcFile.gridy++;
    panelFile.add( this.fldPhysFormat, gbcFile );

    this.fldRemark = GUIFactory.createTextField();
    this.fldRemark.setEditable( false );
    gbcFile.gridy++;
    panelFile.add( this.fldRemark, gbcFile );

    this.fldTimestamp = GUIFactory.createTextField();
    this.fldTimestamp.setEditable( false );
    gbcFile.gridy++;
    panelFile.add( this.fldTimestamp, gbcFile );

    this.fldFileEtc = GUIFactory.createTextField();
    this.fldFileEtc.setEditable( false );
    gbcFile.insets.bottom = 5;
    gbcFile.gridy++;
    panelFile.add( this.fldFileEtc, gbcFile );

    // Bereich Sektor
    JPanel panelSector = GUIFactory.createPanel( new GridBagLayout() );
    panelSector.setBorder( GUIFactory.createTitledBorder( "Sektor" ) );
    gbcDetails.fill    = GridBagConstraints.BOTH;
    gbcDetails.weighty = 1.0;
    gbcDetails.gridy++;
    panelDetails.add( panelSector, gbcDetails );

    GridBagConstraints gbcSector = new GridBagConstraints(
						0, 0,
						1, 1,
						0.0, 0.0,
						GridBagConstraints.WEST,
						GridBagConstraints.NONE,
						new Insets( 5, 5, 0, 5 ),
						0, 0 );

    panelSector.add(
		GUIFactory.createLabel( "Sektorposition:" ),
		gbcSector );
    gbcSector.gridy++;
    panelSector.add( GUIFactory.createLabel( "Sektor-ID:" ), gbcSector );
    gbcSector.insets.bottom = 5;
    gbcSector.gridy++;
    panelSector.add( GUIFactory.createLabel( "Weitere Infos:" ), gbcSector );

    this.fldSectorPos = GUIFactory.createTextField();
    this.fldSectorPos.setEditable( false );
    gbcSector.fill          = GridBagConstraints.HORIZONTAL;
    gbcSector.weightx       = 1.0;
    gbcSector.insets.bottom = 0;
    gbcSector.gridy         = 0;
    gbcSector.gridx++;
    panelSector.add( this.fldSectorPos, gbcSector );

    this.fldSectorID = GUIFactory.createTextField();
    this.fldSectorID.setEditable( false );
    gbcSector.gridy++;
    panelSector.add( this.fldSectorID, gbcSector );

    this.fldSectorEtc = GUIFactory.createTextField();
    this.fldSectorEtc.setEditable( false );
    gbcSector.insets.bottom = 5;
    gbcSector.gridy++;
    panelSector.add( this.fldSectorEtc, gbcSector );

    this.sectorDataInfo = GUIFactory.createLabel(
		"Bitte in der linken Ansicht einen Sektor anklicken" );
    this.sectorDataInfo.setHorizontalAlignment( SwingConstants.CENTER );
    this.sectorDataInfo.setVerticalAlignment( SwingConstants.CENTER );
    this.fldSectorData = new HexCharFld( this );
    GUIFactory.initFont( this.fldSectorData );

    JPanel sectorDataPlaceholder = GUIFactory.createPanel();
    sectorDataPlaceholder.setPreferredSize(
		new Dimension( PREF_SECTORDATA_W, PREF_SECTORDATA_H ) );
    this.spSectorData   = GUIFactory.createScrollPane(
					sectorDataPlaceholder );
    gbcSector.fill      = GridBagConstraints.BOTH;
    gbcSector.weighty   = 1.0;
    gbcSector.gridwidth = GridBagConstraints.REMAINDER;
    gbcSector.gridx     = 0;
    gbcSector.gridy++;
    panelSector.add( this.spSectorData, gbcSector );

    JPanel panelSectorBtns = GUIFactory.createPanel(
					new GridLayout( 1, 2, 5, 5 ) );
    gbcSector.anchor       = GridBagConstraints.CENTER;
    gbcSector.fill         = GridBagConstraints.NONE;
    gbcSector.weightx      = 0.0;
    gbcSector.weighty      = 0.0;
    gbcSector.gridy++;
    panelSector.add( panelSectorBtns, gbcSector );

    this.btnSectorCopy = GUIFactory.createButton( "Sektordaten kopieren" );
    this.btnSectorCopy.setEnabled( false );
    panelSectorBtns.add( this.btnSectorCopy );

    this.btnSectorExport = GUIFactory.createButton(
					"Sektordaten exportieren..." );
    this.btnSectorExport.setEnabled( false );
    panelSectorBtns.add( this.btnSectorExport );


    // Bereich automatische CP/M-Diskettenformaterkannung
    JPanel panelRecognizedFmt = GUIFactory.createPanel(
						new GridBagLayout() );
    panelRecognizedFmt.setBorder(
	GUIFactory.createTitledBorder(
			"Automatische CP/M-Diskettenformaterkennung" ) );
    this.tabbedPane.addTab( "CP/M-Format", panelRecognizedFmt );

    GridBagConstraints gbcRecognizedFmt = new GridBagConstraints(
						0, 0,
						1, 1,
						0.0, 0.0,
						GridBagConstraints.WEST,
						GridBagConstraints.NONE,
						new Insets( 5, 5, 0, 5 ),
						0, 0 );

    panelRecognizedFmt.add(
		GUIFactory.createLabel( "Systemspuren / Directory:" ),
		gbcRecognizedFmt );
    gbcRecognizedFmt.gridy++;
    panelRecognizedFmt.add(
		GUIFactory.createLabel( "Blockgr\u00F6\u00DFe:" ),
		gbcRecognizedFmt );
    gbcRecognizedFmt.insets.bottom = 5;
    gbcRecognizedFmt.gridy++;
    panelRecognizedFmt.add(
		GUIFactory.createLabel( "Blocknummernformat:" ),
		gbcRecognizedFmt );

    this.fldCPMSys = GUIFactory.createTextField();
    this.fldCPMSys.setEditable( false );
    gbcRecognizedFmt.fill          = GridBagConstraints.HORIZONTAL;
    gbcRecognizedFmt.weightx       = 1.0;
    gbcRecognizedFmt.insets.bottom = 0;
    gbcRecognizedFmt.gridy         = 0;
    gbcRecognizedFmt.gridx++;
    panelRecognizedFmt.add( this.fldCPMSys, gbcRecognizedFmt );

    this.fldBlockSize = GUIFactory.createTextField();
    this.fldBlockSize.setEditable( false );
    gbcRecognizedFmt.gridy++;
    panelRecognizedFmt.add( this.fldBlockSize, gbcRecognizedFmt );

    this.fldBlockNumFmt = GUIFactory.createTextField();
    this.fldBlockNumFmt.setEditable( false );
    gbcRecognizedFmt.insets.bottom = 5;
    gbcRecognizedFmt.gridy++;
    panelRecognizedFmt.add( this.fldBlockNumFmt, gbcRecognizedFmt );

    JPanel panelCPMDir = GUIFactory.createPanel( new BorderLayout() );
    panelCPMDir.setBorder( GUIFactory.createTitledBorder( "Directory" ) );
    gbcRecognizedFmt.fill      = GridBagConstraints.BOTH;
    gbcRecognizedFmt.weighty   = 1.0;
    gbcRecognizedFmt.gridwidth = GridBagConstraints.REMAINDER;
    gbcRecognizedFmt.gridx     = 0;
    gbcRecognizedFmt.gridy++;
    panelRecognizedFmt.add( panelCPMDir, gbcRecognizedFmt );

    this.listCPMDir = GUIFactory.createList();
    this.listCPMDir.setLayoutOrientation( JList.VERTICAL_WRAP );
    this.listCPMDir.setDragEnabled( false );
    this.listCPMDir.setPrototypeCellValue( "WWWWWWWW.WWW" );
    this.listCPMDir.setVisibleRowCount( 0 );
    panelCPMDir.add(
		GUIFactory.createScrollPane( this.listCPMDir ),
		BorderLayout.CENTER );


    // Listener
    this.fldFileContent.addHyperlinkListener( this );
    this.fldSectorData.addCaretListener( this );
    this.btnSectorCopy.addActionListener( this );
    this.btnSectorExport.addActionListener( this );


    // Drag&Drop aktivieren
    (new DropTarget( this.fldFileContent, this )).setActive( true );
    (new DropTarget( this.listCPMDir, this )).setActive( true );


    // sonstiges
    resetFind();
    setResizable( true );
    if( !applySettings( Main.getProperties() ) ) {
      pack();
      setLocationByPlatform( true );
    }
    this.fldFileContent.setPreferredSize( null );
  }


	/* --- Aktionen --- */

  private void doFileOpen()
  {
    if( openFile( FileUtil.showFileOpenDlg(
			this,
			"Diskettenabbilddatei \u00F6ffnen",
			RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_DV_DISK ),
			FileUtil.getPlainDiskFileFilter(),
			FileUtil.getAnaDiskFileFilter(),
			FileUtil.getCopyQMFileFilter(),
			FileUtil.getDskFileFilter(),
			FileUtil.getImageDiskFileFilter(),
			FileUtil.getTeleDiskFileFilter() ) ) )
    {
      setRecentFile( file, RecentDirsMngr.FILE_CAT_DV_DISK );
    }
  }


  private void doFind()
  {
    if( this.disk != null ) {
      ReplyBytesDlg dlg = new ReplyBytesDlg(
					this,
					"Bytes suchen",
					this.lastInputFmt,
					this.lastBigEndian,
					this.lastFindText );
      dlg.setVisible( true );
      byte[] findBytes = dlg.getApprovedBytes();
      if( findBytes != null ) {
	if( findBytes.length > 0 ) {
	  resetFind();
	  this.findBytes     = findBytes;
	  this.lastInputFmt  = dlg.getApprovedInputFormat();
	  this.lastBigEndian = dlg.getApprovedBigEndian();
	  this.lastFindText  = dlg.getApprovedText();
	  doFindNext( false );
	  this.mnuFindNext.setEnabled( true );
	  this.mnuFindPrev.setEnabled( true );
	}
      }
    }
  }


  private void doFindNext( boolean backwards )
  {
    if( (this.disk != null)
	&& (this.findBytes != null)
	&& (this.findBytes.length > 0) )
    {
      try {
	if( this.lastFound ) {
	  this.findWraps = 2;
	  if( backwards ) {
	    decFindDataPos();
	  } else {
	    incFindDataPos();
	  }
	}
	this.findWraps = 2;
	for(;;) {
	  SectorData sector = getFindSector();
	  if( sector != null ) {
	    if( backwards ) {
	      for( int i = this.findDataPos; i >= 0; --i ) {
		if( equalsFindBytesAt( sector, i ) ) {
		  this.findDataPos = i;
		  showFoundBytes();
		  return;		// Suche beenden
		}
	      }
	      decFindDataPos();		// weitersuchen
	    } else {
	      int dataLen = sector.getDataLength();
	      for( int i = this.findDataPos; i < dataLen; i++ ) {
		if( equalsFindBytesAt( sector, i ) ) {
		  this.findDataPos = i;
		  showFoundBytes();
		  return;		// Suche beenden
		}
	      }
	      incFindDataPos();		// weitersuchen
	    }
	  } else {
	    // ab dem naechsten Sektor weitersuchen
	    if( backwards ) {
	      decFindSectorIdx();
	    } else {
	      incFindSectorIdx();
	    }
	  }
	}
      }
      catch( NotFoundException ex ) {
	if( this.disk != null ) {
	  BaseDlg.showInfoDlg( this, "Byte-Folge nicht gefunden" );
	}
      }
    }
  }


  private void doSectorCopy()
  {
    SectorData sector = this.selectedSector;
    if( sector != null ) {
      int len = sector.getDataLength();
      if( len > 0 ) {
	StringBuilder buf = new StringBuilder( len * 3 );
	for( int i = 0; i < len; i++ ) {
	  if( i > 0 ) {
	    buf.append( (i % 16) == 0 ? '\n' : '\u0020' );
	  }
	  buf.append( String.format( "%02X", sector.getDataByte( i ) ) );
	}
	buf.append( '\n' );
	EmuUtil.copyToClipboard( this, buf.toString() );
      }
    }
  }


  private void doSectorExport()
  {
    SectorData sector = this.selectedSector;
    if( sector != null ) {
      int len = sector.getDataLength();
      if( len > 0 ) {
	File dirFile = RecentDirsMngr.getRecentDir(
					RecentDirsMngr.FILE_CAT_DV_SECTOR );
	if( (dirFile == null) && (this.file != null) ) {
	  dirFile = this.file.getParentFile();
	}
	String fName = String.format(
				"%ssector_%d_%d_%d_%d.bin",
				this.exportPrefix != null ?
					this.exportPrefix
					: "",
				sector.getCylinder(),
				sector.getHead(),
				sector.getSectorNum(),
				sector.getSizeCode() );
	File file = FileUtil.showFileSaveDlg(
				this,
				"Sektordaten exportieren",
				dirFile != null ? 
					new File( dirFile, fName )
					: new File( fName ),
				FileUtil.getBinaryFileFilter() );
	if( file != null ) {
	  try {
	    OutputStream out = null;
	    try {
	      out = new FileOutputStream( file );
	      sector.writeTo( out, -1 );
	      out.close();
	      out = null;
	      setRecentFile( file, RecentDirsMngr.FILE_CAT_DV_SECTOR );
	    }
	    finally {
	      EmuUtil.closeSilently( out );
	    }
	  }
	  catch( IOException ex ) {
	    BaseDlg.showErrorDlg( this, ex );
	  }
	}
      }
    }
  }


	/* --- private Methoden --- */

  private static void appendModeTextTo(
			StringBuilder                    buf,
			AbstractFloppyDisk.RecordingMode recMode,
			AbstractFloppyDisk.TransferRate  transferRate,
			boolean                          transferRateUnit,
			AbstractFloppyDisk.Density       density )
  {
    int oldLen = buf.length();
    if( recMode != null ) {
      switch( recMode ) {
	case FM:
	  buf.append( "FM" );
	  break;
	case MFM:
	  buf.append( "MFM" );
	  break;
      }
    }
    if( transferRate != null ) {
      String s = null;
      switch( transferRate ) {
	case KBPS_250:
	  s = "250";
	  break;
	case KBPS_300:
	  s = "300";
	  break;
	case KBPS_500:
	  s = "500";
	  break;
      }
      if( s != null ) {
	if( buf.length() > oldLen ) {
	  buf.append( '\u0020' );
	}
	buf.append( s );
	if( transferRateUnit ) {
	  buf.append( " kbps" );
	}
      }
    }
    if( density != null ) {
      String s = null;
      switch( density ) {
	case SD_DD:
	  s = "SD/DD";
	  break;
	case HD:
	  s = "HD";
	  break;
	case ED:
	  s = "ED";
	  break;
      }
      if( s != null ) {
	if( buf.length() > oldLen ) {
	  buf.append( ", " );
	}
	buf.append( s );
      }
    }
  }


  private void clearSectorDetails()
  {
    this.selectedSector = null;
    this.fldSectorPos.setText( "" );
    this.fldSectorID.setText( "" );
    this.fldSectorEtc.setText( "" );
    this.btnSectorCopy.setEnabled( false );
    this.btnSectorExport.setEnabled( false );
    if( this.disk != null ) {
      setSectorDataView( this.sectorDataInfo );
    } else {
      setSectorDataView( null );
    }
  }


  private void decFindDataPos() throws NotFoundException
  {
    SectorData sector = getFindSector();
    if( sector != null ) {
      --this.findDataPos;
      if( this.findDataPos < 0 ) {
	decFindSectorIdx();
      }
    } else {
      decFindSectorIdx();
    }
  }


  private void decFindSectorIdx() throws NotFoundException
  {
    if( this.disk == null ) {
      throw new NotFoundException();
    }
    this.findDataPos    = 0;
    this.findSectorData = null;
    if( this.findSectorIdx > 0 ) {
      --this.findSectorIdx;
    } else {
      for(;;) {
	if( this.findHead > 0 ) {
	  --this.findHead;
	} else {
	  --this.findCyl;
	  if( this.findCyl < 0 ) {
	    this.findCyl = getFindCyls() - 1;
	    --this.findWraps;
	    if( (this.findCyl < 0) || (this.findWraps <= 0) ) {
	      this.findCyl = 0;
	      throw new NotFoundException();
	    }
	  }
	  int sides = getFindSides();
	  if( sides < 1 ) {
	    throw new NotFoundException();
	  }
	  this.findHead = sides - 1;
	}
	int n = this.disk.getTrackSectorCount( this.findCyl, this.findHead );
	if( n > 0 ) {
	  this.findSectorIdx = n - 1;
	  break;
	}
      }
    }
    SectorData sector = getFindSector();
    if( sector != null ) {
      int dataLen = sector.getDataLength();
      if( dataLen > 0 ) {
	this.findDataPos = dataLen - 1;
      }
    }
  }


  private boolean equalsFindBytesAt( SectorData sector, int pos )
  {
    int dataLen = sector.getDataLength();
    for( int i = 0; i < this.findBytes.length; i++ ) {
      int p = pos + i;
      if( p >= dataLen ) {
	return false;
      }
      if( (byte) sector.getDataByte( p ) != this.findBytes[ i ] ) {
	return false;
      }
    }
    return true;
  }


  private void incFindDataPos() throws NotFoundException
  {
    SectorData sector = getFindSector();
    if( sector != null ) {
      this.findDataPos++;
      if( this.findDataPos >= sector.getDataLength() ) {
	incFindSectorIdx();
      }
    } else {
      incFindSectorIdx();
    }
  }


  private void incFindSectorIdx() throws NotFoundException
  {
    this.findDataPos    = 0;
    this.findSectorData = null;
    this.findSectorIdx++;
    if( this.findSectorIdx >= getFindSectorsOfTrack() ) {
      this.findSectorIdx = 0;
      this.findHead++;
      if( this.findHead >= getFindSides() ) {
	this.findHead = 0;
	this.findCyl++;
	if( this.findCyl >= getFindCyls() ) {
	  this.findCyl = 0;
	  --this.findWraps;
	  if( this.findWraps <= 0 ) {
	    throw new NotFoundException();
	  }
	}
      }
    }
  }


  private int getFindCyls() throws NotFoundException
  {
    if( this.disk == null ) {
      throw new NotFoundException();
    }
    return this.disk.getCylinders();
  }


  private SectorData getFindSector() throws NotFoundException
  {
    if( this.disk == null ) {
      throw new NotFoundException();
    }
    if( this.findSectorData == null ) {
      this.findSectorData = this.disk.getSectorByIndex(
						this.findCyl,
						this.findHead,
						this.findSectorIdx );
    }
    return this.findSectorData;
  }


  private int getFindSectorsOfTrack() throws NotFoundException
  {
    if( this.disk == null ) {
      throw new NotFoundException();
    }
    return this.disk.getTrackSectorCount( this.findCyl, this.findHead );
  }


  private int getFindSides() throws NotFoundException
  {
    if( this.disk == null ) {
      throw new NotFoundException();
    }
    return this.disk.getSides();
  }


  private boolean openFile( File file )
  {
    boolean rv = false;
    if( file != null ) {
      try {
	AbstractFloppyDisk disk = DiskUtil.readDiskFile( this, file, true );
	if( disk != null ) {
	  AbstractFloppyDisk disk2 = null;
	  if( disk.isRepaired() ) {
	    String msg = disk.getWarningText();
	    if( msg == null ) {
	      msg = "Die Datei enth\u00E4lt mysteri\u00F6se Daten, die "
			+ Main.APPNAME + " beim Laden repariert hat.";
	    }
	    msg = msg + "\nDie Datei selbst wurde durch die Reparatur"
			+ " nicht ge\u00E4ndert.\n"
			+ "M\u00F6chten Sie den reparierten oder"
			+ " den originalen Dateiinhalt sehen?\n";
	    switch( BaseDlg.showOptionDlg(
				this,
				msg,
				"Dateireparatur",
				"Reparierter Inhalt",
				"Originaler Inhalt",
				EmuUtil.TEXT_CANCEL ) )
	    {
	      case 0:
		// leer
		break;
	      case 1:
		disk = DiskUtil.readDiskFile( this, file, false );
		break;
	      default:
		disk = null;
	    }
	  }
	}
	if( disk != null ) {

	  // alte Abbilddatei schliessen und neue uebernehmen
	  if( this.disk != null ) {
	    this.disk.closeSilently();
	  }
	  this.disk = disk;
	  this.file = file;
	  setTitle( LangUtil.tr( TITLE ) + ": " + file.getPath() );

	  // Allgemeine Infos
	  EmuUtil.setText( this.fldFileName, file.getName() );
	  EmuUtil.setText( this.fldRemark, disk.getRemark() );
	  java.util.Date diskDate = disk.getDiskDate();
	  if( diskDate != null ) {
	    EmuUtil.setText(
			this.fldTimestamp,
			DateFormat.getDateTimeInstance(
				DateFormat.MEDIUM,
				DateFormat.MEDIUM ).format( diskDate ) );
	  }

	  // Diskettenformat
	  StringBuilder buf        = new StringBuilder( 0x100 );
	  String        formatText = disk.getFormatText();
	  AtomicInteger occurence  = new AtomicInteger( 0 );
	  AtomicInteger totalCount = new AtomicInteger( 0 );
	  int           sectorSize = disk.getMostCommonSectorSize(
							occurence,
							totalCount );
	  if( sectorSize > 0 ) {
	    if( !disk.isFormatTextWithSectorSize() ) {
	      buf.append( formatText );
	      if( occurence.intValue() < totalCount.intValue() ) {
		buf.append( LangUtil.tr(
			", h\u00E4ufigste Sektorgr\u00F6\u00DFe: {0} Byte",
			sectorSize ) );
	      } else {
		buf.append( LangUtil.tr(
			", Sektorgr\u00F6\u00DFe: {0} Byte",
			sectorSize ) );
	      }
	      formatText = buf.toString();
	    }
	  } else {
	    sectorSize = -1;
	  }
	  EmuUtil.setText( this.fldPhysFormat, formatText );

	  // Automatische CP/M-Formaterkennung
	  String cpmSysText    = "Kein CP/M-kompatibles Directory gefunden";
	  String blockNumText  = NOT_RECOGNIZED;
	  String blockSizeText = NOT_RECOGNIZED;
	  String[] cpmDirItems = new String[ 0 ];

	  java.util.List<SectorData> dataSectors = new ArrayList<>();
	  byte[] cpmDirBytes = CPMDirUtil.findAndReadDirBytes(
							disk,
							dataSectors,
							null );
	  if( cpmDirBytes != null ) {
	    if( !dataSectors.isEmpty() ) {
	      int sysTracks = dataSectors.get( 0 ).getCylinder();
	      if( sysTracks == 1 ) {
		cpmSysText = "1 Systemspur";
	      } else if( sysTracks > 1 ) {
		cpmSysText = String.format( "%d Systemspuren", sysTracks );
	      } else {
		cpmSysText = "keine Systemspuren";
	      }
	    }
	    if( cpmDirBytes != null ) {
	      java.util.List<FileEntry> entries = CPMDirUtil.extractDir(
								cpmDirBytes,
								true );
	      if( entries != null ) {
		int n = entries.size();
		if( n > 0 ) {
		  cpmDirItems = new String[ n ];
		  for( int i = 0; i < n; i++ ) {
		    String s = entries.get( i ).getName();
		    cpmDirItems[ i ] = (s != null ? s : "");
		  }
		}
	      }
	    }
	    AtomicInteger blockNumSize    = new AtomicInteger();
	    AtomicInteger blockSize       = new AtomicInteger();
	    AtomicBoolean blockSizeUnique = new AtomicBoolean();
	    if( CPMDirUtil.recognizeBlockNumFmt(
					cpmDirBytes,
					blockNumSize,
					blockSize,
					blockSizeUnique ) )
	    {
	      switch( blockNumSize.get() ) {
		case 8:
		  blockNumText = "8 Bit";
		  break;
		case 16:
		  blockNumText = "16 Bit";
		  break;
	      }
	      int bSize = blockSize.get();
	      if( bSize > 0 ) {
		if( (bSize % 1024) == 0 ) {
		  blockSizeText = String.format( "%d kByte", bSize / 1024 );
		} else {
		  blockSizeText = String.format( "%d Byte", bSize );
		}
		if( !blockSizeUnique.get() ) {
		  blockSizeText = "wahrscheinlich "
					+ blockSizeText
					+ " (nicht eindeutig erkannt)";
		}
	      }
	    }
	  }
	  EmuUtil.setText( this.fldCPMSys, cpmSysText );
	  EmuUtil.setText( this.fldBlockSize, blockSizeText );
	  EmuUtil.setText( this.fldBlockNumFmt, blockNumText );
	  this.listCPMDir.setListData( cpmDirItems );

	  // Sektortabelle
	  SortedSet<AbstractFloppyDisk.Density> trackDensities
						= new TreeSet<>();
	  SortedSet<AbstractFloppyDisk.RecordingMode> trackRecModes 
						= new TreeSet<>();
	  SortedSet<AbstractFloppyDisk.TransferRate> trackTrRates
						= new TreeSet<>();
	  buf.setLength( 0 );
	  buf.append( "<html>\n" );

	  int cyls  = disk.getCylinders();
	  int sides = disk.getSides();
	  if( (cyls < 1) || (sides < 1) ) {
	    buf.append( "Diskettenabbilddatei ist leer!" );
	  } else {
	    for( int physCyl = 0; physCyl < cyls; physCyl++ ) {
	      for( int physHead = 0; physHead < sides; physHead++ ) {
		trackDensities.add(
			disk.getTrackDensity( physCyl, physHead ) );
		trackRecModes.add(
			disk.getTrackRecordingMode( physCyl, physHead ) );
		trackTrRates.add(
			disk.getTrackTransferRate( physCyl, physHead ) );
	      }
	    }
	    if( trackDensities.size() == 1 ) {
	      AbstractFloppyDisk.Density d = trackDensities.first();
	      if( d.equals( AbstractFloppyDisk.Density.UNKNOWN )
		  || d.equals( disk.getDiskDensity() ) )
	      {
		trackDensities.clear();
	      }
	    }
	    if( trackRecModes.size() == 1 ) {
	      AbstractFloppyDisk.RecordingMode rm = trackRecModes.first();
	      if( rm.equals( AbstractFloppyDisk.RecordingMode.UNKNOWN )
		  || rm.equals( disk.getDiskRecordingMode() ) )
	      {
		trackRecModes.clear();
	      }
	    }
	    if( trackTrRates.size() == 1 ) {
	      AbstractFloppyDisk.TransferRate tr = trackTrRates.first();
	      if( tr.equals( AbstractFloppyDisk.TransferRate.UNKNOWN )
		  || tr.equals( disk.getDiskTransferRate() ) )
	      {
		trackTrRates.clear();
	      }
	    }
	    boolean hasDensities = (trackDensities.size() > 1);
	    boolean hasRecModes  = (trackRecModes.size() > 1);
	    boolean hasTrRates   = (trackTrRates.size() > 1);
	    boolean hasEtcCol    = hasDensities || hasRecModes || hasTrRates;
	    boolean hasMarked    = false;
	    boolean hasBogusIdSectors     = false;
	    boolean hasNoDataSectors      = false;
	    boolean hasDeletedDataSectors = false;
	    boolean hasErrorSectors       = false;
	    buf.append( "<table border=\"1\">\n"
			+ "<tr><th>Spur</th>" );
	    for( int side = 0; side < sides; side++ ) {
	      if( hasEtcCol ) {
		buf.append( "<th>" );
		if( hasRecModes ) {
		  buf.append( "Mode" );
		}
		if( hasTrRates ) {
		  buf.append( "Kbps" );
		}
		if( hasDensities ) {
		  if( hasRecModes || hasTrRates ) {
		    buf.append( '\u0020' );
		  }
		  buf.append( "xD" );
		}
		buf.append( "</th>" );
	      }
	      buf.append( "<th>Sektoren Seite&nbsp;" );
	      buf.append( side + 1 );
	      buf.append( " (Kopf&nbsp;" );
	      buf.append( side );
	      buf.append( ")</th>" );
	    }
	    buf.append( "</tr>\n" );
	    for( int physCyl = 0; physCyl < cyls; physCyl++ ) {
	      buf.append( "<tr><td align=\"right\">" );
	      buf.append( physCyl );
	      buf.append( "</td>" );
	      for( int physHead = 0; physHead < sides; physHead++ ) {
		if( hasEtcCol ) {
		  buf.append( "<td align=\"left\" nowrap=\"nowrap\">" );
		  appendModeTextTo(
			buf,
			disk.getTrackRecordingMode( physCyl, physHead ),
			disk.getTrackTransferRate( physCyl, physHead ),
			false,
			disk.getTrackDensity( physCyl, physHead ) );
		  buf.append( "</td>" );
		}
		buf.append( "<td align=\"left\" nowrap=\"nowrap\">" );
		int n = disk.getTrackSectorCount( physCyl, physHead );
		for( int i = 0; i < n; i++ ) {
		  SectorData sector = disk.getSectorByIndex(
							physCyl,
							physHead,
							i );
		  if( sector != null ) {
		    if( i > 0 ) {
		      buf.append( "&nbsp;" );
		    }
		    buf.append( "<a href=\"" );
		    buf.append( HTML_ACTION_KEY_PREFIX );
		    buf.append( physCyl );
		    buf.append( ':' );
		    buf.append( physHead );
		    buf.append( ':' );
		    buf.append( i );
		    buf.append( "\">[" );
		    if( sector.hasBogusID() ) {
		      buf.append( MARK_BEG );
		      buf.append( sector.getSectorNum() );
		      buf.append( MARK_BOGUS_ID );
		      buf.append( MARK_END );
		      hasBogusIdSectors = true;
		      hasMarked         = true;
		    } else {
		      buf.append( sector.getSectorNum() );
		    }
		    int c = sector.getCylinder();
		    if( c != physCyl ) {
		      buf.append( ',' );
		      buf.append( MARK_BEG );
		      buf.append( "c=" );
		      buf.append( c );
		      buf.append( MARK_END );
		      hasMarked = true;
		    }
		    int h = sector.getHead();
		    if( h != physHead ) {
		      buf.append( ',' );
		      buf.append( MARK_BEG );
		      buf.append( "h=" );
		      buf.append( h );
		      buf.append( MARK_END );
		      hasMarked = true;
		    }
		    int dataLen = sector.getDataLength();
		    if( dataLen != sectorSize ) {
		      buf.append( ',' );
		      buf.append( MARK_BEG );
		      buf.append( "n=" );
		      buf.append( sector.getSizeCode() );
		      buf.append( MARK_END );
		      hasMarked = true;
		    }
		    if( dataLen == 0 ) {
		      hasNoDataSectors = true;
		      buf.append( ',' );
		      buf.append( MARK_NO_DATA );
		    }
		    if( sector.getDataDeleted() ) {
		      hasDeletedDataSectors = true;
		      buf.append( ',' );
		      buf.append( MARK_DELETED );
		    }
		    if( sector.checkError() ) {
		      hasErrorSectors = true;
		      buf.append( ',' );
		      buf.append( MARK_ERROR );
		    }
		    buf.append( "]</a>" );
		  }
		}
		buf.append( "</td>" );
	      }
	      buf.append( "</tr>\n" );
	    }
	    buf.append( "</table>\n" );
	    if( hasMarked
			|| hasBogusIdSectors
			|| hasNoDataSectors
			|| hasDeletedDataSectors
			|| hasErrorSectors )
	    {
	      buf.append( "<br/>\n" );
	      buf.append( LangUtil.tr( "Agenda:" ) );
	      buf.append( "<br/>\n"
			+ "<table border=\"0\">\n"
			+ "<tr><td valign=\"top\">" );
	      buf.append( MARK_BEG );
	      buf.append( LangUtil.tr( "farblich hervorgehoben" ) );
	      buf.append( MARK_END );
	      buf.append( ":</td><td valign=\"top\">" );
	      buf.append( LangUtil.tr(
			"allgemeine Kennzeichnung,"
				+ " dass es an dieser Stelle"
				+ " eine Besonderheit gibt" ) );
	      buf.append( "</td></tr>\n" );
	      if( hasBogusIdSectors ) {
		buf.append( "<tr><td valign=\"top\">" );
		buf.append( MARK_BEG );
		buf.append( MARK_BOGUS_ID );
		buf.append( MARK_END );
		buf.append( ":</td><td valign=\"top\">" );
		buf.append( LangUtil.tr(
			"Sektor-ID generiert,"
				+ " da Sektorkopf nicht gelesen"
				+ " werden konnte" ) );
		buf.append( "</td></tr>\n" );
	      }
	      if( hasNoDataSectors ) {
		buf.append( "<tr><td valign=\"top\">" );
		buf.append( MARK_NO_DATA );
		buf.append( ":</td><td valign=\"top\">" );
		buf.append( LangUtil.tr( "Sektor ohne Datenbereich" ) );
		buf.append( "</td></tr>\n" );
	      }
	      if( hasDeletedDataSectors ) {
		buf.append( "<tr><td valign=\"top\">" );
		buf.append( MARK_DELETED );
		buf.append( ":</td><td valign=\"top\">" );
		buf.append( LangUtil.tr(
			"Sektor mit <em>Deleted Data Address Mark</em>" ) );
		buf.append( "</td></tr>\n" );
	      }
	      if( hasDeletedDataSectors ) {
		buf.append( "<tr><td>" );
		buf.append( MARK_ERROR );
		buf.append( ":</td><td valign=\"top\">" );
		buf.append( LangUtil.tr(
			"Sektordaten mit CRC-Fehler gelesen" ) );
		buf.append( "</td></tr>\n" );
	      }
	      buf.append( "</table>\n" );
	    }
	  }
	  buf.append( "</html>" );
	  this.fldFileContent.setContentType( "text/html" );
	  EmuUtil.setText( this.fldFileContent, buf );

	  // Sonstiges
	  StringBuilder etcBuf = new StringBuilder();
	  appendModeTextTo(
			etcBuf,
			disk.getDiskRecordingMode(),
			disk.getDiskTransferRate(),
			true,
			disk.getDiskDensity() );
	  String text = null;
	  switch( disk.getDriveType() ) {
	    case INCH_8:
	      text = "8\'\'";
	      break;
	    case INCH_5_25:
	      text = "5,25\'\'";
	      break;
	    case INCH_3_5:
	      text = "3,5\'\'";
	      break;
	  }
	  if( text != null ) {
	    if( etcBuf.length() > 0 ) {
	      text += ", ";
	    }
	    etcBuf.insert( 0, text );
	  }
	  text = null;
	  switch( disk.getStepping() ) {
	    case DOUBLE_STEP:
	      text = "Kopfpositionierung mit Doppelschritten";
	      break;
	    case EVEN_ONLY_STEP:
	      text = "Kopfpositionierung nur auf gerade Spurnummern";
	      break;
	  }
	  if( text != null ) {
	    if( etcBuf.length() > 0 ) {
	      etcBuf.append( ", " );
	    }
	    etcBuf.append( text );
	  }
	  EmuUtil.setText( this.fldFileEtc, etcBuf );

	  // Sonstiges
	  clearSectorDetails();
	  resetFind();
	  if( (cyls > 0) && (sides > 0) ) {
	    this.mnuExportTracks.setEnabled( true );
	  }
	  this.mnuFind.setEnabled( true );

	  this.exportPrefix = file.getName();
	  if( this.exportPrefix != null ) {
	    int pos = this.exportPrefix.indexOf( '.' );
	    if( pos > 0 ) {
	      this.exportPrefix = this.exportPrefix.substring( 0, pos )
							+ "_";
	    }
	  }
	  rv = true;
	}
      }
      catch( IOException ex ) {
	BaseDlg.showErrorDlg( this, ex );
      }
    }
    return rv;
  }


  private void resetFind()
  {
    this.lastFound      = false;
    this.lastBigEndian  = false;
    this.lastInputFmt   = null;
    this.lastFindText   = null;
    this.findBytes      = null;
    this.findCyl        = 0;
    this.findDataPos    = 0;
    this.findHead       = 0;
    this.findWraps      = 0;
    this.findSectorIdx  = 0;
    this.findSectorData = null;
    this.mnuFindNext.setEnabled( false );
    this.mnuFindPrev.setEnabled( false );
  }


  private void setRecentFile( File file, String category )
  {
    RecentDirsMngr.setRecentDir( file, category );
    if( this.recentFilesMngr != null ) {
      this.recentFilesMngr.setRecentFile( file );
    }
  }


  private void setSectorDataView( Component view )
  {
    JViewport vp = this.spSectorData.getViewport();
    if( vp != null ) {
      vp.setView( view );
    }
  }


  private void showFoundBytes()
  {
    this.lastFound = true;

    // Sektor anzeigen
    showSector( this.findCyl, this.findHead, this.findSectorIdx );

    /*
     * gefundene Bytes rueckwaerts selektieren,
     * damit der Cursor auf der ersten,
     * d.h. der gefundenen Position steht
     */
    final int        p1  = this.findDataPos + this.findBytes.length - 1;
    final int        p2  = this.findDataPos;
    final HexCharFld fld = this.fldSectorData;
    EventQueue.invokeLater(
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    fld.setSelection( p1, p2 );
		  }
		} );
  }


  private void showSector( int cyl, int head, int idx )
  {
    SectorData sector = this.disk.getSectorByIndex( cyl, head, idx );
    if( sector != null ) {
      EmuUtil.setText(
		this.fldSectorPos,
		String.format(
			"Spur %d, Seite %d, Index %d\n",
			cyl,
			head + 1,
			idx ) );
      EmuUtil.setText(
		this.fldSectorID,
		String.format(
			"C=%d, H=%d, R=%d, N=%d\n",
			sector.getCylinder(),
			sector.getHead(),
			sector.getSectorNum(),
			sector.getSizeCode() ) );
      String etcText = "";
      if( sector.getDataDeleted()
	  || sector.checkError()
	  || sector.hasBogusID() )
      {
	StringBuilder buf = new StringBuilder( 128 );
	if( sector.getDataDeleted() ) {
	  buf.append( LangUtil.tr( "Daten als gel\u00F6scht markiert" ) );
	}
	if( sector.checkError() ) {
	  if( buf.length() > 0 ) {
	    buf.append( ", " );
	  }
	  buf.append( LangUtil.tr( "Lesefehler" ) );
	}
	if( sector.hasBogusID() ) {
	  if( buf.length() > 0 ) {
	    buf.append( ", " );
	  }
	  buf.append( LangUtil.tr(
		"Sektor-ID generiert (Sektorkopf war nicht lesbar)" ) );
	}
	etcText = buf.toString();
      }
      EmuUtil.setText( this.fldSectorEtc, etcText );
      this.selectedSector = sector;
      setSectorDataView( this.fldSectorData );
      this.fldSectorData.refresh();
      this.btnSectorCopy.setEnabled( true );
      this.btnSectorExport.setEnabled( true );
    } else {
      clearSectorDetails();
    }
  }
}
