/*
 * (c) 2009-2026 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Dialog zur Auswahl eines Verzeichnisses
 */

package jkcemu.file;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EventObject;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import jkcemu.base.BaseDlg;
import jkcemu.base.DeviceIO;
import jkcemu.base.EmuUtil;
import jkcemu.base.GUIFactory;


public class DirSelectDlg
		extends BaseDlg
		implements TreeSelectionListener, TreeWillExpandListener
{
  private File               selectedDirFile;
  private FileNodeComparator comparator;
  private FileNode           rootNode;
  private DefaultTreeModel   treeModel;
  private JTree              tree;
  private JTextField         fldDir;
  private JButton            btnPaste;
  private JButton            btnOK;
  private JButton            btnCancel;


  public static File selectDirectory( Window owner, File preselection )
  {
    DirSelectDlg dlg = new DirSelectDlg( owner, preselection );
    dlg.setVisible( true );
    return dlg.selectedDirFile;
  }


	/* --- TreeSelectionListener --- */

  @Override
  public void valueChanged( TreeSelectionEvent e )
  {
    if( e.getSource() == this.tree ) {
      TreePath tp = this.tree.getSelectionPath();
      if( tp != null ) {
	Object o = tp.getLastPathComponent();
	if( (o != null) && (FileUtil.checkTreeNodeUsable( this.tree, o )) ) {
	  if( o instanceof FileNode ) {
	    File file = ((FileNode) o).getFile();
	    if( file != null ) {
	      this.fldDir.setText( file.getPath() );
	    }
	  }
	}
      }
    }
  }


	/* --- TreeWillExpandListener --- */

  @Override
  public void treeWillCollapse( TreeExpansionEvent e )
					throws ExpandVetoException
  {
    // leer
  }


  @Override
  public void treeWillExpand( TreeExpansionEvent e )
					throws ExpandVetoException
  {
    FileUtil.checkTreeWillExpand( e );

    TreePath treePath = e.getPath();
    if( treePath != null ) {
      setWaitCursor( true );
      Object o = treePath.getLastPathComponent();
      if( o != null ) {
	if( o instanceof FileNode ) {
	  refreshNode( (FileNode) o );
	}
      }
      setWaitCursor( false );
    }
  }


	/* --- ueberschriebene Methoden --- */

  @Override
  protected boolean doAction( EventObject e )
  {
    boolean rv = false;
    try {
      Object src = e.getSource();
      if( src != null ) {
	if( src == this.btnPaste ) {
	  rv = true;
	  this.fldDir.paste();
	} else if( (src == this.fldDir) || (src == this.btnOK) ) {
	  rv = true;
	  doApprove();
	} else if( src == this.btnCancel ) {
	  rv = true;
	  this.selectedDirFile = null;
	  doClose();
	}
      }
    }
    catch( IOException ex ) {
      showErrorDlg( this, null, ex );
    }
    return rv;
  }


  @Override
  public boolean doClose()
  {
    boolean rv = super.doClose();
    if( rv ) {
      this.tree.removeTreeSelectionListener( this );
      this.tree.removeTreeWillExpandListener( this );
      this.tree.removeKeyListener( this );
      this.fldDir.removeActionListener( this );
      this.btnPaste.removeActionListener( this );
      this.btnOK.removeActionListener( this );
      this.btnCancel.removeActionListener( this );
    }
    return rv;
  }


	/* --- Konstruktor --- */

  private DirSelectDlg( Window owner, final File preSelection )
  {
    super( owner, "Verzeichnisauswahl" );
    this.selectedDirFile = null;
    this.comparator      = FileNodeComparator.getIgnoreCaseInstance();

    DeviceIO.startFindUnreachableNetPaths(
				new Runnable()
				{
				  @Override
				  public void run()
				  {
				    updRoots();
				  }
				} );


    // Fensterinhalt
    setLayout( new GridBagLayout() );
    GridBagConstraints gbc = new GridBagConstraints(
					0, 0,
					GridBagConstraints.REMAINDER, 1,
					1.0, 1.0,
					GridBagConstraints.CENTER,
					GridBagConstraints.BOTH,
					new Insets( 5, 5, 5, 5 ),
					0, 0 );


    // Dateibaum
    DefaultTreeSelectionModel selModel = new DefaultTreeSelectionModel();
    selModel.setSelectionMode( TreeSelectionModel.SINGLE_TREE_SELECTION );

    this.rootNode  = new FileNode( null, null, true );
    this.treeModel = new DefaultTreeModel( this.rootNode );
    this.tree      = GUIFactory.createTree( this.treeModel );
    this.tree.setSelectionModel( selModel );
    this.tree.setEditable( false );
    this.tree.setPreferredSize( new Dimension( 400, 300 ) );
    this.tree.setRootVisible( false );
    this.tree.setScrollsOnExpand( true );
    this.tree.setShowsRootHandles( true );
    this.tree.setCellRenderer( new FileTreeCellRenderer() );
    add( GUIFactory.createScrollPane( this.tree ), gbc );


    // Verzeichnisfeld
    gbc.anchor        = GridBagConstraints.WEST;
    gbc.fill          = GridBagConstraints.NONE;
    gbc.weightx       = 0.0;
    gbc.weighty       = 0.0;
    gbc.insets.bottom = 0;
    gbc.gridwidth     = 1;
    gbc.gridy++;
    add(
	GUIFactory.createLabel(
		"Ausgew\u00E4hltes oder neues Verzeichnis:" ),
	gbc );

    this.fldDir       = GUIFactory.createTextField();
    gbc.fill          = GridBagConstraints.HORIZONTAL;
    gbc.weightx       = 1.0;
    gbc.insets.top    = 0;
    gbc.insets.bottom = 5;
    gbc.gridy++;
    add( this.fldDir, gbc );

    this.btnPaste = GUIFactory.createRelImageResourceButton(
					this,
					"edit/paste.png",
	                                EmuUtil.TEXT_PASTE );
    gbc.fill        = GridBagConstraints.NONE;
    gbc.weightx     = 0.0;
    gbc.insets.left = 0;
    gbc.gridx++;
    add( this.btnPaste, gbc );


    // Knoepfe
    JPanel panelBtn = GUIFactory.createPanel(
				new GridLayout( 1, 2, 5, 5 ) );
    gbc.anchor        = GridBagConstraints.CENTER;
    gbc.fill          = GridBagConstraints.NONE;
    gbc.weightx       = 0.0;
    gbc.weighty       = 0.0;
    gbc.insets.top    = 10;
    gbc.insets.bottom = 10;
    gbc.gridwidth     = GridBagConstraints.REMAINDER;
    gbc.gridx         = 0;
    gbc.gridy++;
    add( panelBtn, gbc );

    this.btnOK = GUIFactory.createButtonOK();
    panelBtn.add( this.btnOK );

    this.btnCancel = GUIFactory.createButtonCancel();
    panelBtn.add( this.btnCancel );


    // Fenstergroesse
    pack();
    setResizable( true );
    setParentCentered();
    this.tree.setPreferredSize( null );


    // Dateibaum aktualisieren
    this.rootNode.setChildrenLoaded( false );
    refreshNode( this.rootNode );
    this.treeModel.nodeStructureChanged( this.rootNode );


    // Listener
    this.tree.addTreeSelectionListener( this );
    this.tree.addTreeWillExpandListener( this );
    this.tree.addKeyListener( this );
    this.fldDir.addActionListener( this );
    this.btnPaste.addActionListener( this );
    this.btnOK.addActionListener( this );
    this.btnCancel.addActionListener( this );


    // vorausgewaehltes Verzeichnis einstellen
    if( preSelection != null ) {
      EventQueue.invokeLater(
			new Runnable()
			{
			  public void run()
			  {
			    selectPath( preSelection );
			  }
			} );
    }
  }


	/* --- private Methoden --- */

  private void doApprove() throws IOException
  {
    File dirFile = getAbsoluteDirFile();
    if( dirFile == null ) {
      throw new IOException(
		"Kein Verzeichnis angegeben bzw. ausgew\u00E4hlt" );
    }
    if( dirFile.exists() ) {
      if( !dirFile.isDirectory() ) {
	throw new IOException(
		"Das ausgew\u00E4hlte bzw. angegebene Dateisystemobjekt"
			+ " ist kein Verzeichnis." );
      }
      this.selectedDirFile = dirFile;
      doClose();
    } else {
      if( showYesNoDlg(
		this,
		"Das angegebene Verzeichnis existiert nicht.\n"
			+ "M\u00F6chten Sie es anlegen"
			+ " und ausw\u00E4hlen?" ) )
      {
	if( !dirFile.mkdir() ) {
	  throw new IOException(
		"Das angegebene Verzeichnis konnte nicht angelegt werden." );
	}
	this.selectedDirFile = dirFile;
	doClose();
      }
    }
  }


  private void expandAndSelectPath( TreePath treePath )
  {
    if( treePath != null ) {
      if( treePath.getPathCount() > 1 ) {
	this.tree.expandPath( treePath );
	this.tree.makeVisible( treePath );
	this.tree.setSelectionPath( treePath );
	this.tree.scrollPathToVisible( treePath );
      }
    }
  }


  private File getAbsoluteDirFile()
  {
    File   dirFile = null;
    String dirName = this.fldDir.getText();
    if( dirName != null ) {
      dirName = dirName.trim();
      if( !dirName.isEmpty() ) {
	dirFile = new File( dirName );
	if( dirFile != null ) {
	  if( !dirFile.isAbsolute() ) {
	    dirFile = dirFile.getAbsoluteFile();
	  }
	}
      }
    }
    this.fldDir.setText( dirFile != null ? dirFile.getPath() : "" );
    return dirFile;
  }


  private void refreshNode( FileNode node )
  {
    if( (node.isFileSystemRoot()
		|| FileUtil.checkTreeNodeUsable( this.tree, node ))
	&& !node.hasChildrenLoaded() )
    {
      node.updNode();
      node.removeAllChildren();

      boolean done    = false;
      boolean fsRoot  = false;
      File[]  entries = null;
      File    file    = node.getFile();
      if( file != null ) {
	if( FileUtil.isUsable( file ) ) {
	  entries = file.listFiles();
	}
      } else {
	entries = DeviceIO.listRoots();
	fsRoot  = true;
      }
      if( entries != null ) {
	for( File tmpFile : entries ) {
	  boolean state = fsRoot;
	  if( !state ) {
	    if( FileUtil.isUsable( tmpFile ) ) {
	      if( tmpFile.isDirectory() && !tmpFile.isHidden() ) {
		state = true;
	      }
	    }
	  }
	  if( state ) {
	    node.add( new FileNode( node, tmpFile, fsRoot ) );
	  }
	}
      }
      node.sort( this.comparator );
      node.setChildrenLoaded( true );
      this.treeModel.nodeStructureChanged( node );
    }
  }


  private void selectPath( File file )
  {
    // Pfad in Abschnitte zerlegen
    java.util.List<File> fileItems = new ArrayList<>();
    while( file != null ) {
      fileItems.add( file );
      file = file.getParentFile();
    }

    // zu selektierenden Knoten suchen
    FileNode node     = this.rootNode;
    TreePath treePath = new TreePath( node );
    int      itemIdx  = fileItems.size() - 1;
    while( (node != null) && (itemIdx >= 0) ) {
      refreshNode( node );

      // zugehoeriges Kind suchen
      FileNode childNode = node.getChildByFile( fileItems.get( itemIdx ) );
      if( childNode == null ) {
	break;
      }
      treePath = treePath.pathByAddingChild( childNode );
      node = childNode;
      --itemIdx;
    }
    final TreePath tp = treePath;
    EventQueue.invokeLater(
			new Runnable()
			{
			  public void run()
			  {
			    expandAndSelectPath( tp );
			  }
			} );
  }


  private void updRoots()
  {
    if( this.rootNode != null ) {
      int n = this.rootNode.getChildCount();
      for( int i = 0; i < n; i++ ) {
	TreeNode node = this.rootNode.getChildAt( i );
	if( node instanceof FileNode ) {
	  ((FileNode) node).updNode();
	  this.treeModel.nodeChanged( node );
	}
      }
    }
  }
}
